package com.nepalgo.auth.staff;

import com.nepalgo.auth.dto.*;
import com.nepalgo.auth.security.StaffJwtProperties;
import com.nepalgo.auth.security.StaffJwtService;
import com.nepalgo.auth.security.TotpSecretEncryptor;
import com.nepalgo.auth.security.TotpService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import com.nepalgo.auth.dto.*;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StaffAuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_MINUTES = 15;

    private final StaffUserRepository staffUserRepository;
    private final StaffRefreshTokenRepository refreshTokenRepository;
    private final AdminAuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final StaffJwtService jwtService;
    private final StaffJwtProperties jwtProperties;
    private final TotpService totpService;
    private final TotpSecretEncryptor totpSecretEncryptor;

    @Transactional (noRollbackFor = InvalidStaffCredentialsException.class)
    public StaffLoginResponse login(String email, String rawPassword) {
        // Generic failure for EVERY wrong-credential case (unknown email,
        // wrong password, locked account) — D3/section 5b: no distinct
        // error message that would let someone enumerate valid staff emails.
        StaffUser staff = staffUserRepository.findByEmail(email).orElse(null);

        if (staff == null) {
            throw new InvalidStaffCredentialsException();
        }

        if (staff.getLockedUntil() != null && staff.getLockedUntil().isAfter(Instant.now())) {
            throw new InvalidStaffCredentialsException();
        }

        if (!passwordEncoder.matches(rawPassword, staff.getPasswordHash())) {
            staff.setFailedLoginCount(staff.getFailedLoginCount() + 1);
            if (staff.getFailedLoginCount() >= MAX_FAILED_ATTEMPTS) {
                staff.setLockedUntil(Instant.now().plusSeconds(LOCKOUT_MINUTES * 60));
                logAudit(staff.getId(), "ACCOUNT_LOCKED", staff.getId(), "Too many failed login attempts");
            }
            staffUserRepository.save(staff);
            throw new InvalidStaffCredentialsException();
        }

        if (!staff.isActive()) {
            throw new InvalidStaffCredentialsException();
        }

        // Correct password: reset the failure counter regardless of what
        // happens next (MFA required or not).
        staff.setFailedLoginCount(0);
        staff.setLockedUntil(null);
        staffUserRepository.save(staff);

        if (!staff.isMfaEnabled() && staff.getRole() == StaffRole.SUPER_ADMIN) {
            // Spec 5b: TOTP is MANDATORY for SUPER_ADMIN. Enforced here on the
            // server, not by a UI nudge: no access token until enrollment is done.
            return new StaffLoginResponse(false, jwtService.generatePendingMfaToken(staff), null, true);
        }

        if (!staff.isMfaEnabled()) {
            // D4: MFA is mandatory for SUPER_ADMIN, optional (but not yet
            // set up) for OPS — either way, no 2FA enrolled yet means we
            // can't verify a second factor, so skip straight to tokens.
            // SUPER_ADMIN enforcement happens at enrollment-nudge time in
            // the controller layer, not by blocking login here.
            return issueFullLogin(staff);
        }

        String pendingToken = jwtService.generatePendingMfaToken(staff);
        return new StaffLoginResponse(true, pendingToken, null, false);
    }

    @Transactional
    public MfaEnrollStartResponse startMfaEnrollment(Long staffId) {
        StaffUser staff = getStaffOrThrow(staffId);
        // Never let a (possibly stolen) session swap the authenticator of an
        // account that already has one. Lost phone -> another SUPER_ADMIN uses
        // "reset MFA", which is audited.
        if (staff.isMfaEnabled()) {
            throw new MfaAlreadyEnabledException();
        }
        String secret = totpService.generateSecret();
        staff.setTotpSecretEncrypted(totpSecretEncryptor.encrypt(secret));
        staffUserRepository.save(staff);
        // mfaEnabled stays false until /confirm succeeds — enrollment
        // isn't real until the user proves their authenticator app works.
        return new MfaEnrollStartResponse(secret, totpService.buildQrContentUri(secret, staff.getEmail()));
    }

    @Transactional
    public StaffAuthTokenResponse confirmMfaEnrollment(Long staffId, String code) {
        StaffUser staff = getStaffOrThrow(staffId);
        if (staff.getTotpSecretEncrypted() == null) {
            throw new InvalidMfaCodeException("No enrollment in progress");
        }
        String secret = totpSecretEncryptor.decrypt(staff.getTotpSecretEncrypted());
        if (!totpService.verify(secret, code)) {
            throw new InvalidMfaCodeException("Invalid code");
        }
        staff.setMfaEnabled(true);
        staffUserRepository.save(staff);
        logAudit(staff.getId(), "MFA_ENROLLED", staff.getId(), null);
        return issueTokens(staff);
    }

    @Transactional
    public StaffAuthTokenResponse verifyMfaAndLogin(String pendingToken, String code) {
        Long staffId = jwtService.verifyPendingMfaToken(pendingToken);
        StaffUser staff = getStaffOrThrow(staffId);
        String secret = totpSecretEncryptor.decrypt(staff.getTotpSecretEncrypted());
        if (!totpService.verify(secret, code)) {
            throw new InvalidMfaCodeException("Invalid code");
        }
        return issueTokens(staff);
    }

    @Transactional
    public StaffAuthTokenResponse refresh(String rawRefreshToken) {
        String hash = jwtService.hashToken(rawRefreshToken);
        StaffRefreshToken existing = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(InvalidStaffRefreshTokenException::new);

        if (existing.getRevokedAt() != null) {
            revokeEntireFamily(existing.getTokenFamily());
            throw new InvalidStaffRefreshTokenException();
        }
        if (existing.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidStaffRefreshTokenException();
        }

        StaffUser staff = existing.getStaffUser();
        String newAccessToken = jwtService.generateAccessToken(staff);
        String newRawRefreshToken = jwtService.generateOpaqueRefreshToken();

        StaffRefreshToken newTokenEntity = new StaffRefreshToken();
        newTokenEntity.setStaffUser(staff);
        newTokenEntity.setTokenHash(jwtService.hashToken(newRawRefreshToken));
        newTokenEntity.setTokenFamily(existing.getTokenFamily());
        newTokenEntity.setIssuedAt(Instant.now());
        // Idle timeout: each rotation gets a fresh 30-minute window, per D3 —
        // this is DIFFERENT from the customer side's fixed 30-day expiry.
        // A staff session dies from inactivity, not just from elapsed time.
        newTokenEntity.setExpiresAt(Instant.now().plusSeconds(jwtProperties.getRefreshTokenIdleMinutes() * 60L));
        refreshTokenRepository.save(newTokenEntity);

        existing.setRevokedAt(Instant.now());
        existing.setReplacedById(newTokenEntity.getId());
        refreshTokenRepository.save(existing);

        return new StaffAuthTokenResponse(newAccessToken, newRawRefreshToken, staff.getRole().name());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        String hash = jwtService.hashToken(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(t -> {
            t.setRevokedAt(Instant.now());
            refreshTokenRepository.save(t);
        });
    }

    private StaffAuthTokenResponse issueTokens(StaffUser staff) {
        String accessToken = jwtService.generateAccessToken(staff);
        String rawRefreshToken = jwtService.generateOpaqueRefreshToken();

        StaffRefreshToken tokenEntity = new StaffRefreshToken();
        tokenEntity.setStaffUser(staff);
        tokenEntity.setTokenHash(jwtService.hashToken(rawRefreshToken));
        tokenEntity.setTokenFamily(UUID.randomUUID());
        tokenEntity.setIssuedAt(Instant.now());
        tokenEntity.setExpiresAt(Instant.now().plusSeconds(jwtProperties.getRefreshTokenIdleMinutes() * 60L));
        refreshTokenRepository.save(tokenEntity);

        return new StaffAuthTokenResponse(accessToken, rawRefreshToken, staff.getRole().name());
    }

    public Long pendingTokenStaffId(String pendingToken) {
        return jwtService.verifyPendingMfaToken(pendingToken);
    }

    private StaffLoginResponse issueFullLogin(StaffUser staff) {
        return new StaffLoginResponse(false, null, issueTokens(staff), false);
    }

    private void revokeEntireFamily(java.util.UUID family) {
        Instant now = Instant.now();
        refreshTokenRepository.findByTokenFamily(family).forEach(t -> {
            if (t.getRevokedAt() == null) {
                t.setRevokedAt(now);
                refreshTokenRepository.save(t);
            }
        });
    }

    private StaffUser getStaffOrThrow(Long id) {
        return staffUserRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Staff not found for valid token — should be impossible"));
    }

    private void logAudit(Long actorId, String action, Long targetId, String details) {
        AdminAuditLog log = new AdminAuditLog();
        log.setActorStaffId(actorId);
        log.setAction(action);
        log.setTargetStaffId(targetId);
        log.setDetails(details);
        auditLogRepository.save(log);
    }

    @Transactional
    public StaffSummaryResponse createStaff(Long actorId, CreateStaffRequest request) {
        if (staffUserRepository.findByEmail(request.email()).isPresent()) {
            throw new StaffAlreadyExistsException();
        }
        StaffUser staff = new StaffUser();
        staff.setEmail(request.email());
        staff.setPasswordHash(passwordEncoder.encode(request.temporaryPassword()));
        staff.setRole(StaffRole.valueOf(request.role()));
        staff.setMustChangePassword(true); // D5: forced change at first login
        staff.setMfaEnabled(false);        // enrolls on their own first login
        staffUserRepository.save(staff);

        logAudit(actorId, "STAFF_CREATED", staff.getId(), "role=" + staff.getRole());
        return toSummary(staff);
    }

    public PageResponse<StaffSummaryResponse> listStaff(int page, int size) {
        Page<StaffUser> result = staffUserRepository.findAll(PageRequest.of(page, size));
        var content = result.getContent().stream().map(this::toSummary).toList();
        return new PageResponse<>(content, page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public StaffSummaryResponse deactivateStaff(Long actorId, Long targetId) {
        StaffUser staff = getStaffOrThrow(targetId);
        staff.setActive(false);
        staffUserRepository.save(staff);

        // Deactivating someone must also kill any sessions they're currently
        // holding — otherwise a deactivated account could keep using an
        // already-issued refresh token until it naturally expires.
        refreshTokenRepository.findAll().stream()
                .filter(t -> t.getStaffUser().getId().equals(targetId) && t.getRevokedAt() == null)
                .forEach(t -> {
                    t.setRevokedAt(Instant.now());
                    refreshTokenRepository.save(t);
                });

        logAudit(actorId, "STAFF_DEACTIVATED", targetId, null);
        return toSummary(staff);
    }

    @Transactional
    public StaffSummaryResponse changeRole(Long actorId, Long targetId, String newRole) {
        StaffUser staff = getStaffOrThrow(targetId);
        StaffRole oldRole = staff.getRole();
        staff.setRole(StaffRole.valueOf(newRole));
        staffUserRepository.save(staff);

        logAudit(actorId, "STAFF_ROLE_CHANGED", targetId, oldRole + " -> " + newRole);
        return toSummary(staff);
    }

    @Transactional
    public StaffSummaryResponse resetMfa(Long actorId, Long targetId) {
        StaffUser staff = getStaffOrThrow(targetId);
        staff.setMfaEnabled(false);
        staff.setTotpSecretEncrypted(null);
        staffUserRepository.save(staff);

        logAudit(actorId, "STAFF_MFA_RESET", targetId, null);
        return toSummary(staff);
    }

    private StaffSummaryResponse toSummary(StaffUser s) {
        return new StaffSummaryResponse(s.getId(), s.getEmail(), s.getRole().name(), s.isMfaEnabled(), s.isActive(), s.isMustChangePassword());
    }
}
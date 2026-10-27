package com.nepalgo.auth.customer;

import com.nepalgo.auth.dto.AuthTokenResponse;
import com.nepalgo.auth.firebase.FirebaseTokenVerifier;
import com.nepalgo.auth.security.CustomerJwtProperties;
import com.nepalgo.auth.security.CustomerJwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.nepalgo.auth.dto.CustomerProfileResponse;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerAuthService {

    private final FirebaseTokenVerifier firebaseTokenVerifier;
    private final CustomerRepository customerRepository;
    private final CustomerRefreshTokenRepository refreshTokenRepository;
    private final NepalgoIdGenerator nepalgoIdGenerator;
    private final CustomerJwtService jwtService;
    private final CustomerJwtProperties jwtProperties;

    @Transactional
    public AuthTokenResponse exchange(String firebaseIdToken) {
        var verified = firebaseTokenVerifier.verify(firebaseIdToken);

        boolean isNewUser = customerRepository.findByFirebaseUid(verified.uid()).isEmpty();

        Customer customer = customerRepository.findByFirebaseUid(verified.uid())
                .orElseGet(() -> {
                    Customer c = new Customer();
                    c.setFirebaseUid(verified.uid());
                    c.setEmail(verified.email());
                    c.setDisplayName(verified.name());
                    c.setNepalgoId(nepalgoIdGenerator.generate());
                    return customerRepository.save(c);
                });

        String accessToken = jwtService.generateAccessToken(customer);
        String rawRefreshToken = jwtService.generateOpaqueRefreshToken();

        CustomerRefreshToken tokenEntity = new CustomerRefreshToken();
        tokenEntity.setCustomer(customer);
        tokenEntity.setTokenHash(jwtService.hashToken(rawRefreshToken));
        tokenEntity.setTokenFamily(UUID.randomUUID()); // new login = new family
        tokenEntity.setIssuedAt(Instant.now());
        tokenEntity.setExpiresAt(Instant.now().plusSeconds(jwtProperties.getRefreshTokenTtlDays() * 86400L));
        refreshTokenRepository.save(tokenEntity);

        return new AuthTokenResponse(accessToken, rawRefreshToken, formatNepalgoId(customer.getNepalgoId()), isNewUser);
    }
    @Transactional
    public AuthTokenResponse refresh(String rawRefreshToken) {
        String hash = jwtService.hashToken(rawRefreshToken);
        CustomerRefreshToken existing = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token not found"));

        if (existing.getRevokedAt() != null) {
            revokeEntireFamily(existing.getTokenFamily());
            throw new InvalidRefreshTokenException("Refresh token reuse detected — session revoked");
        }

        if (existing.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidRefreshTokenException("Refresh token expired");
        }

        Customer customer = existing.getCustomer();

        String newAccessToken = jwtService.generateAccessToken(customer);
        String newRawRefreshToken = jwtService.generateOpaqueRefreshToken();

        CustomerRefreshToken newTokenEntity = new CustomerRefreshToken();
        newTokenEntity.setCustomer(customer);
        newTokenEntity.setTokenHash(jwtService.hashToken(newRawRefreshToken));
        newTokenEntity.setTokenFamily(existing.getTokenFamily());
        newTokenEntity.setIssuedAt(Instant.now());
        newTokenEntity.setExpiresAt(Instant.now().plusSeconds(jwtProperties.getRefreshTokenTtlDays() * 86400L));
        refreshTokenRepository.save(newTokenEntity);

        existing.setRevokedAt(Instant.now());
        existing.setReplacedById(newTokenEntity.getId());
        refreshTokenRepository.save(existing);

        return new AuthTokenResponse(newAccessToken, newRawRefreshToken, formatNepalgoId(customer.getNepalgoId()), false);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        String hash = jwtService.hashToken(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            token.setRevokedAt(Instant.now());
            refreshTokenRepository.save(token);
        });
    }

    private void revokeEntireFamily(UUID family) {
        Instant now = Instant.now();
        refreshTokenRepository.findByTokenFamily(family).forEach(t -> {
            if (t.getRevokedAt() == null) {
                t.setRevokedAt(now);
                refreshTokenRepository.save(t);
            }
        });
    }
    public CustomerProfileResponse getProfile(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalStateException("Customer not found for valid JWT — should be impossible"));
        return new CustomerProfileResponse(formatNepalgoId(customer.getNepalgoId()), customer.getEmail(), customer.getDisplayName());
    }

    // Raw storage is unformatted 8 digits; display format per section 5a
    // groups them "4829 1057" for readability. This conversion happens
    // only here, at the API boundary — never in the database.
    private String formatNepalgoId(String raw) {
        return raw.substring(0, 4) + " " + raw.substring(4);
    }
}
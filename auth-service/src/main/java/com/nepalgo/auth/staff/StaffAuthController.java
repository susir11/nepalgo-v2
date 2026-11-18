package com.nepalgo.auth.staff;

import com.nepalgo.auth.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/auth")
@RequiredArgsConstructor
public class StaffAuthController {

    private final StaffAuthService staffAuthService;

    @PostMapping("/login")
    public ResponseEntity<StaffLoginResponse> login(@Valid @RequestBody StaffLoginRequest request) {
        return ResponseEntity.ok(staffAuthService.login(request.email(), request.password()));
    }

    // Enrollment accepts EITHER a full staff access token (OPS opting in) OR the
    // login pending token (SUPER_ADMIN forced to enroll before getting access).
    @PostMapping("/mfa/enroll/start")
    public ResponseEntity<MfaEnrollStartResponse> enrollStart(@AuthenticationPrincipal Jwt jwt, @RequestBody(required = false) MfaConfirmBody body) {
        return ResponseEntity.ok(staffAuthService.startMfaEnrollment(staffIdFrom(jwt, body)));
    }

    @PostMapping("/mfa/enroll/confirm")
    public ResponseEntity<StaffAuthTokenResponse> enrollConfirm(@AuthenticationPrincipal Jwt jwt, @RequestBody MfaConfirmBody body) {
        return ResponseEntity.ok(staffAuthService.confirmMfaEnrollment(staffIdFrom(jwt, body), body.code()));
    }

    private Long staffIdFrom(Jwt jwt, MfaConfirmBody body) {
        if (body != null && body.pendingToken() != null && !body.pendingToken().isBlank()) {
            return staffAuthService.pendingTokenStaffId(body.pendingToken());
        }
        if (jwt == null) {
            throw new com.nepalgo.auth.staff.InvalidPendingTokenException("Sign in first");
        }
        return Long.parseLong(jwt.getSubject());
    }

    @PostMapping("/mfa/verify")
    public ResponseEntity<StaffAuthTokenResponse> verify(@Valid @RequestBody MfaCodeRequest request) {
        return ResponseEntity.ok(staffAuthService.verifyMfaAndLogin(request.pendingToken(), request.code()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<StaffAuthTokenResponse> refresh(@Valid @RequestBody com.nepalgo.auth.dto.RefreshRequest request) {
        return ResponseEntity.ok(staffAuthService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody com.nepalgo.auth.dto.RefreshRequest request) {
        staffAuthService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    public record MfaConfirmBody(String code, String pendingToken) {}
}
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

    @PostMapping("/mfa/enroll/start")
    public ResponseEntity<MfaEnrollStartResponse> enrollStart(@AuthenticationPrincipal Jwt jwt) {
        Long staffId = Long.parseLong(jwt.getSubject());
        return ResponseEntity.ok(staffAuthService.startMfaEnrollment(staffId));
    }

    @PostMapping("/mfa/enroll/confirm")
    public ResponseEntity<StaffAuthTokenResponse> enrollConfirm(@AuthenticationPrincipal Jwt jwt, @RequestBody MfaConfirmBody body) {
        Long staffId = Long.parseLong(jwt.getSubject());
        return ResponseEntity.ok(staffAuthService.confirmMfaEnrollment(staffId, body.code()));
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

    public record MfaConfirmBody(String code) {}
}
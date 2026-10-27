package com.nepalgo.auth.staff;

import com.nepalgo.auth.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

// Every method here is already gated to SUPER_ADMIN only by
// StaffSecurityConfig's hasAuthority("SUPER_ADMIN") rule on
// /api/v1/admin/staff/** — nothing extra needed in this class itself.
@RestController
@RequestMapping("/api/v1/admin/staff")
@RequiredArgsConstructor
public class StaffManagementController {

    private final StaffAuthService staffAuthService;

    @PostMapping
    public ResponseEntity<StaffSummaryResponse> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateStaffRequest request) {
        Long actorId = Long.parseLong(jwt.getSubject());
        return ResponseEntity.ok(staffAuthService.createStaff(actorId, request));
    }

    @GetMapping
    public ResponseEntity<PageResponse<StaffSummaryResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(staffAuthService.listStaff(page, size));
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<StaffSummaryResponse> deactivate(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        Long actorId = Long.parseLong(jwt.getSubject());
        return ResponseEntity.ok(staffAuthService.deactivateStaff(actorId, id));
    }

    @PostMapping("/{id}/role")
    public ResponseEntity<StaffSummaryResponse> changeRole(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @Valid @RequestBody ChangeRoleRequest request) {
        Long actorId = Long.parseLong(jwt.getSubject());
        return ResponseEntity.ok(staffAuthService.changeRole(actorId, id, request.role()));
    }

    @PostMapping("/{id}/reset-mfa")
    public ResponseEntity<StaffSummaryResponse> resetMfa(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        Long actorId = Long.parseLong(jwt.getSubject());
        return ResponseEntity.ok(staffAuthService.resetMfa(actorId, id));
    }
}
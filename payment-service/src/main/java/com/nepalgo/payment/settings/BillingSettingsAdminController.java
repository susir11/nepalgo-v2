package com.nepalgo.payment.settings;

import com.nepalgo.payment.PaymentDtos;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/billing-settings")
@RequiredArgsConstructor
public class BillingSettingsAdminController {

    private final BillingSettingsService service;

    @GetMapping
    public ResponseEntity<PaymentDtos.BillingSettingsResponse> get() {
        return ResponseEntity.ok(PaymentDtos.BillingSettingsResponse.from(service.get()));
    }

    @PutMapping
    public ResponseEntity<PaymentDtos.BillingSettingsResponse> update(@AuthenticationPrincipal Jwt jwt,
                                                                      @Valid @RequestBody PaymentDtos.UpdateBillingSettingsRequest request) {
        Long adminId = Long.parseLong(jwt.getSubject());
        return ResponseEntity.ok(PaymentDtos.BillingSettingsResponse.from(service.update(adminId, request)));
    }
}
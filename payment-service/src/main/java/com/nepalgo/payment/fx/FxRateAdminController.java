package com.nepalgo.payment.fx;

import com.nepalgo.payment.PaymentDtos;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/fx-rates")
@RequiredArgsConstructor
public class FxRateAdminController {

    private final FxRateService fxRateService;

    @PostMapping
    public ResponseEntity<PaymentDtos.FxRateResponse> propose(@AuthenticationPrincipal Jwt jwt,
                                                              @Valid @RequestBody PaymentDtos.ProposeFxRateRequest request) {
        Long adminId = Long.parseLong(jwt.getSubject());
        return ResponseEntity.ok(PaymentDtos.FxRateResponse.from(
                fxRateService.propose(adminId, request.currencyPair(), request.rate())));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<PaymentDtos.FxRateResponse> approve(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        Long adminId = Long.parseLong(jwt.getSubject());
        return ResponseEntity.ok(PaymentDtos.FxRateResponse.from(fxRateService.approve(adminId, id)));
    }

    // pair is a query param because "USD/NPR" contains a slash.
    @GetMapping("/current")
    public ResponseEntity<PaymentDtos.FxRateResponse> current(@RequestParam String pair) {
        return ResponseEntity.ok(PaymentDtos.FxRateResponse.from(fxRateService.current(pair)));
    }

    @GetMapping
    public ResponseEntity<List<PaymentDtos.FxRateResponse>> list(@RequestParam(required = false) String pair) {
        return ResponseEntity.ok(fxRateService.list(pair).stream().map(PaymentDtos.FxRateResponse::from).toList());
    }
}
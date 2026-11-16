package com.nepalgo.payment.credit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class CreditControllers {

    private CreditControllers() {}

    public record ApplyCreditsRequest(@NotNull Long bookingId, @NotEmpty List<@NotBlank String> codes) {}
    public record IssueCreditRequest(@NotNull Long customerId, @NotNull @DecimalMin("0.01") BigDecimal amountUsd,
                                     LocalDate expiresOn, String note) {}
    public record EditCreditRequest(@DecimalMin("0.01") BigDecimal amountUsd, LocalDate expiresOn) {}
    public record ReasonRequest(@NotBlank String reason) {}
    public record CompleteRefundRequest(@NotBlank String payoutReference) {}
    public record CancellationRequest(@NotNull Long bookingId, @NotBlank String tier, @NotNull BigDecimal refundPercent,
                                      boolean forceMajeure) {}
    public record ReleaseRequest(@NotNull Long bookingId) {}

    private static Long me(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }

    // ---------------- customer: wallet + apply credits to a trip request
    @RestController
    @RequestMapping("/api/store-credits")
    @RequiredArgsConstructor
    public static class Customer {
        private final StoreCreditService service;

        @GetMapping
        public ResponseEntity<List<StoreCreditService.WalletCard>> wallet(@AuthenticationPrincipal Jwt jwt) {
            return ResponseEntity.ok(service.wallet(me(jwt)));
        }

        @PostMapping("/apply")
        public ResponseEntity<List<StoreCreditService.WalletCard>> apply(@AuthenticationPrincipal Jwt jwt,
                                                                         @Valid @RequestBody ApplyCreditsRequest req) {
            return ResponseEntity.ok(service.applyToBooking(me(jwt), req.bookingId(), req.codes()));
        }
    }

    // ---------------- admin: ledger / detail / issue / edit / void (one screen, two views)
    @RestController
    @RequestMapping("/api/admin/store-credits")
    @RequiredArgsConstructor
    public static class AdminCredits {
        private final StoreCreditService service;

        @GetMapping
        public ResponseEntity<StoreCreditService.LedgerPage> ledger(@RequestParam(required = false) Long customerId,
                                                                    @RequestParam(required = false) String status,
                                                                    @RequestParam(defaultValue = "0") int page,
                                                                    @RequestParam(defaultValue = "20") int size) {
            return ResponseEntity.ok(service.ledger(customerId, status, page, size));
        }

        @GetMapping("/{id}")
        public ResponseEntity<StoreCreditService.CreditDetail> detail(@PathVariable Long id) {
            return ResponseEntity.ok(service.detail(id));
        }

        @PostMapping
        public ResponseEntity<StoreCreditService.CreditView> issue(@AuthenticationPrincipal Jwt jwt,
                                                                   @Valid @RequestBody IssueCreditRequest req) {
            return ResponseEntity.ok(service.issueManual(me(jwt), req.customerId(), req.amountUsd(), req.expiresOn(), req.note()));
        }

        @PutMapping("/{id}")
        public ResponseEntity<StoreCreditService.CreditView> edit(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                                                  @Valid @RequestBody EditCreditRequest req) {
            return ResponseEntity.ok(service.edit(me(jwt), id, req.amountUsd(), req.expiresOn()));
        }

        @PostMapping("/{id}/void")
        public ResponseEntity<StoreCreditService.CreditView> voidCredit(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                                                        @Valid @RequestBody ReasonRequest req) {
            return ResponseEntity.ok(service.voidCredit(me(jwt), id, req.reason()));
        }
    }

    // ---------------- admin: accept / reject a customer's applied credit while pricing the bill
    @RestController
    @RequestMapping("/api/admin/bills/{billId}/credits")
    @RequiredArgsConstructor
    public static class AdminBillCredits {
        private final StoreCreditService service;

        @PostMapping("/{creditId}/accept")
        public ResponseEntity<Map<String, String>> accept(@AuthenticationPrincipal Jwt jwt, @PathVariable Long billId,
                                                          @PathVariable Long creditId) {
            service.accept(me(jwt), billId, creditId);
            return ResponseEntity.ok(Map.of("result", "ACCEPTED"));
        }

        @PostMapping("/{creditId}/reject")
        public ResponseEntity<Map<String, String>> reject(@AuthenticationPrincipal Jwt jwt, @PathVariable Long billId,
                                                          @PathVariable Long creditId, @Valid @RequestBody ReasonRequest req) {
            service.reject(me(jwt), billId, creditId, req.reason());
            return ResponseEntity.ok(Map.of("result", "REJECTED"));
        }
    }

    // ---------------- admin: refund-request queue
    @RestController
    @RequestMapping("/api/admin/refund-requests")
    @RequiredArgsConstructor
    public static class AdminRefunds {
        private final RefundService service;

        @GetMapping
        public ResponseEntity<List<RefundService.RefundView>> queue(@RequestParam(required = false) String status) {
            return ResponseEntity.ok(service.queue(status));
        }

        @PostMapping("/{id}/complete")
        public ResponseEntity<RefundService.RefundView> complete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                                                 @Valid @RequestBody CompleteRefundRequest req) {
            return ResponseEntity.ok(service.complete(me(jwt), id, req.payoutReference()));
        }
    }

    // ---------------- internal: booking-service drives cancellation + credit release
    @RestController
    @RequestMapping("/internal")
    @RequiredArgsConstructor
    public static class Internal {
        private final RefundService refundService;
        private final StoreCreditService creditService;

        @PostMapping("/cancellations")
        public ResponseEntity<RefundService.CancellationOutcome> cancel(@Valid @RequestBody CancellationRequest req) {
            return ResponseEntity.ok(refundService.cancel(req.bookingId(), req.tier(), req.refundPercent(), req.forceMajeure()));
        }

        @PostMapping("/store-credits/release")
        public ResponseEntity<Map<String, Integer>> release(@Valid @RequestBody ReleaseRequest req) {
            return ResponseEntity.ok(Map.of("released", creditService.releaseForBooking(req.bookingId())));
        }
    }
}

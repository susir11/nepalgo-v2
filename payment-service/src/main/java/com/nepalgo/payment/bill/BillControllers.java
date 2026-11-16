package com.nepalgo.payment.bill;

import com.nepalgo.payment.PaymentDtos;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

// The three doors into billing, each behind its own security chain:
//   /api/admin/bills/**   staff JWT (OPS + SUPER_ADMIN): build, edit, view, timeline
//   /api/bills/**         customer JWT: view own issued bill, USD only
//   /internal/bills/**    X-Internal-Api-Key: booking-service drives issue/lock
public final class BillControllers {

    private BillControllers() {}

    @RestController
    @RequestMapping("/api/admin/bills")
    @RequiredArgsConstructor
    public static class Admin {
        private final BillService billService;

        @PostMapping
        public ResponseEntity<PaymentDtos.AdminBillResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                                    @Valid @RequestBody PaymentDtos.CreateBillRequest req) {
            Bill bill = billService.create(adminId(jwt), req);
            return ResponseEntity.ok(billService.adminView(bill.getId()));
        }

        @GetMapping("/{id}")
        public ResponseEntity<PaymentDtos.AdminBillResponse> get(@PathVariable Long id) {
            return ResponseEntity.ok(billService.adminView(id));
        }

        // Current bill of a booking (what the booking detail screen shows).
        @GetMapping
        public ResponseEntity<PaymentDtos.AdminBillResponse> byBooking(@RequestParam Long bookingId) {
            return ResponseEntity.ok(billService.adminViewForBooking(bookingId));
        }

        // THE edit button. Response may be a NEW bill (correction) — check "correctsBillId".
        @PutMapping("/{id}")
        public ResponseEntity<PaymentDtos.AdminBillResponse> edit(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                                                  @Valid @RequestBody PaymentDtos.UpdateBillRequest req) {
            Bill bill = billService.edit(adminId(jwt), id, req);
            return ResponseEntity.ok(billService.adminView(bill.getId()));
        }

        @GetMapping("/{id}/pdf")
        public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
            return pdfResponse(billService.adminPdf(id));
        }

        // e.g. /api/admin/bills/export?from=2026-07-17&to=2027-07-16
        @GetMapping("/export")
        public ResponseEntity<byte[]> export(@RequestParam java.time.LocalDate from, @RequestParam java.time.LocalDate to) {
            byte[] body = billService.exportCsv(from, to).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename("invoices_" + from + "_" + to + ".csv").build().toString())
                    .body(body);
        }

        @GetMapping("/timeline")
        public ResponseEntity<List<PaymentDtos.BillTimelineEvent>> timeline(@RequestParam Long bookingId) {
            return ResponseEntity.ok(billService.timeline(bookingId));
        }

        private static Long adminId(Jwt jwt) {
            return Long.parseLong(jwt.getSubject());
        }
    }

    @RestController
    @RequestMapping("/api/bills")
    @RequiredArgsConstructor
    public static class Customer {
        private final BillService billService;

        @GetMapping("/booking/{bookingId}")
        public ResponseEntity<PaymentDtos.CustomerBillResponse> mine(@AuthenticationPrincipal Jwt jwt,
                                                                     @PathVariable Long bookingId) {
            return ResponseEntity.ok(billService.customerView(Long.parseLong(jwt.getSubject()), bookingId));
        }

        @GetMapping("/booking/{bookingId}/pdf")
        public ResponseEntity<byte[]> minePdf(@AuthenticationPrincipal Jwt jwt, @PathVariable Long bookingId) {
            return pdfResponse(billService.customerPdf(Long.parseLong(jwt.getSubject()), bookingId));
        }
    }

    // "attachment" => browsers/apps download it with the invoice number as file name.
    static ResponseEntity<byte[]> pdfResponse(BillService.Pdf pdf) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(pdf.filename()).build().toString())
                .body(pdf.bytes());
    }

    // ---- internal: called by booking-service only ----

    public record IssueRequest(@NotNull Long bookingId, @NotNull Long adminId) {}
    public record LockRequest(@NotNull Long bookingId, @NotNull Long adminId, String buyerName) {}
    public record InternalBillStatus(Long billId, Long bookingId, String invoiceNumber, String status, boolean issued,
                                     BigDecimal payableUsd) {}

    @RestController
    @RequestMapping("/internal/bills")
    @RequiredArgsConstructor
    public static class Internal {
        private final BillService billService;
        private final com.nepalgo.payment.receipt.ReceiptService receiptService;
        private final com.nepalgo.payment.credit.StoreCreditService storeCreditService;

        // The service returns a ready-made DTO built INSIDE its transaction.
        // (Building it here, after the transaction closed, touched the lazy
        // lineItems collection -> LazyInitializationException -> a 500 AFTER
        // the lock had already committed. Found by the e2e test.)
        @PostMapping("/issue")
        public ResponseEntity<InternalBillStatus> issue(@Valid @RequestBody IssueRequest req) {
            return ResponseEntity.ok(billService.issueForBooking(req.adminId(), req.bookingId()));
        }

        @PostMapping("/lock")
        public ResponseEntity<InternalBillStatus> lock(@Valid @RequestBody LockRequest req) {
            // 1. double-claim guard (spec 8 Step 4) -> 409 DUPLICATE_RECEIPT blocks verification
            receiptService.assertNoDoubleClaim(req.bookingId());
            // 2. lock the bill (idempotent), 3. mark the verified receipt
            InternalBillStatus status = billService.lockForBooking(req.adminId(), req.bookingId(), req.buyerName());
            receiptService.markLatestAccepted(req.bookingId());
            // 4. paid => accepted travel credits are now actually consumed (spec 6b)
            storeCreditService.redeemForBooking(req.bookingId());
            return ResponseEntity.ok(status);
        }

        @GetMapping("/status")
        public ResponseEntity<InternalBillStatus> get(@RequestParam Long bookingId) {
            return ResponseEntity.ok(billService.statusForBooking(bookingId));
        }
    }
}

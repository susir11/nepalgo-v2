package com.nepalgo.payment.receipt;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public final class ReceiptControllers {

    private ReceiptControllers() {}

    // booking-service forwards the customer's upload here (after its own checks).
    @RestController
    @RequestMapping("/internal/receipts")
    @RequiredArgsConstructor
    public static class Internal {
        private final ReceiptService receiptService;

        @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public ResponseEntity<ReceiptService.Stored> store(@RequestParam Long bookingId,
                                                           @RequestParam Long customerId,
                                                           @RequestParam String receiptCode,
                                                           @RequestPart("file") MultipartFile file) {
            return ResponseEntity.ok(receiptService.store(bookingId, customerId, receiptCode, file));
        }
    }

    @RestController
    @RequestMapping("/api/admin/receipts")
    @RequiredArgsConstructor
    public static class Admin {
        private final ReceiptService receiptService;

        @GetMapping
        public ResponseEntity<List<ReceiptService.ReceiptView>> forBooking(@RequestParam Long bookingId) {
            return ResponseEntity.ok(receiptService.forAdmin(bookingId));
        }
    }
}

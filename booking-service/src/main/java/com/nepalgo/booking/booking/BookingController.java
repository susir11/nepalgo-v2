package com.nepalgo.booking.booking;

import com.nepalgo.booking.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import com.nepalgo.booking.dto.BookingPageResponse;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    public ResponseEntity<BookingResponse> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateBookingRequest request) {
        Long customerId = Long.parseLong(jwt.getSubject());
        return ResponseEntity.ok(bookingService.create(customerId, request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookingResponse> getById(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        Long customerId = Long.parseLong(jwt.getSubject());
        return ResponseEntity.ok(bookingService.getById(id, customerId));
    }

    @PostMapping("/{id}/consent")
    public ResponseEntity<BookingResponse> confirmConsent(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @RequestBody ConfirmConsentRequest request) {
        Long customerId = Long.parseLong(jwt.getSubject());
        return ResponseEntity.ok(bookingService.confirmConsent(customerId, id));
    }

    @PostMapping("/{id}/payment-receipt")
    public ResponseEntity<BookingResponse> submitPaymentReceipt(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @Valid @RequestBody SubmitPaymentReceiptRequest request) {
        Long customerId = Long.parseLong(jwt.getSubject());
        return ResponseEntity.ok(bookingService.submitPaymentReceipt(customerId, id, request));
    }

    @PostMapping("/{id}/withdraw")
    public ResponseEntity<BookingResponse> withdraw(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        Long customerId = Long.parseLong(jwt.getSubject());
        return ResponseEntity.ok(bookingService.withdraw(customerId, id));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<com.nepalgo.booking.dto.CancelBookingResponse> cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        Long customerId = Long.parseLong(jwt.getSubject());
        return ResponseEntity.ok(bookingService.cancel(customerId, id));
    }

    @PostMapping("/{id}/identity")
    public ResponseEntity<BookingResponse> submitIdentity(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @Valid @RequestBody IdentityRequest request) {
        Long customerId = Long.parseLong(jwt.getSubject());
        return ResponseEntity.ok(bookingService.submitIdentity(customerId, id, request));
    }

    @GetMapping
    public ResponseEntity<BookingPageResponse> myTrips(@AuthenticationPrincipal Jwt jwt,
                                                       @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        Long customerId = Long.parseLong(jwt.getSubject());
        return ResponseEntity.ok(bookingService.listForCustomer(customerId, page, size));
    }
}
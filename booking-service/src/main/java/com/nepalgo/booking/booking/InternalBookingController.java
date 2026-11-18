package com.nepalgo.booking.booking;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

// Service-to-service read of a booking (payment-service needs owner + status
// before it will price a bill). Protected by X-Internal-Api-Key, never exposed
// through the gateway. Deliberately excludes the passport number: the caller
// doesn't need it, so it never leaves this service (data minimisation).
@RestController
@RequestMapping("/internal/bookings")
@RequiredArgsConstructor
public class InternalBookingController {

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;

    public record BookingSummary(Long id, Long customerId, String status, String origin, String voucherCode,
                                 String destinationName, LocalDate arrivalDate, int durationDays, int partySize,
                                 boolean guideRequested, boolean porterRequested, String specialRequestText,
                                 String leadBookerLegalName, String leadBookerEmail) {}

    @GetMapping("/{id}")
    public ResponseEntity<BookingSummary> get(@PathVariable Long id) {
        Booking b = bookingRepository.findById(id).orElseThrow(() -> new BookingNotFoundException(id));
        return ResponseEntity.ok(new BookingSummary(b.getId(), b.getCustomerId(), b.getStatus().name(),
                b.getOrigin().name(), b.getVoucherCode(), b.getDestinationNameSnapshot(), b.getArrivalDate(),
                b.getDurationDays(), b.getPartySize(), b.isGuideRequested(), b.isPorterRequested(),
                b.getSpecialRequestText(), b.getLeadBookerLegalName(), b.getLeadBookerEmail()));
    }

    // Account deletion: see BookingService.eraseCustomer. 409 while a payment is being verified.
    @PostMapping("/customers/{customerId}/erase")
    public ResponseEntity<BookingService.ErasureResult> erase(@PathVariable Long customerId) {
        return ResponseEntity.ok(bookingService.eraseCustomer(customerId));
    }
}

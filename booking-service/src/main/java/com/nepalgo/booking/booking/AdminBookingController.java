package com.nepalgo.booking.booking;

import com.nepalgo.booking.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import com.nepalgo.booking.dto.ForceMajeureCancelRequest;

@RestController
@RequestMapping("/api/admin/bookings")
@RequiredArgsConstructor
public class AdminBookingController {

    private final BookingService bookingService;

    @GetMapping
    public ResponseEntity<AdminBookingPageResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long destinationId,
            @RequestParam(required = false) String origin,
            @RequestParam(required = false) java.time.LocalDate arrivalFrom,
            @RequestParam(required = false) java.time.LocalDate arrivalTo,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "urgency") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(bookingService.adminList(new BookingService.AdminSearch(status, customerId,
                destinationId, origin, arrivalFrom, arrivalTo, q, sort), page, size));
    }

    @GetMapping("/stats")
    public ResponseEntity<java.util.Map<String, Long>> stats() {
        return ResponseEntity.ok(bookingService.stats());
    }

    @GetMapping("/{id}/timeline")
    public ResponseEntity<java.util.List<BookingService.TimelineEntry>> timeline(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.timeline(id));
    }

    public record InternalNotesRequest(@jakarta.validation.constraints.Size(max = 5000) String internalNotes) {}

    @PutMapping("/{id}/internal-notes")
    public ResponseEntity<AdminBookingDetailResponse> notes(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                                            @Valid @RequestBody InternalNotesRequest req) {
        return ResponseEntity.ok(bookingService.updateInternalNotes(Long.parseLong(jwt.getSubject()), id, req.internalNotes()));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<AdminBookingSummaryResponse> approve(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        Long adminId = Long.parseLong(jwt.getSubject());
        return ResponseEntity.ok(bookingService.approve(adminId, id));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<AdminBookingSummaryResponse> reject(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @Valid @RequestBody RejectBookingRequest request) {
        Long adminId = Long.parseLong(jwt.getSubject());
        return ResponseEntity.ok(bookingService.reject(adminId, id, request.rejectionReason()));
    }

    // Manual (ops-created) bookings: issue the priced bill so the customer can pay.
    @PostMapping("/{id}/issue-bill")
    public ResponseEntity<AdminBookingSummaryResponse> issueBill(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        Long adminId = Long.parseLong(jwt.getSubject());
        return ResponseEntity.ok(bookingService.issueBillForManualBooking(adminId, id));
    }

    @PostMapping("/manual")
    public ResponseEntity<AdminBookingSummaryResponse> createManual(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateManualBookingRequest request) {
        Long adminId = Long.parseLong(jwt.getSubject());
        return ResponseEntity.ok(bookingService.createManual(adminId, request));
    }

    @PostMapping("/{id}/verify-payment")
    public ResponseEntity<AdminBookingSummaryResponse> verifyPayment(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        Long adminId = Long.parseLong(jwt.getSubject());
        return ResponseEntity.ok(bookingService.verifyPayment(adminId, id));
    }

    @PostMapping("/{id}/reject-payment")
    public ResponseEntity<AdminBookingSummaryResponse> rejectPayment(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @Valid @RequestBody RejectBookingRequest request) {
        Long adminId = Long.parseLong(jwt.getSubject());
        return ResponseEntity.ok(bookingService.rejectPayment(adminId, id, request.rejectionReason()));
    }


    @PostMapping("/{id}/force-majeure-cancel")
    public ResponseEntity<com.nepalgo.booking.dto.CancelBookingResponse> forceMajeureCancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @Valid @RequestBody ForceMajeureCancelRequest request) {
        Long adminId = Long.parseLong(jwt.getSubject());
        return ResponseEntity.ok(bookingService.forceMajeureCancel(adminId, id, request.reason()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminBookingDetailResponse> detail(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.adminDetail(id));
    }
}
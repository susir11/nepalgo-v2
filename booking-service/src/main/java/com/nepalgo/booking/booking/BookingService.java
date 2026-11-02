package com.nepalgo.booking.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nepalgo.booking.client.CatalogClient;
import com.nepalgo.booking.dto.BookingResponse;
import com.nepalgo.booking.dto.CreateBookingRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.nepalgo.booking.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.nepalgo.booking.booking.CancellationTierCalculator.TierResult;

import org.springframework.beans.factory.annotation.Value;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingAuditLogRepository auditLogRepository;
    private final CatalogClient catalogClient;
    private final VoucherCodeGenerator voucherCodeGenerator;
    private final ObjectMapper objectMapper;
    private final CancellationTierCalculator cancellationTierCalculator;

    @Transactional
    public BookingResponse create(Long customerId, CreateBookingRequest request) {
        var destination = catalogClient.getDestination(request.destinationId());
        if (destination.defaultItinerary() == null) {
            throw new IllegalStateException("Destination has no DEFAULT itinerary authored yet");
        }

        int durationDays = destination.defaultItinerary().days().size();
        LocalDate endDate = request.arrivalDate().plusDays(durationDays);

        // D8.8: reject if any OTHER active booking overlaps this
        // destination + date range.
        List<Booking> overlaps = bookingRepository.findOverlappingHolds(
                request.destinationId(), request.arrivalDate(), endDate);
        if (!overlaps.isEmpty()) {
            throw new DestinationUnavailableException(
                    "This destination is already held for an overlapping date range");
        }

        Booking booking = new Booking();
        booking.setVoucherCode(voucherCodeGenerator.generate());
        booking.setStatus(BookingStatus.PENDING_APPROVAL);
        booking.setOrigin(BookingOrigin.self_served);
        booking.setCustomerId(customerId);
        booking.setDestinationId(request.destinationId());
        booking.setDestinationNameSnapshot(destination.name());
        booking.setItineraryVersionId(request.itineraryVersionId());

        try {
            booking.setItinerarySnapshot(objectMapper.writeValueAsString(destination.defaultItinerary()));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize itinerary snapshot", e);
        }

        booking.setPartySize(request.partySize());
        booking.setArrivalDate(request.arrivalDate());
        booking.setDurationDays(durationDays);
        booking.setGuideRequested(request.guideRequested());
        booking.setPorterRequested(request.porterRequested());
        booking.setSpecialRequestText(request.specialRequestText());

        bookingRepository.save(booking);
        logAudit(null, "BOOKING_CREATED", booking.getId(), customerId, null);

        return toResponse(booking);
    }

    public BookingResponse getById(Long bookingId, Long customerId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        if (!booking.getCustomerId().equals(customerId)) {
            // A customer can never view another customer's booking, even
            // by guessing a valid id — same boundary as "every service
            // re-validates independently," applied to data ownership here.
            throw new BookingNotFoundException(bookingId);
        }
        return toResponse(booking);
    }

    public BookingPageResponse listForCustomer(Long customerId, int page, int size) {
        Page<Booking> result = bookingRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, PageRequest.of(page, size));
        var content = result.getContent().stream().map(this::toResponse).toList();
        return new BookingPageResponse(content, page, size, result.getTotalElements(), result.getTotalPages());
    }

    public AdminBookingDetailResponse adminDetail(Long bookingId) {
        Booking b = getBookingOrThrow(bookingId);
        return new AdminBookingDetailResponse(b.getId(), b.getVoucherCode(), b.getStatus().name(), b.getOrigin().name(),
                b.getCustomerId(), b.getDestinationId(), b.getDestinationNameSnapshot(), b.getItinerarySnapshot(),
                b.getPartySize(), b.getArrivalDate(), b.getDurationDays(), b.isGuideRequested(), b.isPorterRequested(),
                b.getSpecialRequestText(), b.getLeadBookerLegalName(), b.getLeadBookerPassportNumber(), b.getLeadBookerEmail(),
                b.isConsentConfirmed(), b.getPaymentReceiptImageUrl(), b.getPaymentReceiptCode(), b.getRejectionReason(),
                b.getInternalNotes(), b.getCancellationTier(), b.getCancellationRefundPercent(), b.getCreatedAt());
    }

    private void logAudit(Long adminUserId, String actionType, Long bookingId, Long customerId, String details) {
        BookingAuditLog log = new BookingAuditLog();
        log.setAdminUserId(adminUserId);
        log.setActionType(actionType);
        log.setBookingId(bookingId);
        log.setCustomerId(customerId);

        // details column is jsonb, so a bare string like "60_PLUS_DAYS" or a
        // free-text rejection reason is invalid JSON and Postgres rejects the
        // insert (which then rolls back the whole business transaction).
        // Wrapping every non-null detail as {"detail": "..."} makes all call
        // sites safe at once, and ObjectMapper handles quote/escape characters
        // inside user-typed reasons correctly.
        if (details != null) {
            try {
                log.setDetails(objectMapper.writeValueAsString(java.util.Map.of("detail", details)));
            } catch (Exception e) {
                throw new IllegalStateException("Failed to serialize audit details", e);
            }
        }

        auditLogRepository.save(log);
    }

    private BookingResponse toResponse(Booking b) {
        return new BookingResponse(b.getId(), b.getVoucherCode(), b.getStatus().name(),
                b.getDestinationNameSnapshot(), b.getPartySize(), b.getArrivalDate(), b.getDurationDays(),
                b.isGuideRequested(), b.isPorterRequested(), b.getSpecialRequestText(), b.getCreatedAt());
    }

    @Transactional
    public AdminBookingSummaryResponse approve(Long adminId, Long bookingId) {
        Booking booking = getBookingOrThrow(bookingId);
        requireStatus(booking, BookingStatus.PENDING_APPROVAL);
        booking.setStatus(BookingStatus.AWAITING_PAYMENT);
        booking.setAwaitingPaymentSince(Instant.now());
        bookingRepository.save(booking);
        logAudit(adminId, "BOOKING_APPROVED", bookingId, booking.getCustomerId(), null);
        return toAdminSummary(booking);
    }

    @Transactional
    public AdminBookingSummaryResponse reject(Long adminId, Long bookingId, String reason) {
        Booking booking = getBookingOrThrow(bookingId);
        requireStatus(booking, BookingStatus.PENDING_APPROVAL);
        booking.setStatus(BookingStatus.REJECTED);
        booking.setRejectionReason(reason);
        bookingRepository.save(booking);
        logAudit(adminId, "BOOKING_REJECTED", bookingId, booking.getCustomerId(), reason);
        return toAdminSummary(booking);
    }

    @Transactional
    public AdminBookingSummaryResponse createManual(Long adminId, CreateManualBookingRequest request) {
        var destination = catalogClient.getDestination(request.destinationId());
        if (destination.defaultItinerary() == null) {
            throw new IllegalStateException("Destination has no DEFAULT itinerary authored yet");
        }

        int durationDays = destination.defaultItinerary().days().size();
        LocalDate endDate = request.arrivalDate().plusDays(durationDays);

        List<Booking> overlaps = bookingRepository.findOverlappingHolds(
                request.destinationId(), request.arrivalDate(), endDate);
        if (!overlaps.isEmpty()) {
            throw new DestinationUnavailableException("This destination is already held for an overlapping date range");
        }

        Booking booking = new Booking();
        booking.setVoucherCode(voucherCodeGenerator.generate());
        booking.setStatus(BookingStatus.AWAITING_PAYMENT); // skips approval — admin-initiated
        booking.setAwaitingPaymentSince(Instant.now());
        booking.setOrigin(BookingOrigin.ops_created);
        booking.setCustomerId(request.customerId());
        booking.setDestinationId(request.destinationId());
        booking.setDestinationNameSnapshot(destination.name());
        booking.setItineraryVersionId(request.itineraryVersionId());

        try {
            booking.setItinerarySnapshot(objectMapper.writeValueAsString(destination.defaultItinerary()));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize itinerary snapshot", e);
        }

        booking.setPartySize(request.partySize());
        booking.setArrivalDate(request.arrivalDate());
        booking.setDurationDays(durationDays);
        booking.setGuideRequested(request.guideRequested());
        booking.setPorterRequested(request.porterRequested());
        booking.setSpecialRequestText(request.specialRequestText());
        booking.setInternalNotes(request.internalNotes());

        bookingRepository.save(booking);
        logAudit(adminId, "BOOKING_CREATED_MANUALLY", booking.getId(), request.customerId(), null);

        return toAdminSummary(booking);
    }

    public AdminBookingPageResponse adminList(String status, Long customerId, int page, int size) {
        Page<Booking> result = bookingRepository.adminSearch(status, customerId, PageRequest.of(page, size));
        var content = result.getContent().stream().map(this::toAdminSummary).toList();
        return new AdminBookingPageResponse(content, page, size, result.getTotalElements(), result.getTotalPages());
    }

    private void requireStatus(Booking booking, BookingStatus expected) {
        if (booking.getStatus() != expected) {
            throw new InvalidBookingStateException(
                    "Booking is in status " + booking.getStatus() + ", expected " + expected);
        }
    }

    private Booking getBookingOrThrow(Long id) {
        return bookingRepository.findById(id).orElseThrow(() -> new BookingNotFoundException(id));
    }

    private AdminBookingSummaryResponse toAdminSummary(Booking b) {
        return new AdminBookingSummaryResponse(b.getId(), b.getVoucherCode(), b.getStatus().name(),
                b.getCustomerId(), b.getDestinationNameSnapshot(), b.getArrivalDate(), b.getOrigin().name());
    }

    @Transactional
    public BookingResponse confirmConsent(Long customerId, Long bookingId) {
        Booking booking = getBookingOrThrow(bookingId);
        requireOwnership(booking, customerId);
        requireStatus(booking, BookingStatus.AWAITING_PAYMENT);
        // D-placeholder: a real ConsentRecord/TermsVersion check arrives in
        // Phase 5 — for now this just flips a boolean on the booking itself.
        booking.setConsentConfirmed(true);
        bookingRepository.save(booking);
        return toResponse(booking);
    }

    @Transactional
    public BookingResponse submitPaymentReceipt(Long customerId, Long bookingId, SubmitPaymentReceiptRequest request) {
        Booking booking = getBookingOrThrow(bookingId);
        requireOwnership(booking, customerId);
        requireStatus(booking, BookingStatus.AWAITING_PAYMENT);

        if (!booking.isConsentConfirmed()) {
            // Mirrors the spec's real intent (Step 3 must check a confirmed
            // consent record before accepting payment) even though the
            // underlying consent mechanism itself is still a placeholder.
            throw new InvalidBookingStateException("Consent must be confirmed before submitting payment");
        }

        booking.setPaymentReceiptImageUrl(request.paymentReceiptImageUrl());
        booking.setPaymentReceiptCode(request.paymentReceiptCode());
        booking.setStatus(BookingStatus.VERIFYING_PAYMENT);
        bookingRepository.save(booking);
        logAudit(null, "PAYMENT_RECEIPT_SUBMITTED", bookingId, customerId, null);
        return toResponse(booking);
    }

    @Transactional
    public AdminBookingSummaryResponse verifyPayment(Long adminId, Long bookingId) {
        Booking booking = getBookingOrThrow(bookingId);
        requireStatus(booking, BookingStatus.VERIFYING_PAYMENT);
        booking.setStatus(BookingStatus.CONFIRMED);
        bookingRepository.save(booking);
        logAudit(adminId, "PAYMENT_VERIFIED", bookingId, booking.getCustomerId(), null);
        return toAdminSummary(booking);
    }

    @Transactional
    public AdminBookingSummaryResponse rejectPayment(Long adminId, Long bookingId, String reason) {
        Booking booking = getBookingOrThrow(bookingId);
        requireStatus(booking, BookingStatus.VERIFYING_PAYMENT);
        booking.setStatus(BookingStatus.PAYMENT_REJECTED);
        booking.setRejectionReason(reason);
        bookingRepository.save(booking);
        logAudit(adminId, "PAYMENT_REJECTED", bookingId, booking.getCustomerId(), reason);
        return toAdminSummary(booking);
    }

    private void requireOwnership(Booking booking, Long customerId) {
        if (!booking.getCustomerId().equals(customerId)) {
            throw new BookingNotFoundException(booking.getId()); // same reasoning as getById — don't reveal existence to a non-owner
        }
    }

    @Transactional
    public BookingResponse withdraw(Long customerId, Long bookingId) {
        Booking booking = getBookingOrThrow(bookingId);
        requireOwnership(booking, customerId);
        // 8.9: only valid BEFORE any payment — PENDING_APPROVAL or
        // AWAITING_PAYMENT. No tier, no refund, just releases the hold.
        if (booking.getStatus() != BookingStatus.PENDING_APPROVAL
                && booking.getStatus() != BookingStatus.AWAITING_PAYMENT) {
            throw new InvalidBookingStateException(
                    "Cannot withdraw a booking in status " + booking.getStatus());
        }
        booking.setStatus(BookingStatus.WITHDRAWN);
        bookingRepository.save(booking);
        logAudit(null, "BOOKING_WITHDRAWN", bookingId, customerId, null);
        return toResponse(booking);
    }

    @Transactional
    public CancelBookingResponse cancel(Long customerId, Long bookingId) {
        Booking booking = getBookingOrThrow(bookingId);
        requireOwnership(booking, customerId);
        requireStatus(booking, BookingStatus.CONFIRMED);

        TierResult tier = cancellationTierCalculator.calculate(booking.getArrivalDate());
        booking.setStatus(BookingStatus.CANCELLED_TIERED);
        booking.setCancellationTier(tier.tier());
        booking.setCancellationRefundPercent(tier.refundPercent());
        bookingRepository.save(booking);

        logAudit(null, "BOOKING_CANCELLED_TIERED", bookingId, customerId, tier.tier());
        return new CancelBookingResponse(booking.getId(), booking.getStatus().name(), tier.tier(), tier.refundPercent());
    }

    @Transactional
    public CancelBookingResponse forceMajeureCancel(Long adminId, Long bookingId, String reason) {
        // 8.7 / 6.3: bypasses the tier table entirely — always 100% cash,
        // no fee, support-initiated only. Only staff can call this endpoint
        // at all (enforced by security config), so no separate role check
        // is needed here beyond that.
        Booking booking = getBookingOrThrow(bookingId);
        requireStatus(booking, BookingStatus.CONFIRMED);

        booking.setStatus(BookingStatus.CANCELLED_FORCE_MAJEURE);
        booking.setCancellationTier("FORCE_MAJEURE");
        booking.setCancellationRefundPercent(new BigDecimal("100.00"));
        booking.setRejectionReason(reason); // reusing this field to store the force-majeure reason
        bookingRepository.save(booking);

        logAudit(adminId, "BOOKING_CANCELLED_FORCE_MAJEURE", bookingId, booking.getCustomerId(), reason);
        return new CancelBookingResponse(booking.getId(), booking.getStatus().name(), "FORCE_MAJEURE", new BigDecimal("100.00"));
    }

    // Spec section 8 / settings: payment_window_hours is a named, configurable
// value (48 as a sensible default). Field injection via @Value sits fine
// alongside Lombok's constructor injection for the final fields.
    @Value("${nepalgo.booking.payment-window-hours:48}")
    private long paymentWindowHours;

    @Transactional
    public int expireOverdueBookings() {
        Instant cutoff = Instant.now().minus(Duration.ofHours(paymentWindowHours));
        List<Booking> overdue = bookingRepository
                .findByStatusAndAwaitingPaymentSinceBefore(BookingStatus.AWAITING_PAYMENT, cutoff);
        for (Booking b : overdue) {
            b.setStatus(BookingStatus.EXPIRED);
            bookingRepository.save(b);
            // EXPIRED isn't one of the three "active hold" statuses, so the
            // date-range hold is released automatically with no extra code.
            logAudit(null, "BOOKING_EXPIRED", b.getId(), b.getCustomerId(), "payment window elapsed");
        }
        return overdue.size();
    }

    @Transactional
    public BookingResponse submitIdentity(Long customerId, Long bookingId, IdentityRequest request) {
        Booking booking = getBookingOrThrow(bookingId);
        requireOwnership(booking, customerId);
        requireStatus(booking, BookingStatus.AWAITING_PAYMENT);
        booking.setLeadBookerLegalName(request.legalName());
        booking.setLeadBookerPassportNumber(request.passportNumber());
        booking.setLeadBookerEmail(request.email());
        bookingRepository.save(booking);
        // Deliberately NO details in the audit row: passport number is PII and
        // should never be copied into a log table.
        logAudit(null, "IDENTITY_SUBMITTED", bookingId, customerId, null);
        return toResponse(booking);
    }
}
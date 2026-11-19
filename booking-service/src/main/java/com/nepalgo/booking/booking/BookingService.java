package com.nepalgo.booking.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nepalgo.booking.client.CatalogClient;
import com.nepalgo.booking.client.PaymentClient;
import com.nepalgo.booking.client.LegalClient;
import com.nepalgo.booking.client.AuthClient;
import com.nepalgo.booking.client.NotificationPublisher;
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


@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingAuditLogRepository auditLogRepository;
    private final CatalogClient catalogClient;
    private final VoucherCodeGenerator voucherCodeGenerator;
    private final ObjectMapper objectMapper;
    private final com.nepalgo.booking.review.ReviewRepository reviewRepository;
    private final CancellationTierCalculator cancellationTierCalculator;
    private final PaymentClient paymentClient;
    private final LegalClient legalClient;
    private final AuthClient authClient;
    private final NotificationPublisher notifications;
    private final PaymentDeadline paymentDeadline;

    @Transactional
    public BookingResponse create(Long customerId, CreateBookingRequest request) {
        requireActiveCustomer(customerId);
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
        // From the catalogue, never from the client: id and snapshot must describe the same itinerary.
        booking.setItineraryVersionId(destination.defaultItinerary().id());

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
                b.getPaymentReceiptId(), b.getPaymentReceiptCode(), b.getRejectionReason(),
                b.getInternalNotes(), b.getCancellationTier(), b.getCancellationRefundPercent(), b.getCreatedAt(),
                paymentDeadline.dueAt(b));
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
                b.isGuideRequested(), b.isPorterRequested(), b.getSpecialRequestText(), b.getCreatedAt(),
                b.getInvoiceNumber(), b.getRejectionReason(), b.getDestinationId(), b.getItinerarySnapshot(),
                paymentDeadline.dueAt(b));
    }

    // "Approve & Issue Bill" (spec 8, Step 2). Order matters:
    //   1. payment-service issues the bill (assigns the invoice number) — FIRST
    //   2. only then does the booking move to AWAITING_PAYMENT
    // If step 1 fails (bill not priced, no Payoneer link, service down) the
    // booking does not move: fail-closed. If step 2 fails after step 1 worked,
    // the admin clicks again and the idempotent issue returns the SAME number.
    @Transactional
    public AdminBookingSummaryResponse approve(Long adminId, Long bookingId) {
        Booking booking = getBookingOrThrow(bookingId);
        requireStatus(booking, BookingStatus.PENDING_APPROVAL);
        PaymentClient.BillStatus bill = paymentClient.issueBill(bookingId, adminId);
        booking.setInvoiceNumber(bill.invoiceNumber());
        booking.setStatus(BookingStatus.AWAITING_PAYMENT);
        booking.setAwaitingPaymentSince(Instant.now());
        bookingRepository.save(booking);
        logAudit(adminId, "BOOKING_APPROVED", bookingId, booking.getCustomerId(), "bill " + bill.invoiceNumber());
        notifications.publish("BILL_ISSUED", "BILL_ISSUED:" + bill.invoiceNumber(), booking.getCustomerId(), bookingId,
                java.util.Map.of("destinationName", booking.getDestinationNameSnapshot(), "voucherCode", booking.getVoucherCode(), "amountUsd", bill.payableUsd()));
        return toAdminSummary(booking);
    }

    // Manual bookings (8b) start in AWAITING_PAYMENT without a bill. Admin prices
    // the bill in payment-service, then issues it here. The payment window starts
    // NOW — the customer couldn't pay before there was a bill to pay.
    @Transactional
    public AdminBookingSummaryResponse issueBillForManualBooking(Long adminId, Long bookingId) {
        Booking booking = getBookingOrThrow(bookingId);
        requireStatus(booking, BookingStatus.AWAITING_PAYMENT);
        if (booking.getInvoiceNumber() != null) {
            throw new InvalidBookingStateException("Bill already issued: " + booking.getInvoiceNumber());
        }
        PaymentClient.BillStatus bill = paymentClient.issueBill(bookingId, adminId);
        booking.setInvoiceNumber(bill.invoiceNumber());
        booking.setAwaitingPaymentSince(Instant.now());
        bookingRepository.save(booking);
        logAudit(adminId, "BILL_ISSUED", bookingId, booking.getCustomerId(), "bill " + bill.invoiceNumber());
        notifications.publish("MANUAL_BOOKING_BILL_READY", "BILL_ISSUED:" + bill.invoiceNumber(), booking.getCustomerId(), bookingId,
                java.util.Map.of("destinationName", booking.getDestinationNameSnapshot(), "voucherCode", booking.getVoucherCode(), "amountUsd", bill.payableUsd()));
        return toAdminSummary(booking);
    }

    @Transactional
    public AdminBookingSummaryResponse reject(Long adminId, Long bookingId, String reason) {
        Booking booking = getBookingOrThrow(bookingId);
        requireStatus(booking, BookingStatus.PENDING_APPROVAL);
        booking.setStatus(BookingStatus.REJECTED);
        booking.setRejectionReason(reason);
        bookingRepository.save(booking);
        paymentClient.releaseCreditsQuietly(bookingId);   // reserved travel credits go back to the wallet
        logAudit(adminId, "BOOKING_REJECTED", bookingId, booking.getCustomerId(), reason);
        notifications.publish("BOOKING_REJECTED", "BOOKING_REJECTED:booking:" + bookingId, booking.getCustomerId(), bookingId,
                java.util.Map.of("destinationName", booking.getDestinationNameSnapshot(), "voucherCode", booking.getVoucherCode(), "reason", reason));
        return toAdminSummary(booking);
    }

    @Transactional
    public AdminBookingSummaryResponse createManual(Long adminId, CreateManualBookingRequest request) {
        requireActiveCustomer(request.customerId());
        var destination = catalogClient.getDestination(request.destinationId());
        boolean custom = request.customItinerary() != null && !request.customItinerary().isEmpty();
        // Snapshot uses the SAME JSON shape as a catalogue version, so the app and
        // admin render a custom trip with zero changes. id = null, tier = CUSTOM.
        CatalogClient.ItineraryVersionResponse itinerary = custom
                ? new CatalogClient.ItineraryVersionResponse(null, "CUSTOM",
                    java.util.stream.IntStream.range(0, request.customItinerary().size())
                        .mapToObj(i -> {
                            var d = request.customItinerary().get(i);
                            return new CatalogClient.ItineraryDayResponse(i + 1, d.narrative().trim(),
                                    d.hotelName() == null || d.hotelName().isBlank() ? null : d.hotelName().trim(),
                                    d.hotelLat(), d.hotelLng());
                        }).toList())
                : destination.defaultItinerary();
        if (itinerary == null) {
            throw new InvalidBookingStateException(
                    "This destination has no DEFAULT itinerary yet. Add a custom itinerary or author the default first.");
        }

        int durationDays = itinerary.days().size();
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
        booking.setItineraryVersionId(custom ? null : itinerary.id());   // never a client-supplied id

        try {
            booking.setItinerarySnapshot(objectMapper.writeValueAsString(itinerary));
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
        logAudit(adminId, "BOOKING_CREATED_MANUALLY", booking.getId(), request.customerId(),
                custom ? "custom itinerary, " + durationDays + " days" : "DEFAULT itinerary, " + durationDays + " days");

        return toAdminSummary(booking);
    }

    public AdminBookingPageResponse adminList(AdminSearch f, int page, int size) {
        Page<Booking> result = bookingRepository.adminSearch(blank(f.status()), f.customerId(), f.destinationId(),
                blank(f.origin()), f.arrivalFrom(), f.arrivalTo(), blank(f.q()), blank(f.sort()),
                PageRequest.of(page, Math.min(size, 100)));
        var content = result.getContent().stream().map(this::toAdminSummary).toList();
        return new AdminBookingPageResponse(content, page, size, result.getTotalElements(), result.getTotalPages());
    }

    public record AdminSearch(String status, Long customerId, Long destinationId, String origin,
                              LocalDate arrivalFrom, LocalDate arrivalTo, String q, String sort) {}

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    // Dashboard "what needs attention now" counters (spec 15.1).
    public java.util.Map<String, Long> stats() {
        java.util.Map<String, Long> m = new java.util.TreeMap<>();
        for (BookingStatus s : BookingStatus.values()) m.put(s.name(), 0L);
        for (Object[] row : bookingRepository.countByStatus()) {
            m.put((String) row[0], ((Number) row[1]).longValue());
        }
        m.put("MANUAL_AWAITING_BILL", bookingRepository.countByStatusAndInvoiceNumberIsNull(BookingStatus.AWAITING_PAYMENT));
        return m;
    }

    // Booking-service's slice of the activity timeline (spec 15.1). The admin
    // frontend merges it with payment-service's bill timeline at render time.
    public java.util.List<TimelineEntry> timeline(Long bookingId) {
        getBookingOrThrow(bookingId);
        return auditLogRepository.findByBookingIdOrderByCreatedAt(bookingId).stream()
                .map(a -> new TimelineEntry(a.getId(), a.getActionType(), a.getAdminUserId(), a.getCustomerId(),
                        a.getDetails(), a.getCreatedAt()))
                .toList();
    }

    public record TimelineEntry(Long id, String actionType, Long adminUserId, Long customerId, String details,
                                Instant createdAt) {}

    @Transactional
    public AdminBookingDetailResponse updateInternalNotes(Long adminId, Long bookingId, String notes) {
        Booking b = getBookingOrThrow(bookingId);
        b.setInternalNotes(notes == null || notes.isBlank() ? null : notes.strip());
        bookingRepository.save(b);
        logAudit(adminId, "INTERNAL_NOTES_UPDATED", bookingId, b.getCustomerId(),
                b.getInternalNotes() == null ? "(cleared)" : b.getInternalNotes());
        return adminDetail(bookingId);
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
                b.getCustomerId(), b.getDestinationNameSnapshot(), b.getArrivalDate(), b.getOrigin().name(),
                b.getPartySize(), b.getInvoiceNumber(), b.getLeadBookerLegalName(), b.getCreatedAt());
    }

    @Transactional
    public BookingResponse submitPaymentReceipt(Long customerId, Long bookingId, String receiptCode,
                                                org.springframework.web.multipart.MultipartFile file) {
        Booking booking = getBookingOrThrow(bookingId);
        requireOwnership(booking, customerId);
        requireStatus(booking, BookingStatus.AWAITING_PAYMENT);

        requireActiveCustomer(customerId);
        if (booking.getInvoiceNumber() == null) {
            // Server-side guard: you can't pay a bill that hasn't been issued yet.
            throw new InvalidBookingStateException("The bill for this booking has not been issued yet");
        }
        // Spec 8 Step 3: verify SERVER-SIDE that a ConsentRecord exists for this
        // booking against the CURRENT Terms version — whatever screen the app showed.
        // legal-service owns consent; if it can't answer we refuse (fail-closed).
        LegalClient.ConsentCheck consent = legalClient.checkConsent(bookingId, customerId);
        if (!consent.valid()) {
            throw new ConsentRequiredException("CONSENT_FOR_OUTDATED_TERMS".equals(consent.reason())
                    ? "Our Terms were updated. Please review and agree to the current version before paying."
                    : "Please review and agree to the Terms (Policy Review) before submitting payment.",
                    consent.reason(), consent.currentTermsDocumentId());
        }

        // All booking-side checks passed: hand the image to payment-service, which
        // validates it is a real image, stores it privately and returns its id.
        PaymentClient.StoredReceipt stored = paymentClient.storeReceipt(bookingId, customerId, receiptCode, file);
        booking.setPaymentReceiptId(stored.receiptId());
        booking.setPaymentReceiptCode(stored.receiptCode());
        booking.setRejectionReason(null);   // a fresh attempt clears the previous kick-back reason
        booking.setStatus(BookingStatus.VERIFYING_PAYMENT);
        bookingRepository.save(booking);
        logAudit(null, "PAYMENT_RECEIPT_SUBMITTED", bookingId, customerId, null);
        notifications.publish("RECEIPT_SUBMITTED", "RECEIPT_SUBMITTED:receipt:" + stored.receiptId(), customerId, bookingId,
                java.util.Map.of("destinationName", booking.getDestinationNameSnapshot(), "voucherCode", booking.getVoucherCode(), "receiptCode", stored.receiptCode()));
        return toResponse(booking);
    }

    // "Payment Verified - Issue Tickets": lock the bill FIRST (it becomes a final
    // tax document, buyer name stamped from the identity form), then confirm.
    @Transactional
    public AdminBookingSummaryResponse verifyPayment(Long adminId, Long bookingId) {
        Booking booking = getBookingOrThrow(bookingId);
        requireStatus(booking, BookingStatus.VERIFYING_PAYMENT);
        PaymentClient.BillStatus bill = paymentClient.lockBill(bookingId, adminId, booking.getLeadBookerLegalName());
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setRejectionReason(null);
        bookingRepository.save(booking);
        logAudit(adminId, "PAYMENT_VERIFIED", bookingId, booking.getCustomerId(), "bill " + bill.invoiceNumber() + " locked");
        notifications.publish("PAYMENT_VERIFIED", "PAYMENT_VERIFIED:booking:" + bookingId, booking.getCustomerId(), bookingId,
                java.util.Map.of("destinationName", booking.getDestinationNameSnapshot(), "voucherCode", booking.getVoucherCode(), "invoiceNumber", bill.invoiceNumber()));
        return toAdminSummary(booking);
    }

    // Decision D1: kick back to AWAITING_PAYMENT (not a dead end) with a reason the
    // customer sees, clear the bad receipt, and give them a FRESH payment window
    // — otherwise a slow admin review could leave them only minutes to retry.
    @Transactional
    public AdminBookingSummaryResponse rejectPayment(Long adminId, Long bookingId, String reason) {
        Booking booking = getBookingOrThrow(bookingId);
        requireStatus(booking, BookingStatus.VERIFYING_PAYMENT);
        booking.setStatus(BookingStatus.AWAITING_PAYMENT);
        booking.setAwaitingPaymentSince(Instant.now());
        booking.setRejectionReason(reason);
        Long rejectedReceiptId = booking.getPaymentReceiptId();
        booking.setPaymentReceiptId(null);
        booking.setPaymentReceiptCode(null);
        bookingRepository.save(booking);
        logAudit(adminId, "PAYMENT_REJECTED", bookingId, booking.getCustomerId(), reason);
        notifications.publish("PAYMENT_REJECTED", "PAYMENT_REJECTED:receipt:" + rejectedReceiptId, booking.getCustomerId(), bookingId,
                java.util.Map.of("destinationName", booking.getDestinationNameSnapshot(), "voucherCode", booking.getVoucherCode(), "reason", reason));
        return toAdminSummary(booking);
    }

    // Spec 15.2: a suspended customer can view trips and reach support, but can't
    // start NEW activity (trip requests, payment submissions). auth-service owns
    // the account status; we ask it every time (fail-closed if it can't answer).
    private void requireActiveCustomer(Long customerId) {
        AuthClient.CustomerStatus c = authClient.customer(customerId);
        if ("SUSPENDED".equals(c.status())) {
            throw new CustomerSuspendedException();
        }
    }

    private void requireOwnership(Booking booking, Long customerId) {
        if (!booking.getCustomerId().equals(customerId)) {
            throw new BookingNotFoundException(booking.getId()); // same reasoning as getById — don't reveal existence to a non-owner
        }
    }

    public record ErasureResult(int bookingsWithdrawn, int bookingsAnonymized) {}

    // Account deletion (spec 15.2), booking-service's part. Called by auth-service
    // BEFORE it deletes the account (fail-closed). Think of a bank closing an
    // account: open applications are cancelled, but the statements of past
    // transactions stay on file for the tax authority - with the customer's
    // personal details struck out.
    //  - PENDING_APPROVAL / AWAITING_PAYMENT -> WITHDRAWN (8.9 rules, credits released)
    //  - VERIFYING_PAYMENT -> refuse: money is in flight; deleting now would
    //    remove the details staff need to verify and refund it. The app tells the
    //    customer to try again after verification.
    //  - every booking keeps its row, customer_id reference, voucher and amounts,
    //    but loses legal name, passport number, e-mail and free-text request.
    // Idempotent: running it twice changes nothing more.
    @Transactional
    public ErasureResult eraseCustomer(Long customerId) {
        List<Booking> bookings = bookingRepository.findByCustomerId(customerId);
        if (bookings.stream().anyMatch(b -> b.getStatus() == BookingStatus.VERIFYING_PAYMENT)) {
            throw new InvalidBookingStateException(
                    "A payment is being verified. The account can be deleted once that is finished.");
        }
        int withdrawn = 0, anonymized = 0;
        for (Booking b : bookings) {
            if (b.getStatus() == BookingStatus.PENDING_APPROVAL || b.getStatus() == BookingStatus.AWAITING_PAYMENT) {
                b.setStatus(BookingStatus.WITHDRAWN);
                paymentClient.releaseCreditsQuietly(b.getId());
                logAudit(null, "BOOKING_WITHDRAWN", b.getId(), customerId, "Account deleted by customer");
                withdrawn++;
            }
            if (b.getLeadBookerLegalName() != null || b.getLeadBookerPassportNumber() != null
                    || b.getLeadBookerEmail() != null || b.getSpecialRequestText() != null) {
                b.setLeadBookerLegalName(null);
                b.setLeadBookerPassportNumber(null);
                b.setLeadBookerEmail(null);
                b.setSpecialRequestText(null);
                logAudit(null, "PERSONAL_DATA_ERASED", b.getId(), customerId, "Account deleted by customer");
                anonymized++;
            }
        }
        bookingRepository.saveAll(bookings);
        // Reviews are the customer's own words: they leave with the account (same transaction).
        reviewRepository.deleteByCustomerId(customerId);
        return new ErasureResult(withdrawn, anonymized);
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
        paymentClient.releaseCreditsQuietly(bookingId);
        logAudit(null, "BOOKING_WITHDRAWN", bookingId, customerId, null);
        return toResponse(booking);
    }

    @Transactional
    public CancelBookingResponse cancel(Long customerId, Long bookingId) {
        Booking booking = getBookingOrThrow(bookingId);
        requireOwnership(booking, customerId);
        requireStatus(booking, BookingStatus.CONFIRMED);

        TierResult tier = cancellationTierCalculator.calculate(booking.getArrivalDate());
        // Money first (payment-service creates the RefundRequest / Travel Credit),
        // status second. Payment's side is idempotent, so a retry is always safe.
        PaymentClient.CancellationOutcome money = paymentClient.cancel(bookingId, tier.tier(), tier.refundPercent(), false);
        booking.setStatus(BookingStatus.CANCELLED_TIERED);
        booking.setCancellationTier(tier.tier());
        booking.setCancellationRefundPercent(tier.refundPercent());
        bookingRepository.save(booking);

        logAudit(null, "BOOKING_CANCELLED_TIERED", bookingId, customerId, tier.tier()
                + " cash $" + money.cashRefundUsd() + ", credit $" + money.creditUsd());
        return new CancelBookingResponse(booking.getId(), booking.getStatus().name(), tier.tier(), tier.refundPercent(),
                money.cashRefundUsd(), money.creditCode(), money.creditUsd());
    }

    @Transactional
    public CancelBookingResponse forceMajeureCancel(Long adminId, Long bookingId, String reason) {
        // 8.7 / 6.3: bypasses the tier table entirely — always 100% cash,
        // no fee, support-initiated only. Only staff can call this endpoint
        // at all (enforced by security config), so no separate role check
        // is needed here beyond that.
        Booking booking = getBookingOrThrow(bookingId);
        requireStatus(booking, BookingStatus.CONFIRMED);

        PaymentClient.CancellationOutcome money =
                paymentClient.cancel(bookingId, "FORCE_MAJEURE", new BigDecimal("100.00"), true);
        booking.setStatus(BookingStatus.CANCELLED_FORCE_MAJEURE);
        booking.setCancellationTier("FORCE_MAJEURE");
        booking.setCancellationRefundPercent(new BigDecimal("100.00"));
        booking.setRejectionReason(reason); // reusing this field to store the force-majeure reason
        bookingRepository.save(booking);

        logAudit(adminId, "BOOKING_CANCELLED_FORCE_MAJEURE", bookingId, booking.getCustomerId(), reason);
        return new CancelBookingResponse(booking.getId(), booking.getStatus().name(), "FORCE_MAJEURE",
                new BigDecimal("100.00"), money.cashRefundUsd(), money.creditCode(), money.creditUsd());
    }

    // Spec section 8 / 15: payment_window_hours is a staff-editable value in
    // Billing Settings (payment-service). BookingExpiryJob fetches it BEFORE
    // opening this transaction, so no HTTP call happens while DB locks are held.
    @Transactional
    public int expireOverdueBookings(long paymentWindowHours) {
        Instant cutoff = Instant.now().minus(Duration.ofHours(paymentWindowHours));
        // Only bookings whose bill was issued: a manual booking still being priced
        // has nothing to pay yet, so its window hasn't started.
        List<Booking> overdue = bookingRepository
                .findByStatusAndAwaitingPaymentSinceBeforeAndInvoiceNumberIsNotNull(BookingStatus.AWAITING_PAYMENT, cutoff);
        for (Booking b : overdue) {
            b.setStatus(BookingStatus.EXPIRED);
            bookingRepository.save(b);
            paymentClient.releaseCreditsQuietly(b.getId());
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
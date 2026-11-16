package com.nepalgo.legal.consent;

import com.nepalgo.legal.common.BookingClient;
import com.nepalgo.legal.common.LegalErrors;
import com.nepalgo.legal.document.LegalDocument;
import com.nepalgo.legal.document.LegalDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

// Records and verifies consent for the gated Policy Review (spec 9).
// The UI's locked checkboxes make it easy for an honest customer; this service
// is what makes it actually enforced — a client that skips the screen can't
// fake a valid record, and booking-service refuses payment without one.
@Service
@RequiredArgsConstructor
public class ConsentService {

    private final ConsentRecordRepository repository;
    private final LegalDocumentService documents;
    private final BookingClient bookingClient;

    @Value("${nepalgo.consent.max-age-hours:24}")
    private long maxAgeHours;

    @Value("${nepalgo.consent.clock-skew-seconds:120}")
    private long clockSkewSeconds;

    public record SectionTimes(Instant expandedAt, Instant checkedAt) {}

    public record ConsentInput(Long bookingId, Long termsDocumentId, SectionTimes cancellationMatrix,
                               SectionTimes forceMajeureWeather, SectionTimes identityLiability) {}

    public record ConsentView(Long id, Long bookingId, Long customerId, Long termsDocumentId, String versionLabel,
                              String contentSha256, Instant consentedAt, SectionTimes cancellationMatrix,
                              SectionTimes forceMajeureWeather, SectionTimes identityLiability) {
        static ConsentView of(ConsentRecord r) {
            return new ConsentView(r.getId(), r.getBookingId(), r.getCustomerId(), r.getLegalDocumentId(),
                    r.getVersionLabel(), r.getContentSha256(), r.getConsentedAt(),
                    new SectionTimes(r.getCancellationExpandedAt(), r.getCancellationCheckedAt()),
                    new SectionTimes(r.getForceMajeureExpandedAt(), r.getForceMajeureCheckedAt()),
                    new SectionTimes(r.getIdentityExpandedAt(), r.getIdentityCheckedAt()));
        }
    }

    public record CheckResult(boolean valid, String reason, Long consentId, String versionLabel, Long currentTermsDocumentId) {}

    // Written when the customer taps the (now unlocked) payment button.
    @Transactional
    public ConsentView record(Long customerId, ConsentInput in, String clientIp, String userAgent) {
        LegalDocument terms = documents.currentOrThrow(LegalDocument.Type.TERMS_AND_CONDITIONS);
        // The app must send the id of the version it SHOWED. If admin published a
        // new version mid-checkout, refuse: the customer has to see the new text.
        if (!terms.getId().equals(in.termsDocumentId())) {
            throw LegalErrors.conflict("TERMS_VERSION_CHANGED",
                    "The Terms were updated while you were reviewing them. Please review the current version.")
                    .with("currentTermsDocumentId", terms.getId())
                    .with("currentVersionLabel", terms.getVersionLabel());
        }

        BookingClient.BookingSummary booking = bookingClient.get(in.bookingId());
        if (!booking.customerId().equals(customerId)) {
            throw LegalErrors.notFound("BOOKING_NOT_FOUND", "Booking not found: " + in.bookingId());   // don't reveal others' bookings
        }
        if (!"AWAITING_PAYMENT".equals(booking.status())) {
            throw LegalErrors.conflict("INVALID_BOOKING_STATE",
                    "Terms are agreed right before payment; this booking is " + booking.status());
        }

        // Idempotent: a double tap (or retry after a timeout) returns the same record.
        var existing = repository.findByBookingIdAndLegalDocumentId(in.bookingId(), terms.getId());
        if (existing.isPresent()) {
            return ConsentView.of(existing.get());
        }

        Instant now = Instant.now();
        validate("cancellationMatrix", in.cancellationMatrix(), now);
        validate("forceMajeureWeather", in.forceMajeureWeather(), now);
        validate("identityLiability", in.identityLiability(), now);
        Instant lastTick = Stream.of(in.cancellationMatrix(), in.forceMajeureWeather(), in.identityLiability())
                .map(SectionTimes::checkedAt).max(Instant::compareTo).orElseThrow();

        ConsentRecord r = new ConsentRecord();
        r.setCustomerId(customerId);
        r.setBookingId(in.bookingId());
        r.setLegalDocumentId(terms.getId());
        r.setVersionLabel(terms.getVersionLabel());
        r.setContentSha256(terms.getContentSha256());
        r.setCancellationExpandedAt(in.cancellationMatrix().expandedAt());
        r.setCancellationCheckedAt(in.cancellationMatrix().checkedAt());
        r.setForceMajeureExpandedAt(in.forceMajeureWeather().expandedAt());
        r.setForceMajeureCheckedAt(in.forceMajeureWeather().checkedAt());
        r.setIdentityExpandedAt(in.identityLiability().expandedAt());
        r.setIdentityCheckedAt(in.identityLiability().checkedAt());
        // Server time of the tap, never earlier than the last tick (allowed clock skew).
        r.setConsentedAt(now.isAfter(lastTick) ? now : lastTick);
        r.setClientIp(truncate(clientIp, 64));
        r.setUserAgent(truncate(userAgent, 500));
        repository.save(r);
        return ConsentView.of(r);
    }

    // Each card: opened, then ticked; not in the future; not from a stale screen.
    private void validate(String section, SectionTimes t, Instant now) {
        if (t == null || t.expandedAt() == null || t.checkedAt() == null) {
            throw LegalErrors.badRequest("SECTION_NOT_ACKNOWLEDGED",
                    "Each Policy Review card must be opened and ticked: " + section + " is missing");
        }
        if (t.checkedAt().isBefore(t.expandedAt())) {
            throw LegalErrors.badRequest("SECTION_ORDER", section + ": ticked before it was opened");
        }
        Instant latest = now.plusSeconds(clockSkewSeconds);
        if (t.expandedAt().isAfter(latest) || t.checkedAt().isAfter(latest)) {
            throw LegalErrors.badRequest("SECTION_IN_FUTURE", section + ": timestamp is in the future (check device clock)");
        }
        if (t.expandedAt().isBefore(now.minus(Duration.ofHours(maxAgeHours)))) {
            throw LegalErrors.badRequest("SECTION_STALE", section + ": review is older than " + maxAgeHours + "h; please review again");
        }
    }

    // booking-service asks this before accepting a payment receipt (spec 8, Step 3).
    @Transactional(readOnly = true)
    public CheckResult check(Long bookingId, Long customerId) {
        LegalDocument terms = documents.currentOrThrow(LegalDocument.Type.TERMS_AND_CONDITIONS);
        return repository.findByBookingIdAndLegalDocumentId(bookingId, terms.getId())
                .filter(r -> r.getCustomerId().equals(customerId))
                .map(r -> new CheckResult(true, null, r.getId(), r.getVersionLabel(), terms.getId()))
                .orElseGet(() -> new CheckResult(false,
                        repository.findByBookingIdOrderByConsentedAtDesc(bookingId).isEmpty()
                                ? "NO_CONSENT" : "CONSENT_FOR_OUTDATED_TERMS",
                        null, null, terms.getId()));
    }

    @Transactional(readOnly = true)
    public List<ConsentView> forCustomer(Long customerId, Long bookingId) {
        return repository.findByCustomerIdOrderByConsentedAtDesc(customerId).stream()
                .filter(r -> bookingId == null || bookingId.equals(r.getBookingId()))
                .map(ConsentView::of).toList();
    }

    @Transactional(readOnly = true)
    public List<ConsentView> forBooking(Long bookingId) {
        return repository.findByBookingIdOrderByConsentedAtDesc(bookingId).stream().map(ConsentView::of).toList();
    }

    private static String truncate(String s, int max) {
        return s == null ? null : (s.length() <= max ? s : s.substring(0, max));
    }
}

package com.nepalgo.legal.consent;

import com.nepalgo.legal.common.BookingClient;
import com.nepalgo.legal.common.LegalErrors;
import com.nepalgo.legal.document.LegalDocument;
import com.nepalgo.legal.document.LegalDocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Spec 9: the server is what makes the gated Policy Review real. These tests pin
// down every way a client could try to fake or shortcut a consent.
class ConsentServiceTest {

    ConsentRecordRepository repo = mock(ConsentRecordRepository.class);
    LegalDocumentService docs = mock(LegalDocumentService.class);
    BookingClient bookings = mock(BookingClient.class);
    ConsentService service = new ConsentService(repo, docs, bookings);
    LegalDocument terms;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "maxAgeHours", 24L);
        ReflectionTestUtils.setField(service, "clockSkewSeconds", 120L);
        terms = new LegalDocument();
        terms.setId(3L);
        terms.setVersionLabel("v2");
        terms.setContentSha256("a".repeat(64));
        when(docs.currentOrThrow(LegalDocument.Type.TERMS_AND_CONDITIONS)).thenReturn(terms);
        when(bookings.get(10L)).thenReturn(new BookingClient.BookingSummary(10L, 1L, "AWAITING_PAYMENT"));
        when(repo.findByBookingIdAndLegalDocumentId(10L, 3L)).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    static ConsentService.SectionTimes ok(int openedSecsAgo, int tickedSecsAgo) {
        Instant now = Instant.now();
        return new ConsentService.SectionTimes(now.minusSeconds(openedSecsAgo), now.minusSeconds(tickedSecsAgo));
    }

    static ConsentService.ConsentInput input(long docId, ConsentService.SectionTimes c, ConsentService.SectionTimes f, ConsentService.SectionTimes i) {
        return new ConsentService.ConsentInput(10L, docId, c, f, i);
    }

    private static String code(Runnable r) {
        try { r.run(); return "none"; }
        catch (LegalErrors.LegalException e) { return (String) ReflectionTestUtils.getField(e, "code"); }
    }

    @Test
    void validConsentStoresExactVersionAndHash() {
        var v = service.record(1L, input(3L, ok(90, 80), ok(70, 60), ok(50, 40)), "1.2.3.4", "test");
        assertThat(v.versionLabel()).isEqualTo("v2");
        assertThat(v.contentSha256()).isEqualTo("a".repeat(64));
        assertThat(v.consentedAt()).isAfterOrEqualTo(v.identityLiability().checkedAt());
    }

    @Test
    void staleVersionRejected_customerMustSeeTheNewTerms() {
        assertThat(code(() -> service.record(1L, input(2L, ok(90, 80), ok(70, 60), ok(50, 40)), null, null)))
                .isEqualTo("TERMS_VERSION_CHANGED");
    }

    @Test
    void cardMissingRejected() {
        assertThat(code(() -> service.record(1L, input(3L, ok(90, 80), null, ok(50, 40)), null, null)))
                .isEqualTo("SECTION_NOT_ACKNOWLEDGED");
    }

    @Test
    void tickedBeforeOpenedRejected() {
        assertThat(code(() -> service.record(1L, input(3L, ok(10, 90), ok(70, 60), ok(50, 40)), null, null)))
                .isEqualTo("SECTION_ORDER");
    }

    @Test
    void futureAndStaleTimestampsRejected() {
        // opened +58 min, ticked +60 min from now: correct order, but in the future (device clock wrong / forged)
        assertThat(code(() -> service.record(1L, input(3L, ok(-3500, -3600), ok(70, 60), ok(50, 40)), null, null)))
                .isEqualTo("SECTION_IN_FUTURE");
        assertThat(code(() -> service.record(1L, input(3L, ok(26 * 3600, 25 * 3600), ok(70, 60), ok(50, 40)), null, null)))
                .isEqualTo("SECTION_STALE");
    }

    @Test
    void someoneElsesBookingLooksLikeNotFound() {
        when(bookings.get(10L)).thenReturn(new BookingClient.BookingSummary(10L, 999L, "AWAITING_PAYMENT"));
        assertThat(code(() -> service.record(1L, input(3L, ok(90, 80), ok(70, 60), ok(50, 40)), null, null)))
                .isEqualTo("BOOKING_NOT_FOUND");
    }

    @Test
    void onlyBeforePayment() {
        when(bookings.get(10L)).thenReturn(new BookingClient.BookingSummary(10L, 1L, "CONFIRMED"));
        assertThatThrownBy(() -> service.record(1L, input(3L, ok(90, 80), ok(70, 60), ok(50, 40)), null, null))
                .isInstanceOf(LegalErrors.LegalException.class);
    }

    @Test
    void checkReportsOutdatedConsent() {
        ConsentRecord old = new ConsentRecord();
        old.setCustomerId(1L);
        when(repo.findByBookingIdOrderByConsentedAtDesc(10L)).thenReturn(java.util.List.of(old));   // consent exists, but for v1
        var r = service.check(10L, 1L);
        assertThat(r.valid()).isFalse();
        assertThat(r.reason()).isEqualTo("CONSENT_FOR_OUTDATED_TERMS");
        assertThat(r.currentTermsDocumentId()).isEqualTo(3L);
    }
}

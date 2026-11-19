package com.nepalgo.booking.booking;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentDeadlineTest {

    private static final Instant SINCE = Instant.parse("2026-11-19T08:00:00Z");

    @Test
    void issuedBillCountsDownFromAwaitingSince() {
        assertThat(PaymentDeadline.dueAt(BookingStatus.AWAITING_PAYMENT, "NG-2026-000001", SINCE, 48))
                .isEqualTo(Instant.parse("2026-11-21T08:00:00Z"));
    }

    @Test
    void noDeadlineWithoutAnIssuedBill() {   // manual booking still being priced
        assertThat(PaymentDeadline.dueAt(BookingStatus.AWAITING_PAYMENT, null, SINCE, 48)).isNull();
    }

    @Test
    void noDeadlineInOtherStatuses() {
        for (BookingStatus s : BookingStatus.values()) {
            if (s == BookingStatus.AWAITING_PAYMENT) continue;
            assertThat(PaymentDeadline.dueAt(s, "NG-2026-000001", SINCE, 48)).as(s.name()).isNull();
        }
    }

    @Test
    void unknownWindowShowsNothingRatherThanAGuess() {
        assertThat(PaymentDeadline.dueAt(BookingStatus.AWAITING_PAYMENT, "NG-2026-000001", SINCE, 0)).isNull();
        assertThat(PaymentDeadline.dueAt(BookingStatus.AWAITING_PAYMENT, "NG-2026-000001", null, 48)).isNull();
    }

    @Test
    void cacheUsesLatestWindow() {
        PaymentDeadline d = new PaymentDeadline();
        Booking b = new Booking();
        b.setStatus(BookingStatus.AWAITING_PAYMENT);
        b.setInvoiceNumber("NG-2026-000001");
        b.setAwaitingPaymentSince(SINCE);
        assertThat(d.dueAt(b)).isNull();
        d.update(2);
        assertThat(d.dueAt(b)).isEqualTo(Instant.parse("2026-11-19T10:00:00Z"));
    }
}

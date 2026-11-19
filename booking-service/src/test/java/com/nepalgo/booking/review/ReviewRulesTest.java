package com.nepalgo.booking.review;

import com.nepalgo.booking.booking.Booking;
import com.nepalgo.booking.booking.BookingStatus;
import com.nepalgo.booking.review.ReviewDtos.ModerateRequest.Action;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

/** Pure-rule tests: eligibility window, public display name, moderation state machine, average rounding. */
class ReviewRulesTest {

    private static Booking booking(BookingStatus status, LocalDate arrival, int days) {
        Booking b = new Booking();
        b.setStatus(status);
        b.setArrivalDate(arrival);
        b.setDurationDays(days);
        return b;
    }

    @Test
    void confirmedTripCanBeReviewedFromItsLastDay() {
        Booking b = booking(BookingStatus.CONFIRMED, LocalDate.of(2026, 10, 1), 5);   // 1..5 Oct
        assertThat(ReviewPolicy.tripLastDay(b)).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(ReviewPolicy.ineligibleReason(b, LocalDate.of(2026, 10, 4))).isEqualTo("TRIP_NOT_FINISHED");
        assertThat(ReviewPolicy.ineligibleReason(b, LocalDate.of(2026, 10, 5))).isNull();
        assertThat(ReviewPolicy.ineligibleReason(b, LocalDate.of(2027, 1, 1))).isNull();
    }

    @Test
    void oneDayTripEndsOnArrivalDay() {
        Booking b = booking(BookingStatus.CONFIRMED, LocalDate.of(2026, 10, 1), 1);
        assertThat(ReviewPolicy.tripLastDay(b)).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    void onlyConfirmedTripsQualify() {
        for (BookingStatus s : BookingStatus.values()) {
            if (s == BookingStatus.CONFIRMED) continue;
            Booking b = booking(s, LocalDate.of(2020, 1, 1), 3);
            assertThat(ReviewPolicy.ineligibleReason(b, LocalDate.of(2026, 1, 1)))
                    .as(s.name()).isEqualTo("TRIP_NOT_CONFIRMED");
        }
    }

    @Test
    void displayNameNeverExposesFullSurname() {
        assertThat(ReviewPolicy.displayName("Susir Poudel")).isEqualTo("Susir P.");
        assertThat(ReviewPolicy.displayName("  anna  maria  schmidt ")).isEqualTo("anna S.");
        assertThat(ReviewPolicy.displayName("Cher")).isEqualTo("Cher");
        assertThat(ReviewPolicy.displayName(null)).isEqualTo("NepalGo traveller");   // erased booking
        assertThat(ReviewPolicy.displayName("   ")).isEqualTo("NepalGo traveller");
    }

    @Test
    void moderationTransitions() {
        assertThat(ReviewService.nextStatus(ReviewStatus.PENDING, Action.PUBLISH, null)).isEqualTo(ReviewStatus.PUBLISHED);
        assertThat(ReviewService.nextStatus(ReviewStatus.HIDDEN, Action.PUBLISH, null)).isEqualTo(ReviewStatus.PUBLISHED);
        assertThat(ReviewService.nextStatus(ReviewStatus.PENDING, Action.REJECT, "Contains a phone number"))
                .isEqualTo(ReviewStatus.REJECTED);
        assertThat(ReviewService.nextStatus(ReviewStatus.PUBLISHED, Action.HIDE, "Complaint upheld"))
                .isEqualTo(ReviewStatus.HIDDEN);
    }

    @Test
    void illegalTransitionsAreRefused() {
        assertThatThrownBy(() -> ReviewService.nextStatus(ReviewStatus.REJECTED, Action.PUBLISH, null))
                .isInstanceOf(ReviewException.class).extracting("code").isEqualTo("INVALID_REVIEW_STATE");
        assertThatThrownBy(() -> ReviewService.nextStatus(ReviewStatus.PUBLISHED, Action.REJECT, "x"))
                .extracting("code").isEqualTo("INVALID_REVIEW_STATE");
        assertThatThrownBy(() -> ReviewService.nextStatus(ReviewStatus.PENDING, Action.HIDE, "x"))
                .extracting("code").isEqualTo("INVALID_REVIEW_STATE");
    }

    @Test
    void rejectAndHideNeedANote() {
        assertThatThrownBy(() -> ReviewService.nextStatus(ReviewStatus.PENDING, Action.REJECT, null))
                .extracting("code").isEqualTo("NOTE_REQUIRED");
        assertThatThrownBy(() -> ReviewService.nextStatus(ReviewStatus.PUBLISHED, Action.HIDE, null))
                .extracting("code").isEqualTo("NOTE_REQUIRED");
    }

    @Test
    void averageRoundsHalfUpToOneDecimal() {
        assertThat(ReviewService.average(0, 0)).isEqualTo(0.0);
        assertThat(ReviewService.average(17, 4)).isEqualTo(4.3);   // 4.25 -> 4.3
        assertThat(ReviewService.average(14, 3)).isEqualTo(4.7);   // 4.666 -> 4.7
        assertThat(ReviewService.average(5, 1)).isEqualTo(5.0);
    }
}

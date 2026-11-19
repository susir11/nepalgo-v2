package com.nepalgo.booking.review;

import com.nepalgo.booking.booking.Booking;
import com.nepalgo.booking.booking.BookingStatus;

import java.time.LocalDate;

/**
 * Pure rules (no DB, no clock) so they are trivially unit-testable.
 * Analogy: a chargeback window — you can only dispute a transaction that
 * actually settled, and only once the goods were delivered.
 */
public final class ReviewPolicy {

    private ReviewPolicy() {}

    public static final int MIN_BODY = 10;
    public static final int MAX_BODY = 2000;

    /** Last day of the trip. A 1-day trip ends on its arrival day. */
    public static LocalDate tripLastDay(Booking b) {
        return b.getArrivalDate().plusDays(Math.max(b.getDurationDays(), 1) - 1L);
    }

    /** null = may review; otherwise a machine-readable reason the app turns into friendly copy. */
    public static String ineligibleReason(Booking b, LocalDate today) {
        if (b.getStatus() != BookingStatus.CONFIRMED) return "TRIP_NOT_CONFIRMED";
        if (today.isBefore(tripLastDay(b))) return "TRIP_NOT_FINISHED";
        return null;
    }

    /** "Susir Poudel" -> "Susir P.", blank/erased -> "NepalGo traveller". Public pages never show full names. */
    public static String displayName(String legalName) {
        if (legalName == null || legalName.isBlank()) return "NepalGo traveller";
        String[] parts = legalName.trim().split("\s+");
        String first = parts[0].length() > 40 ? parts[0].substring(0, 40) : parts[0];
        if (parts.length == 1) return first;
        return first + " " + Character.toUpperCase(parts[parts.length - 1].charAt(0)) + ".";
    }
}

package com.nepalgo.booking.booking;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The customer-facing "pay by" time.
 *
 * The window (hours) belongs to payment-service's Billing Settings. BookingExpiryJob
 * already fetches it once a minute, so it stores the latest value here and every
 * booking response reads it from memory: showing a trip never costs a cross-service
 * call. Like a card terminal caching the daily limit instead of phoning the bank
 * on every tap.
 *
 * Until the first successful fetch (a few seconds after startup) the window is
 * unknown and dueAt() returns null: the app then shows no deadline rather than a
 * wrong one.
 */
@Component
public class PaymentDeadline {

    private final AtomicInteger windowHours = new AtomicInteger(0);   // 0 = not known yet

    public void update(int hours) { windowHours.set(hours); }

    public Instant dueAt(Booking b) {
        return dueAt(b.getStatus(), b.getInvoiceNumber(), b.getAwaitingPaymentSince(), windowHours.get());
    }

    /** Pure rule, unit-tested. Same conditions the expiry job uses: only issued bills count down. */
    static Instant dueAt(BookingStatus status, String invoiceNumber, Instant awaitingSince, int windowHours) {
        if (status != BookingStatus.AWAITING_PAYMENT || invoiceNumber == null || awaitingSince == null || windowHours < 1) {
            return null;
        }
        return awaitingSince.plus(Duration.ofHours(windowHours));
    }
}

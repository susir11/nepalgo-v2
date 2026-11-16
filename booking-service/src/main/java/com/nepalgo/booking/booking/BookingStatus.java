package com.nepalgo.booking.booking;

public enum BookingStatus {
    PENDING_APPROVAL, AWAITING_PAYMENT, VERIFYING_PAYMENT, CONFIRMED,
    // PAYMENT_REJECTED removed (decision D1): a rejected payment goes back to
    // AWAITING_PAYMENT with a reason and a fresh payment window.
    REJECTED, EXPIRED,
    CANCELLED_TIERED, CANCELLED_FORCE_MAJEURE, WITHDRAWN
}
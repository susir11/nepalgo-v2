package com.nepalgo.booking.booking;

// 403: account is suspended (fraud/abuse handling, spec 15.2). Existing trips stay visible.
public class CustomerSuspendedException extends RuntimeException {
    public CustomerSuspendedException() {
        super("This account is suspended. Existing trips stay visible; please contact support.");
    }
}

package com.nepalgo.booking.booking;

public class BookingNotFoundException extends RuntimeException {
    public BookingNotFoundException(Long id) {
        super("Booking not found: " + id);
    }
}
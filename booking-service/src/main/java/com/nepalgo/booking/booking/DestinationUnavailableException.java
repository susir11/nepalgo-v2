package com.nepalgo.booking.booking;

public class DestinationUnavailableException extends RuntimeException {
    public DestinationUnavailableException(String message) {
        super(message);
    }
}
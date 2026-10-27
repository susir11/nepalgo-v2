package com.nepalgo.auth.staff;

public class InvalidPendingTokenException extends RuntimeException {
    public InvalidPendingTokenException(String message) {
        super(message);
    }
}
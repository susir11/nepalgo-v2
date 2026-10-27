package com.nepalgo.auth.firebase;

public class InvalidFirebaseTokenException extends RuntimeException {
    public InvalidFirebaseTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
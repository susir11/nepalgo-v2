package com.nepalgo.auth.staff;

public class MfaAlreadyEnabledException extends RuntimeException {
    public MfaAlreadyEnabledException() {
        super("Two-step verification is already enabled. Ask a SUPER_ADMIN to reset it if you lost your device.");
    }
}

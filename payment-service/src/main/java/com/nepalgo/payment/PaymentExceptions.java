package com.nepalgo.payment;

// Every payment-service exception in one holder class (fewer files).
public final class PaymentExceptions {

    private PaymentExceptions() {}

    public static class FxRateNotFound extends RuntimeException {
        public FxRateNotFound(Long id) { super("FX rate not found: " + id); }
    }

    public static class NoApprovedFxRate extends RuntimeException {
        public NoApprovedFxRate(String pair) { super("No approved FX rate for " + pair); }
    }

    public static class InvalidFxRateState extends RuntimeException {
        public InvalidFxRateState(String message) { super(message); }
    }

    public static class SelfApprovalNotAllowed extends RuntimeException {
        public SelfApprovalNotAllowed() {
            super("Maker-checker segregation is enforced: a different admin must approve this");
        }
    }
}
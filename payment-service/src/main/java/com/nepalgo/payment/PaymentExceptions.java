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

    // ---- Bills ----
    public static class BillNotFound extends RuntimeException {
        public BillNotFound(String what) { super("Bill not found: " + what); }
    }

    // 409: request is valid but the bill/booking isn't in a state that allows it.
    public static class InvalidBillState extends RuntimeException {
        public InvalidBillState(String message) { super(message); }
    }

    public static class CreditNotFound extends RuntimeException {
        public CreditNotFound(Long id) { super("Travel credit not found: " + id); }
    }

    public static class RefundNotFound extends RuntimeException {
        public RefundNotFound(Long id) { super("Refund request not found: " + id); }
    }

    // 400: the request itself is incomplete for this situation (e.g. missing correction reason).
    public static class BadBillRequest extends RuntimeException {
        public BadBillRequest(String message) { super(message); }
    }

    // 409: same receipt code or same screenshot already used on another booking.
    public static class DuplicateReceipt extends RuntimeException {
        private final java.util.Set<Long> codeOnBookings;
        private final java.util.Set<Long> imageOnBookings;

        public DuplicateReceipt(String code, java.util.Set<Long> codeOnBookings, java.util.Set<Long> imageOnBookings) {
            super("Possible double claim: receipt '" + code + "' "
                    + (codeOnBookings.isEmpty() ? "" : "code was already used on booking(s) " + codeOnBookings + " ")
                    + (imageOnBookings.isEmpty() ? "" : "image was already used on booking(s) " + imageOnBookings)
                    + ". Verification blocked; check your Payoneer dashboard.");
            this.codeOnBookings = codeOnBookings;
            this.imageOnBookings = imageOnBookings;
        }

        public java.util.Set<Long> getCodeOnBookings() { return codeOnBookings; }
        public java.util.Set<Long> getImageOnBookings() { return imageOnBookings; }
    }

    public static class BillAlreadyExists extends RuntimeException {
        public BillAlreadyExists(Long bookingId) { super("Booking " + bookingId + " already has a bill"); }
    }

    public static class BookingNotFound extends RuntimeException {
        public BookingNotFound(Long bookingId) { super("Booking not found: " + bookingId); }
    }

    // 503: a service we depend on is down; we fail closed instead of guessing.
    public static class DependencyUnavailable extends RuntimeException {
        public DependencyUnavailable(String service, Throwable cause) { super(service + " is unavailable, try again shortly", cause); }
    }

    public static class SelfApprovalNotAllowed extends RuntimeException {
        public SelfApprovalNotAllowed() {
            super("Maker-checker segregation is enforced: a different admin must approve this");
        }
    }
}
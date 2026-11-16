package com.nepalgo.booking.booking;

// 409 with a machine code the app uses to send the customer back to Policy Review.
public class ConsentRequiredException extends RuntimeException {
    private final String reason;
    private final Long currentTermsDocumentId;

    public ConsentRequiredException(String message, String reason, Long currentTermsDocumentId) {
        super(message);
        this.reason = reason;
        this.currentTermsDocumentId = currentTermsDocumentId;
    }

    public String getReason() { return reason; }
    public Long getCurrentTermsDocumentId() { return currentTermsDocumentId; }
}

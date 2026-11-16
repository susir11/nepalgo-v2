package com.nepalgo.booking.booking;

import com.nepalgo.booking.client.CatalogUnavailableException;
import com.nepalgo.booking.client.PaymentClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class BookingExceptionHandler {

    @ExceptionHandler(DestinationUnavailableException.class)
    public ProblemDetail handleUnavailable(DestinationUnavailableException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Destination Unavailable");
        problem.setDetail(e.getMessage());
        problem.setProperty("code", "DESTINATION_UNAVAILABLE");
        return problem;
    }

    @ExceptionHandler(BookingNotFoundException.class)
    public ProblemDetail handleNotFound(BookingNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Booking Not Found");
        problem.setProperty("code", "BOOKING_NOT_FOUND");
        return problem;
    }

    @ExceptionHandler(CatalogUnavailableException.class)
    public ProblemDetail handleCatalogUnavailable(CatalogUnavailableException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
        problem.setTitle("Catalog Service Unavailable");
        problem.setProperty("code", "CATALOG_UNAVAILABLE");
        return problem;
    }

    // payment-service said NO (e.g. bill not priced / no Payoneer link):
    // keep its status and code so the admin sees the real reason.
    @ExceptionHandler(PaymentClient.PaymentRejectedException.class)
    public ProblemDetail handlePaymentRejected(PaymentClient.PaymentRejectedException e) {
        HttpStatus status = HttpStatus.resolve(e.getStatus());
        ProblemDetail problem = ProblemDetail.forStatus(status == null ? HttpStatus.CONFLICT : status);
        problem.setTitle("Payment Service Rejected The Request");
        problem.setDetail(e.getMessage());
        problem.setProperty("code", e.getCode());
        return problem;
    }

    // payment-service unreachable: fail closed, booking unchanged, safe to retry.
    @ExceptionHandler(PaymentClient.PaymentUnavailableException.class)
    public ProblemDetail handlePaymentUnavailable(PaymentClient.PaymentUnavailableException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
        problem.setTitle("Payment Service Unavailable");
        problem.setDetail("Nothing was changed. Try again shortly.");
        problem.setProperty("code", "PAYMENT_SERVICE_UNAVAILABLE");
        return problem;
    }

    @ExceptionHandler(ConsentRequiredException.class)
    public ProblemDetail handleConsentRequired(ConsentRequiredException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Consent Required");
        problem.setDetail(e.getMessage());
        problem.setProperty("code", "CONSENT_REQUIRED");
        problem.setProperty("reason", e.getReason());                       // NO_CONSENT / CONSENT_FOR_OUTDATED_TERMS
        problem.setProperty("currentTermsDocumentId", e.getCurrentTermsDocumentId());
        return problem;
    }

    @ExceptionHandler(com.nepalgo.booking.client.LegalClient.LegalUnavailableException.class)
    public ProblemDetail handleLegalUnavailable(com.nepalgo.booking.client.LegalClient.LegalUnavailableException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
        problem.setTitle("Legal Service Unavailable");
        problem.setDetail("Nothing was changed. Try again shortly.");
        problem.setProperty("code", "LEGAL_SERVICE_UNAVAILABLE");
        return problem;
    }

    @ExceptionHandler(InvalidBookingStateException.class)
    public ProblemDetail handleInvalidState(InvalidBookingStateException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Invalid Booking State");
        problem.setDetail(e.getMessage());
        problem.setProperty("code", "INVALID_BOOKING_STATE");
        return problem;
    }
}
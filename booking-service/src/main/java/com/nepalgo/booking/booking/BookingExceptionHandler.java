package com.nepalgo.booking.booking;

import com.nepalgo.booking.client.CatalogUnavailableException;
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

    @ExceptionHandler(InvalidBookingStateException.class)
    public ProblemDetail handleInvalidState(InvalidBookingStateException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Invalid Booking State");
        problem.setDetail(e.getMessage());
        problem.setProperty("code", "INVALID_BOOKING_STATE");
        return problem;
    }
}
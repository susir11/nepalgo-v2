package com.nepalgo.payment;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class PaymentExceptionHandler {

    private ProblemDetail problem(HttpStatus status, String title, String detail, String code) {
        ProblemDetail p = ProblemDetail.forStatus(status);
        p.setTitle(title);
        p.setDetail(detail);
        p.setProperty("code", code);   // stable machine-readable code, same convention as other services
        return p;
    }

    // Bean Validation failures (@Valid on a request body). Without this, Spring
    // returns a generic 400 with no hint WHICH field was wrong, and no "code".
    // We list every bad field so a client (Flutter / React admin) can highlight it.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validation(MethodArgumentNotValidException e) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(fe -> fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        ProblemDetail p = problem(HttpStatus.BAD_REQUEST, "Validation Failed",
                "One or more fields are invalid", "VALIDATION_FAILED");
        p.setProperty("fieldErrors", fieldErrors);
        return p;
    }

    // Malformed JSON / wrong type (e.g. "rate": "abc").
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail unreadable(HttpMessageNotReadableException e) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed Request", "Request body is missing or not valid JSON", "MALFORMED_REQUEST");
    }

    @ExceptionHandler(PaymentExceptions.FxRateNotFound.class)
    public ProblemDetail fxNotFound(PaymentExceptions.FxRateNotFound e) {
        return problem(HttpStatus.NOT_FOUND, "FX Rate Not Found", e.getMessage(), "FX_RATE_NOT_FOUND");
    }

    @ExceptionHandler(PaymentExceptions.NoApprovedFxRate.class)
    public ProblemDetail noApproved(PaymentExceptions.NoApprovedFxRate e) {
        return problem(HttpStatus.NOT_FOUND, "No Approved FX Rate", e.getMessage(), "NO_APPROVED_FX_RATE");
    }

    @ExceptionHandler(PaymentExceptions.InvalidFxRateState.class)
    public ProblemDetail invalidState(PaymentExceptions.InvalidFxRateState e) {
        return problem(HttpStatus.CONFLICT, "Invalid FX Rate State", e.getMessage(), "INVALID_FX_RATE_STATE");
    }

    @ExceptionHandler(PaymentExceptions.BillNotFound.class)
    public ProblemDetail billNotFound(PaymentExceptions.BillNotFound e) {
        return problem(HttpStatus.NOT_FOUND, "Bill Not Found", e.getMessage(), "BILL_NOT_FOUND");
    }

    @ExceptionHandler(PaymentExceptions.CreditNotFound.class)
    public ProblemDetail creditNotFound(PaymentExceptions.CreditNotFound e) {
        return problem(HttpStatus.NOT_FOUND, "Travel Credit Not Found", e.getMessage(), "CREDIT_NOT_FOUND");
    }

    @ExceptionHandler(PaymentExceptions.RefundNotFound.class)
    public ProblemDetail refundNotFound(PaymentExceptions.RefundNotFound e) {
        return problem(HttpStatus.NOT_FOUND, "Refund Request Not Found", e.getMessage(), "REFUND_NOT_FOUND");
    }

    @ExceptionHandler(PaymentExceptions.BookingNotFound.class)
    public ProblemDetail bookingNotFound(PaymentExceptions.BookingNotFound e) {
        return problem(HttpStatus.NOT_FOUND, "Booking Not Found", e.getMessage(), "BOOKING_NOT_FOUND");
    }

    @ExceptionHandler(PaymentExceptions.InvalidBillState.class)
    public ProblemDetail invalidBillState(PaymentExceptions.InvalidBillState e) {
        return problem(HttpStatus.CONFLICT, "Invalid Bill State", e.getMessage(), "INVALID_BILL_STATE");
    }

    @ExceptionHandler(PaymentExceptions.BadBillRequest.class)
    public ProblemDetail badBill(PaymentExceptions.BadBillRequest e) {
        return problem(HttpStatus.BAD_REQUEST, "Bad Bill Request", e.getMessage(), "BAD_BILL_REQUEST");
    }

    @ExceptionHandler(PaymentExceptions.DuplicateReceipt.class)
    public ProblemDetail duplicateReceipt(PaymentExceptions.DuplicateReceipt e) {
        ProblemDetail p = problem(HttpStatus.CONFLICT, "Duplicate Receipt", e.getMessage(), "DUPLICATE_RECEIPT");
        p.setProperty("codeUsedOnBookings", e.getCodeOnBookings());
        p.setProperty("imageUsedOnBookings", e.getImageOnBookings());
        return p;
    }

    // Upload above spring.servlet.multipart.max-file-size.
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ProblemDetail tooLarge(org.springframework.web.multipart.MaxUploadSizeExceededException e) {
        return problem(HttpStatus.CONTENT_TOO_LARGE, "File Too Large", "Image is larger than 10 MB", "FILE_TOO_LARGE");
    }

    @ExceptionHandler(PaymentExceptions.BillAlreadyExists.class)
    public ProblemDetail billExists(PaymentExceptions.BillAlreadyExists e) {
        return problem(HttpStatus.CONFLICT, "Bill Already Exists", e.getMessage(), "BILL_ALREADY_EXISTS");
    }

    @ExceptionHandler(PaymentExceptions.DependencyUnavailable.class)
    public ProblemDetail dependencyDown(PaymentExceptions.DependencyUnavailable e) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Dependency Unavailable", e.getMessage(), "DEPENDENCY_UNAVAILABLE");
    }

    // Two admins edited the same bill at once: the second save loses (@Version).
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail staleEdit(ObjectOptimisticLockingFailureException e) {
        return problem(HttpStatus.CONFLICT, "Concurrent Edit",
                "This bill was changed by someone else. Reload it and apply your edit again.", "CONCURRENT_EDIT");
    }

    // Last-resort guard for DB constraints (e.g. second current bill for a booking
    // created in a race): the database said no, so we say 409, never 500.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail integrity(DataIntegrityViolationException e) {
        return problem(HttpStatus.CONFLICT, "Conflict", "The change conflicts with existing data", "DATA_CONFLICT");
    }

    @ExceptionHandler(PaymentExceptions.SelfApprovalNotAllowed.class)
    public ProblemDetail selfApproval(PaymentExceptions.SelfApprovalNotAllowed e) {
        return problem(HttpStatus.FORBIDDEN, "Self-Approval Not Allowed", e.getMessage(), "SELF_APPROVAL_NOT_ALLOWED");
    }
}
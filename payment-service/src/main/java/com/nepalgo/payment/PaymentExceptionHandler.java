package com.nepalgo.payment;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
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

    @ExceptionHandler(PaymentExceptions.SelfApprovalNotAllowed.class)
    public ProblemDetail selfApproval(PaymentExceptions.SelfApprovalNotAllowed e) {
        return problem(HttpStatus.FORBIDDEN, "Self-Approval Not Allowed", e.getMessage(), "SELF_APPROVAL_NOT_ALLOWED");
    }
}
package com.nepalgo.auth.firebase;

import com.nepalgo.auth.staff.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class FirebaseExceptionHandler {

    @ExceptionHandler(InvalidFirebaseTokenException.class)
    public ProblemDetail handleInvalidToken(InvalidFirebaseTokenException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        problem.setTitle("Invalid Firebase Token");
        problem.setDetail(e.getMessage());
        problem.setProperty("code", "AUTH_INVALID_FIREBASE_TOKEN");
        return problem;
    }

    @ExceptionHandler(InvalidStaffCredentialsException.class)
    public ProblemDetail handleInvalidStaffCredentials(InvalidStaffCredentialsException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        problem.setTitle("Invalid Credentials");
        problem.setDetail("Incorrect email or password"); // deliberately generic
        problem.setProperty("code", "AUTH_INVALID_CREDENTIALS");
        return problem;
    }

    @ExceptionHandler(com.nepalgo.auth.staff.MfaAlreadyEnabledException.class)
    public ProblemDetail handleMfaAlreadyEnabled(com.nepalgo.auth.staff.MfaAlreadyEnabledException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("MFA Already Enabled");
        problem.setDetail(e.getMessage());
        problem.setProperty("code", "AUTH_MFA_ALREADY_ENABLED");
        return problem;
    }

    // Unknown / expired / reused customer refresh token -> 401 so the app signs the
    // user out cleanly (before this handler existed it surfaced as a 500).
    @ExceptionHandler(com.nepalgo.auth.customer.InvalidRefreshTokenException.class)
    public ProblemDetail handleInvalidCustomerRefresh(com.nepalgo.auth.customer.InvalidRefreshTokenException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        problem.setTitle("Invalid Refresh Token");
        problem.setDetail(e.getMessage());
        problem.setProperty("code", "AUTH_INVALID_REFRESH_TOKEN");
        return problem;
    }

    @ExceptionHandler(com.nepalgo.auth.customer.AccountDeletionBlockedException.class)
    public ProblemDetail handleDeletionBlocked(com.nepalgo.auth.customer.AccountDeletionBlockedException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Account Deletion Not Possible Yet");
        problem.setDetail(e.getMessage());
        problem.setProperty("code", "ACCOUNT_DELETION_BLOCKED");
        return problem;
    }

    @ExceptionHandler(com.nepalgo.auth.customer.DeletionDependencyUnavailableException.class)
    public ProblemDetail handleDeletionUnavailable(com.nepalgo.auth.customer.DeletionDependencyUnavailableException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
        problem.setTitle("Service Unavailable");
        problem.setDetail(e.getMessage());
        problem.setProperty("code", "ACCOUNT_DELETION_UNAVAILABLE");
        return problem;
    }

    @ExceptionHandler(InvalidMfaCodeException.class)
    public ProblemDetail handleInvalidMfaCode(InvalidMfaCodeException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        problem.setTitle("Invalid MFA Code");
        problem.setProperty("code", "AUTH_INVALID_MFA_CODE");
        return problem;
    }

    @ExceptionHandler(InvalidStaffRefreshTokenException.class)
    public ProblemDetail handleInvalidStaffRefreshToken(InvalidStaffRefreshTokenException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        problem.setTitle("Invalid Refresh Token");
        problem.setProperty("code", "AUTH_INVALID_STAFF_REFRESH_TOKEN");
        return problem;
    }

    @ExceptionHandler(InvalidPendingTokenException.class)
    public ProblemDetail handleInvalidPendingToken(InvalidPendingTokenException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        problem.setTitle("Invalid Pending Token");
        problem.setProperty("code", "AUTH_INVALID_PENDING_TOKEN");
        return problem;
    }

    @ExceptionHandler(StaffAlreadyExistsException.class)
    public ProblemDetail handleStaffAlreadyExists(StaffAlreadyExistsException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Staff Already Exists");
        problem.setProperty("code", "STAFF_EMAIL_ALREADY_EXISTS");
        return problem;
    }
}
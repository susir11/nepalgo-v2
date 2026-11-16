package com.nepalgo.legal.common;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

// All legal-service errors: one exception type carrying an HTTP status and a
// stable machine code, rendered as RFC 9457 ProblemDetail (same shape as the
// other services, so the apps can handle every service's errors the same way).
public final class LegalErrors {

    private LegalErrors() {}

    public static class LegalException extends RuntimeException {
        private final HttpStatus status;
        private final String code;
        private final Map<String, Object> extra = new LinkedHashMap<>();

        public LegalException(HttpStatus status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }

        public LegalException with(String key, Object value) {
            extra.put(key, value);
            return this;
        }
    }

    public static LegalException notFound(String code, String msg) { return new LegalException(HttpStatus.NOT_FOUND, code, msg); }
    public static LegalException conflict(String code, String msg) { return new LegalException(HttpStatus.CONFLICT, code, msg); }
    public static LegalException badRequest(String code, String msg) { return new LegalException(HttpStatus.BAD_REQUEST, code, msg); }
    public static LegalException unavailable(String msg) { return new LegalException(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE", msg); }

    @RestControllerAdvice
    public static class Handler {

        private static ProblemDetail problem(HttpStatus status, String title, String detail, String code) {
            ProblemDetail p = ProblemDetail.forStatus(status);
            p.setTitle(title);
            p.setDetail(detail);
            p.setProperty("code", code);
            return p;
        }

        @ExceptionHandler(LegalException.class)
        public ProblemDetail legal(LegalException e) {
            ProblemDetail p = problem(e.status, e.status.getReasonPhrase(), e.getMessage(), e.code);
            e.extra.forEach(p::setProperty);
            return p;
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ProblemDetail validation(MethodArgumentNotValidException e) {
            Map<String, String> fields = new LinkedHashMap<>();
            e.getBindingResult().getFieldErrors().forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
            ProblemDetail p = problem(HttpStatus.BAD_REQUEST, "Validation Failed", "One or more fields are invalid", "VALIDATION_FAILED");
            p.setProperty("fieldErrors", fields);
            return p;
        }

        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ProblemDetail unreadable(HttpMessageNotReadableException e) {
            return problem(HttpStatus.BAD_REQUEST, "Malformed Request", "Request body is missing or not valid JSON", "MALFORMED_REQUEST");
        }

        @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
        public ProblemDetail stale(ObjectOptimisticLockingFailureException e) {
            return problem(HttpStatus.CONFLICT, "Concurrent Edit", "Someone else changed this; reload and try again", "CONCURRENT_EDIT");
        }

        // DB guards (immutable published text, append-only consent, unique current version).
        @ExceptionHandler(DataIntegrityViolationException.class)
        public ProblemDetail integrity(DataIntegrityViolationException e) {
            return problem(HttpStatus.CONFLICT, "Conflict", "The change conflicts with existing data", "DATA_CONFLICT");
        }
    }
}

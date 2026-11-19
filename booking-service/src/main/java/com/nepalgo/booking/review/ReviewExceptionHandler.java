package com.nepalgo.booking.review;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ReviewExceptionHandler {

    @ExceptionHandler(ReviewException.class)
    public ProblemDetail handle(ReviewException e) {
        ProblemDetail problem = ProblemDetail.forStatus(e.getStatus());
        problem.setTitle("Review Request Refused");
        problem.setDetail(e.getMessage());
        problem.setProperty("code", e.getCode());
        return problem;
    }
}

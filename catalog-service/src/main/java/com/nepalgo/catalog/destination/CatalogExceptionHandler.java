package com.nepalgo.catalog.destination;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class CatalogExceptionHandler {

    @ExceptionHandler(DestinationNotFoundException.class)
    public ProblemDetail handleNotFound(DestinationNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Destination Not Found");
        problem.setDetail(e.getMessage());
        problem.setProperty("code", "DESTINATION_NOT_FOUND");
        return problem;
    }

    @ExceptionHandler(com.nepalgo.catalog.weather.WeatherUnavailableException.class)
    public ProblemDetail handleWeatherUnavailable(com.nepalgo.catalog.weather.WeatherUnavailableException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
        problem.setTitle("Weather Unavailable");
        problem.setDetail(e.getMessage());
        problem.setProperty("code", "WEATHER_UNAVAILABLE");
        return problem;
    }

    // Bad input from the admin (e.g. duplicate day numbers, unknown itinerary tier): 400, not 500.
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadInput(IllegalArgumentException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Invalid Request");
        problem.setDetail(e.getMessage());
        problem.setProperty("code", "INVALID_REQUEST");
        return problem;
    }

    @ExceptionHandler(com.nepalgo.catalog.image.InvalidImageException.class)
    public ProblemDetail handleInvalidImage(com.nepalgo.catalog.image.InvalidImageException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Invalid Image");
        problem.setDetail(e.getMessage());
        problem.setProperty("code", "INVALID_IMAGE");
        return problem;
    }

    @ExceptionHandler(com.nepalgo.catalog.image.ImageProcessingException.class)
    public ProblemDetail handleImageProcessing(com.nepalgo.catalog.image.ImageProcessingException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setTitle("Image Processing Failed");
        problem.setProperty("code", "IMAGE_PROCESSING_FAILED");
        return problem;
    }
}
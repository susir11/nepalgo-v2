package com.nepalgo.booking.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record CreateBookingRequest(
        @NotNull Long destinationId,
        @NotNull Long itineraryVersionId,
        @Min(1) int partySize,
        @NotNull @Future LocalDate arrivalDate,
        boolean guideRequested,
        boolean porterRequested,
        String specialRequestText
) {}
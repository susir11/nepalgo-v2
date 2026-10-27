package com.nepalgo.catalog.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record ItineraryDayRequest(
        @Min(1) int dayNumber,
        @NotBlank String narrative,
        String hotelName,
        BigDecimal hotelLat,
        BigDecimal hotelLng
) {}
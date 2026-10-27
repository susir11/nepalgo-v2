package com.nepalgo.catalog.dto;

import java.math.BigDecimal;

public record ItineraryDayResponse(
        int dayNumber, String narrative, String hotelName, BigDecimal hotelLat, BigDecimal hotelLng
) {}
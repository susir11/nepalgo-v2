package com.nepalgo.catalog.dto;

import java.math.BigDecimal;
import java.util.List;

public record DestinationDetailResponse(
        Long id, String name, String region, String type, String description,
        String bestSeason, BigDecimal basePriceUsd, String permitNotes,
        List<String> imageUrls, ItineraryVersionResponse defaultItinerary
) {}
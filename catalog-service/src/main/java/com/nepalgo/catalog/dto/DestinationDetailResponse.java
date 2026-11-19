package com.nepalgo.catalog.dto;

import java.math.BigDecimal;
import java.util.List;

public record DestinationDetailResponse(
        Long id, String name, String region, String type, String description,
        String bestSeason, BigDecimal basePriceUsd, String permitNotes,
        List<String> imageUrls,          // full-size URLs, cover first (kept for older app builds)
        ItineraryVersionResponse defaultItinerary,
        List<Image> images               // id + all sizes: app hero uses medium, admin needs ids to remove
) {
    public record Image(Long id, String thumbnailUrl, String mediumUrl, String fullUrl) {}
}
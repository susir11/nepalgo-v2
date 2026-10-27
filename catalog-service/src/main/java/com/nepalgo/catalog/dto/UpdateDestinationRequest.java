package com.nepalgo.catalog.dto;
import java.math.BigDecimal;


// Same shape as create — a full replace, not a partial patch. Keeps admin
// editing predictable: what you submit is exactly what gets saved.
public record UpdateDestinationRequest(
        String name, String region, String type, String description,
        String bestSeason, java.math.BigDecimal basePriceUsd, String permitNotes,
        BigDecimal latitude,
        BigDecimal longitude
) {}
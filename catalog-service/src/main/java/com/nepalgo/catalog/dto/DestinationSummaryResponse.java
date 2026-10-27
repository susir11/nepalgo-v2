package com.nepalgo.catalog.dto;

import java.math.BigDecimal;

public record DestinationSummaryResponse(
        Long id, String name, String region, String type,
        BigDecimal basePriceUsd, String thumbnailUrl
) {}
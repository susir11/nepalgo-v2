package com.nepalgo.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;


public record CreateDestinationRequest(
        @NotBlank String name,
        @NotBlank String region,
        @NotBlank String type,
        @NotBlank String description,
        String bestSeason,
        @NotNull @DecimalMin("0.0") BigDecimal basePriceUsd,
        String permitNotes,
        @jakarta.validation.constraints.DecimalMin("-90") @jakarta.validation.constraints.DecimalMax("90") BigDecimal latitude,
        @jakarta.validation.constraints.DecimalMin("-180") @jakarta.validation.constraints.DecimalMax("180") BigDecimal longitude

) {}
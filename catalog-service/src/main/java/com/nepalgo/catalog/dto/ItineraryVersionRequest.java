package com.nepalgo.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ItineraryVersionRequest(
        @NotNull String tier,
        @NotEmpty List<@Valid ItineraryDayRequest> days
) {}
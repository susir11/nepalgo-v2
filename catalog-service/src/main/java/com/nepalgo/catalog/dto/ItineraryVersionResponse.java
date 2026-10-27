package com.nepalgo.catalog.dto;

import java.util.List;

public record ItineraryVersionResponse(Long id, String tier, List<ItineraryDayResponse> days) {}
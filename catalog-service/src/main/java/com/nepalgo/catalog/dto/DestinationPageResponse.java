package com.nepalgo.catalog.dto;

import java.util.List;

public record DestinationPageResponse(
        List<DestinationSummaryResponse> content, int page, int size, long totalElements, int totalPages
) {}
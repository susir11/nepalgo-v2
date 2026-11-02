package com.nepalgo.booking.dto;

import java.util.List;

public record AdminBookingPageResponse(
        List<AdminBookingSummaryResponse> content, int page, int size, long totalElements, int totalPages
) {}
package com.nepalgo.booking.dto;

import java.math.BigDecimal;

public record CancelBookingResponse(
        Long id, String status, String cancellationTier, BigDecimal cancellationRefundPercent
) {}
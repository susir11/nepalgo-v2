package com.nepalgo.booking.dto;

import java.math.BigDecimal;

public record CancelBookingResponse(
        Long id, String status, String cancellationTier, BigDecimal cancellationRefundPercent,
        BigDecimal cashRefundUsd,     // queued as a RefundRequest (sent manually by admin via Payoneer)
        String travelCreditCode,      // NG-CR-... wallet card, null if none issued
        BigDecimal travelCreditUsd
) {}
package com.nepalgo.booking.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record AdminBookingDetailResponse(
        Long id, String voucherCode, String status, String origin, Long customerId,
        Long destinationId, String destinationNameSnapshot, String itinerarySnapshot,
        int partySize, LocalDate arrivalDate, int durationDays,
        boolean guideRequested, boolean porterRequested, String specialRequestText,
        String leadBookerLegalName, String leadBookerPassportNumber, String leadBookerEmail,
        boolean consentConfirmed, String paymentReceiptImageUrl, String paymentReceiptCode,
        String rejectionReason, String internalNotes,
        String cancellationTier, BigDecimal cancellationRefundPercent, Instant createdAt
) {}
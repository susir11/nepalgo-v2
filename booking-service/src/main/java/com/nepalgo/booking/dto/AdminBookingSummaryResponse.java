package com.nepalgo.booking.dto;

import java.time.LocalDate;

public record AdminBookingSummaryResponse(
        Long id, String voucherCode, String status, Long customerId,
        String destinationNameSnapshot, LocalDate arrivalDate, String origin,
        int partySize, String invoiceNumber, String leadBookerLegalName, java.time.Instant createdAt
) {}
package com.nepalgo.booking.dto;

import java.time.Instant;
import java.time.LocalDate;

public record BookingResponse(
        Long id, String voucherCode, String status, String destinationNameSnapshot,
        int partySize, LocalDate arrivalDate, int durationDays,
        boolean guideRequested, boolean porterRequested, String specialRequestText,
        Instant createdAt
) {}
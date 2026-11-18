package com.nepalgo.booking.dto;

import java.time.Instant;
import java.time.LocalDate;

public record BookingResponse(
        Long id, String voucherCode, String status, String destinationNameSnapshot,
        int partySize, LocalDate arrivalDate, int durationDays,
        boolean guideRequested, boolean porterRequested, String specialRequestText,
        Instant createdAt,
        String invoiceNumber,      // non-null once the bill is issued (customer can fetch it from payment-service)
        String rejectionReason,    // shown to the customer after a rejection / payment kick-back (spec 8, Step 4)
        Long destinationId,
        String itinerarySnapshot   // JSON of the EXACT itinerary booked (Final Itinerary Review shows this, not today's catalog)
) {}
package com.nepalgo.booking.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// "Create Booking Manually" (spec 8b) — same shape as the customer's own create
// request, plus admin-only fields: which customer it's for, internal notes, and
// optionally a custom day list agreed over WhatsApp. Starts at AWAITING_PAYMENT,
// skipping the approval step, since the WhatsApp conversation already served as
// the availability check.
//
// Itinerary: customItinerary present -> snapshot = those days (tier CUSTOM, no
// catalogue version); absent -> the destination's DEFAULT itinerary, as before.
public record CreateManualBookingRequest(
        @NotNull Long customerId,
        @NotNull Long destinationId,
        Long itineraryVersionId,
        @Min(1) int partySize,
        @NotNull LocalDate arrivalDate,
        boolean guideRequested,
        boolean porterRequested,
        String specialRequestText,
        String internalNotes,
        @Size(min = 1, max = 60) List<@Valid CustomDay> customItinerary
) {
    /** One day of an ad-hoc itinerary. Day number = position in the list. */
    public record CustomDay(
            @NotBlank @Size(max = 2000) String narrative,
            @Size(max = 200) String hotelName,
            @DecimalMin("-90") @DecimalMax("90") BigDecimal hotelLat,
            @DecimalMin("-180") @DecimalMax("180") BigDecimal hotelLng
    ) {}
}

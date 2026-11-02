package com.nepalgo.booking.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

// "Create Booking Manually" — same shape as the customer's own create
// request, plus admin-only fields: which customer it's for, and internal
// notes. Starts at AWAITING_PAYMENT, skipping the approval step entirely,
// since an admin creating it on someone's behalf already represents a
// pre-approved arrangement (section 8b).
public record CreateManualBookingRequest(
        @NotNull Long customerId,
        @NotNull Long destinationId,
        @NotNull Long itineraryVersionId,
        @Min(1) int partySize,
        @NotNull LocalDate arrivalDate,
        boolean guideRequested,
        boolean porterRequested,
        String specialRequestText,
        String internalNotes
) {}
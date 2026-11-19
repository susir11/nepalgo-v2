package com.nepalgo.booking.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record CreateBookingRequest(
        @NotNull Long destinationId,
        // Ignored by the server (kept so older app builds keep working): the booked
        // version always comes from the catalogue's current DEFAULT, the same one
        // that is snapshotted, so the stored id can never disagree with the snapshot.
        Long itineraryVersionId,
        @Min(1) int partySize,
        @NotNull @Future LocalDate arrivalDate,
        boolean guideRequested,
        boolean porterRequested,
        String specialRequestText
) {}
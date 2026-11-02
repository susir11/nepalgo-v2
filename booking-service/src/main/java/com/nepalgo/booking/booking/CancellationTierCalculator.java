package com.nepalgo.booking.booking;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

// Section 8.7 / 6.1: tier is based on days between TODAY and the trip's
// arrival date. cancellationRefundPercent stores the CASH-REFUND-OR-CREDIT
// portion the customer receives (not the fee retained) — e.g. 60+ days
// out retains a 10% fee, so 90% is what's recorded here.
@Component
public class CancellationTierCalculator {

    public record TierResult(String tier, BigDecimal refundPercent) {}

    public TierResult calculate(LocalDate arrivalDate) {
        long daysOut = ChronoUnit.DAYS.between(LocalDate.now(), arrivalDate);

        if (daysOut >= 60) {
            return new TierResult("60_PLUS_DAYS", new BigDecimal("90.00"));
        } else if (daysOut >= 30) {
            return new TierResult("30_59_DAYS", new BigDecimal("70.00"));
        } else if (daysOut >= 15) {
            return new TierResult("15_29_DAYS", new BigDecimal("50.00"));
        } else {
            // Covers 0-14 days AND any negative value (arrival date already
            // passed) — both are "too late," so both fall to the strictest
            // tier rather than needing a separate case.
            return new TierResult("0_14_DAYS", BigDecimal.ZERO);
        }
    }
}
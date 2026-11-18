package com.nepalgo.booking.booking;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

// Spec 8.7 / Terms §6.1: the tier decides how much money the customer gets back,
// so every boundary day is pinned down here (off-by-one = real money).
class CancellationTierCalculatorTest {

    private final CancellationTierCalculator calc = new CancellationTierCalculator();

    @ParameterizedTest(name = "{0} days out -> {1} ({2}%)")
    @CsvSource({
            "400, 60_PLUS_DAYS, 90",
            "60,  60_PLUS_DAYS, 90",   // boundary: exactly 60 days is still the best tier
            "59,  30_59_DAYS,   70",
            "30,  30_59_DAYS,   70",
            "29,  15_29_DAYS,   50",
            "15,  15_29_DAYS,   50",
            "14,  0_14_DAYS,    0",
            "0,   0_14_DAYS,    0",    // cancelling on arrival day
            "-3,  0_14_DAYS,    0",    // trip already started: strictest tier, never an error
    })
    void tiers(int daysOut, String tier, int percent) {
        var r = calc.calculate(LocalDate.now().plusDays(daysOut));
        assertThat(r.tier()).isEqualTo(tier);
        assertThat(r.refundPercent()).isEqualByComparingTo(String.valueOf(percent));
    }
}

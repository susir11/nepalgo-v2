package com.nepalgo.payment.bill;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Plain JUnit, no Spring context: runs in milliseconds.
class BillCalculatorTest {

    private static BigDecimal d(String v) { return new BigDecimal(v); }

    private static final List<BillCalculator.Line> TRIP = List.of(
            new BillCalculator.Line(d("2"), d("5000.00")),     // 2 nights hotel   = 10,000
            new BillCalculator.Line(d("1"), d("12500.00")),    // private car      = 12,500
            new BillCalculator.Line(d("3"), d("2500.00")));    // guide, 3 days    =  7,500

    @Test
    void noVat_serviceChargeOnly() {
        var t = BillCalculator.compute(TRIP, d("10"), false, d("13"), d("136.50"), null);
        assertThat(t.subtotalNpr()).isEqualByComparingTo("30000.00");
        assertThat(t.serviceChargeNpr()).isEqualByComparingTo("3000.00");
        assertThat(t.vatNpr()).isEqualByComparingTo("0.00");
        assertThat(t.grandTotalNpr()).isEqualByComparingTo("33000.00");
        assertThat(t.grandTotalUsd()).isEqualByComparingTo("241.76");   // 33000 / 136.5 = 241.758...
        assertThat(t.payableUsd()).isEqualByComparingTo("241.76");
    }

    @Test
    void vatIsAppliedOnSubtotalPlusServiceCharge() {
        var t = BillCalculator.compute(TRIP, d("10"), true, d("13"), d("136.50"), null);
        assertThat(t.taxableAmountNpr()).isEqualByComparingTo("33000.00");
        assertThat(t.vatNpr()).isEqualByComparingTo("4290.00");          // 13% of 33,000 (not of 30,000)
        assertThat(t.grandTotalNpr()).isEqualByComparingTo("37290.00");
        assertThat(t.grandTotalUsd()).isEqualByComparingTo("273.19");     // 37290 / 136.5 = 273.186...
    }

    @Test
    void storeCreditNetsAgainstUsdTotal() {
        var t = BillCalculator.compute(TRIP, d("10"), false, d("13"), d("136.50"), d("45"));
        assertThat(t.appliedCreditUsd()).isEqualByComparingTo("45.00");
        assertThat(t.payableUsd()).isEqualByComparingTo("196.76");
    }

    @Test
    void creditLargerThanBillIsCappedNeverNegative() {
        var small = List.of(new BillCalculator.Line(d("1"), d("1365.00")));   // = $10 at 136.5
        var t = BillCalculator.compute(small, d("0"), false, d("13"), d("136.50"), d("45"));
        assertThat(t.appliedCreditUsd()).isEqualByComparingTo("10.00");
        assertThat(t.payableUsd()).isEqualByComparingTo("0.00");
    }

    @Test
    void roundingIsHalfUpPerStep() {
        // 1.5 x 333.33 = 499.995 -> 500.00 ; 10% service = 50.00
        var t = BillCalculator.compute(List.of(new BillCalculator.Line(d("1.5"), d("333.33"))),
                d("10"), false, d("13"), d("100"), null);
        assertThat(t.subtotalNpr()).isEqualByComparingTo("500.00");
        assertThat(t.serviceChargeNpr()).isEqualByComparingTo("50.00");
        assertThat(t.grandTotalUsd()).isEqualByComparingTo("5.50");
    }

    @Test
    void emptyBillIsZero() {
        var t = BillCalculator.compute(List.of(), d("10"), true, d("13"), d("136.5"), null);
        assertThat(t.grandTotalNpr()).isEqualByComparingTo("0");
        assertThat(t.payableUsd()).isEqualByComparingTo("0");
    }

    @Test
    void rejectsBadInputs() {
        assertThatThrownBy(() -> BillCalculator.compute(TRIP, d("10"), false, d("13"), d("0"), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BillCalculator.compute(TRIP, d("10"), false, d("13"), d("136.5"), d("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

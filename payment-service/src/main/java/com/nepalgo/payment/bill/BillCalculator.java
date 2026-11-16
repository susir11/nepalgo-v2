package com.nepalgo.payment.bill;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

// Pure money math for a bill (spec section 21, "Computation, in order").
// No Spring, no database: the same inputs always give the same outputs, which
// makes it trivially unit-testable — the part of billing you most want proven.
//
//   subtotal        = Σ line totals                                  (NPR)
//   service charge  = subtotal × service%                            (NPR)
//   VAT             = (subtotal + service charge) × VAT%  if VAT-registered
//   grand total NPR = subtotal + service charge + VAT
//   grand total USD = grand total NPR ÷ fx_rate   (fx_rate = NPR per 1 USD)
//   payable USD     = grand total USD − applied store credit (never below 0)
//
// Rounding: every stored/displayed amount is rounded to 2 dp HALF_UP at the
// step where it is produced (the way a cashier rounds each line on a receipt),
// so the printed lines always add up to the printed totals in NPR.
public final class BillCalculator {

    public static final int MONEY_SCALE = 2;
    private static final RoundingMode RM = RoundingMode.HALF_UP;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private BillCalculator() {}

    public record Line(BigDecimal quantity, BigDecimal unitPriceNpr) {}

    public record Totals(
            BigDecimal subtotalNpr,
            BigDecimal serviceChargeNpr,
            BigDecimal taxableAmountNpr,   // subtotal + service charge (VAT base)
            BigDecimal vatNpr,             // 0.00 when not VAT-registered
            BigDecimal grandTotalNpr,
            BigDecimal grandTotalUsd,
            BigDecimal appliedCreditUsd,   // what was actually netted (capped at the total)
            BigDecimal payableUsd          // what the Payoneer link must be created for
    ) {}

    public static BigDecimal lineTotal(BigDecimal quantity, BigDecimal unitPriceNpr) {
        return quantity.multiply(unitPriceNpr).setScale(MONEY_SCALE, RM);
    }

    // NPR -> USD using the frozen rate (rate = how many NPR one USD buys).
    public static BigDecimal toUsd(BigDecimal npr, BigDecimal fxRate) {
        return npr.divide(fxRate, MONEY_SCALE, RM);
    }

    public static Totals compute(List<Line> lines, BigDecimal serviceChargePercent, boolean vatRegistered,
                                 BigDecimal vatPercent, BigDecimal fxRate, BigDecimal appliedCreditUsd) {
        if (fxRate == null || fxRate.signum() <= 0) {
            throw new IllegalArgumentException("fx rate must be positive");
        }
        BigDecimal subtotal = lines.stream()
                .map(l -> lineTotal(l.quantity(), l.unitPriceNpr()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(MONEY_SCALE, RM);

        BigDecimal serviceCharge = percentOf(subtotal, serviceChargePercent);
        BigDecimal taxable = subtotal.add(serviceCharge);

        // Nepal runs a single flat VAT rate; when not registered there is no VAT line at all.
        BigDecimal vat = vatRegistered ? percentOf(taxable, vatPercent) : BigDecimal.ZERO.setScale(MONEY_SCALE);
        BigDecimal grandNpr = taxable.add(vat);
        BigDecimal grandUsd = toUsd(grandNpr, fxRate);

        // A $45 credit on a $30 bill nets $30, not -$15: credit can't create a refund here.
        BigDecimal credit = appliedCreditUsd == null ? BigDecimal.ZERO : appliedCreditUsd.setScale(MONEY_SCALE, RM);
        if (credit.signum() < 0) {
            throw new IllegalArgumentException("applied credit cannot be negative");
        }
        BigDecimal creditUsed = credit.min(grandUsd);
        BigDecimal payable = grandUsd.subtract(creditUsed);

        return new Totals(subtotal, serviceCharge, taxable, vat, grandNpr, grandUsd, creditUsed, payable);
    }

    private static BigDecimal percentOf(BigDecimal amount, BigDecimal percent) {
        if (percent == null || percent.signum() == 0) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE);
        }
        return amount.multiply(percent).divide(HUNDRED, MONEY_SCALE, RM);
    }
}

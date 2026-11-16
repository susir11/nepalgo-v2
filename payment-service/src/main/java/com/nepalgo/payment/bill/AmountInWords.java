package com.nepalgo.payment.bill;

import java.math.BigDecimal;
import java.math.RoundingMode;

// Nepal tax invoices must show the grand total in words as well as figures.
// Uses the South-Asian grouping (thousand, lakh, crore) that Nepali accountants
// and banks use — 1,25,000 is "One Lakh Twenty Five Thousand", not
// "One Hundred Twenty Five Thousand". Same style as an Indian/Nepali cheque.
//
//   format(new BigDecimal("125450.50"))
//   -> "Rupees One Lakh Twenty Five Thousand Four Hundred Fifty and Paisa Fifty Only"
public final class AmountInWords {

    private static final String[] ONES = {
            "Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
            "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"};
    private static final String[] TENS = {
            "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"};

    private AmountInWords() {}

    public static String format(BigDecimal amountNpr) {
        if (amountNpr == null || amountNpr.signum() < 0) {
            throw new IllegalArgumentException("amount must be zero or positive");
        }
        BigDecimal rounded = amountNpr.setScale(2, RoundingMode.HALF_UP);
        long rupees = rounded.longValue();
        int paisa = rounded.remainder(BigDecimal.ONE).movePointRight(2).intValue();

        StringBuilder sb = new StringBuilder("Rupees ").append(rupees == 0 ? "Zero" : words(rupees));
        if (paisa > 0) {
            sb.append(" and Paisa ").append(belowHundred(paisa));
        }
        return sb.append(" Only").toString();
    }

    // Recursive split: crore (10^7), lakh (10^5), thousand, hundred, rest.
    // Amounts of 100 crore and more fall back into "N Crore", e.g. "One Hundred Crore".
    static String words(long n) {
        StringBuilder sb = new StringBuilder();
        if (n >= 10_000_000) {
            sb.append(words(n / 10_000_000)).append(" Crore ");
            n %= 10_000_000;
        }
        if (n >= 100_000) {
            sb.append(belowHundred((int) (n / 100_000))).append(" Lakh ");
            n %= 100_000;
        }
        if (n >= 1_000) {
            sb.append(belowHundred((int) (n / 1_000))).append(" Thousand ");
            n %= 1_000;
        }
        if (n >= 100) {
            sb.append(ONES[(int) (n / 100)]).append(" Hundred ");
            n %= 100;
        }
        if (n > 0) {
            sb.append(belowHundred((int) n));
        }
        return sb.toString().trim();
    }

    private static String belowHundred(int n) {
        if (n < 20) return ONES[n];
        return TENS[n / 10] + (n % 10 == 0 ? "" : " " + ONES[n % 10]);
    }
}

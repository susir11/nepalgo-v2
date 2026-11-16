package com.nepalgo.payment.bill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AmountInWordsTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "0        | Rupees Zero Only",
            "5        | Rupees Five Only",
            "19       | Rupees Nineteen Only",
            "20       | Rupees Twenty Only",
            "99       | Rupees Ninety Nine Only",
            "100      | Rupees One Hundred Only",
            "1005     | Rupees One Thousand Five Only",
            "33000    | Rupees Thirty Three Thousand Only",
            "100000   | Rupees One Lakh Only",
            "125450.50| Rupees One Lakh Twenty Five Thousand Four Hundred Fifty and Paisa Fifty Only",
            "9999999  | Rupees Ninety Nine Lakh Ninety Nine Thousand Nine Hundred Ninety Nine Only",
            "10000000 | Rupees One Crore Only",
            "37290.05 | Rupees Thirty Seven Thousand Two Hundred Ninety and Paisa Five Only",
            "1500000000 | Rupees One Hundred Fifty Crore Only",
    })
    void formats(String amount, String expected) {
        assertThat(AmountInWords.format(new BigDecimal(amount.trim()))).isEqualTo(expected.trim());
    }

    @Test
    void roundsPaisaHalfUp() {
        assertThat(AmountInWords.format(new BigDecimal("10.995"))).isEqualTo("Rupees Eleven Only");
    }

    @Test
    void rejectsNegative() {
        assertThatThrownBy(() -> AmountInWords.format(new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

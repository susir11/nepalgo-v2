package com.nepalgo.payment.receipt;

import com.nepalgo.payment.PaymentExceptions;
import com.nepalgo.payment.credit.StoreCredit;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Fraud-relevant rules: what counts as "the same receipt code" and what counts
// as an image (by bytes, never by file name).
class ReceiptRulesTest {

    @Test
    void receiptCodesCompareIgnoringCaseSpacesAndDashes() {
        assertThat(PaymentReceipt.normalize("pyn-12 34")).isEqualTo("PYN1234");
        assertThat(PaymentReceipt.normalize(" PYN_12.34 ")).isEqualTo("PYN1234");
        assertThat(PaymentReceipt.normalize(null)).isEmpty();
    }

    @Test
    void imageTypeIsDetectedFromMagicBytes() {
        assertThat(ReceiptService.detectImageType(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0})).isEqualTo("image/jpeg");
        assertThat(ReceiptService.detectImageType(new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10})).isEqualTo("image/png");
        assertThat(ReceiptService.detectImageType("RIFF0000WEBP".getBytes())).isEqualTo("image/webp");
    }

    @Test
    void htmlRenamedToPngIsRejected() {
        assertThatThrownBy(() -> ReceiptService.detectImageType("<html>not an image</html>".getBytes()))
                .isInstanceOf(PaymentExceptions.BadBillRequest.class);
        assertThatThrownBy(() -> ReceiptService.detectImageType(new byte[0]))
                .isInstanceOf(PaymentExceptions.BadBillRequest.class);
    }

    @Test
    void creditExpiryIsDerivedNotStored() {
        StoreCredit c = new StoreCredit();
        c.setStatus(StoreCredit.Status.ACTIVE);
        c.setExpiresOn(LocalDate.now().minusDays(1));
        assertThat(c.isSpendable()).isFalse();
        assertThat(c.displayStatus()).isEqualTo("EXPIRED");
        c.setExpiresOn(LocalDate.now().plusDays(1));
        assertThat(c.isSpendable()).isTrue();
        c.setStatus(StoreCredit.Status.VOID);
        assertThat(c.isSpendable()).isFalse();
        assertThat(c.displayStatus()).isEqualTo("VOID");
    }
}

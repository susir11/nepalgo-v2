package com.nepalgo.notification;

import com.nepalgo.notification.model.Delivery;
import com.nepalgo.notification.model.Notification;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Spec 19 matrix as an executable check: right audience, right channels, and
// every template renders readable customer copy (USD only, never NPR).
class EventCatalogTest {

    @Test
    void matrixMatchesSpec19() {
        assertThat(EventCatalog.EVENTS.get("PAYMENT_VERIFIED").channels()).containsExactly(Delivery.Channel.PUSH, Delivery.Channel.EMAIL);
        assertThat(EventCatalog.EVENTS.get("PAYMENT_VERIFIED").attachInvoice()).isTrue();
        assertThat(EventCatalog.EVENTS.get("RECEIPT_SUBMITTED").audience()).isEqualTo(Notification.Audience.ADMIN);
        assertThat(EventCatalog.EVENTS.get("BILL_ISSUED").channels()).containsExactly(Delivery.Channel.PUSH);
        assertThat(EventCatalog.EVENTS.get("REFUND_SENT").channels()).contains(Delivery.Channel.EMAIL);
        assertThat(EventCatalog.EVENTS.get("CREDIT_ISSUED").channels()).contains(Delivery.Channel.EMAIL);
    }

    @Test
    void everyEventRendersWithAndWithoutData() {
        Map<String, Object> full = Map.of("destinationName", "Everest Base Camp Trek", "voucherCode", "NEPALGO-ABC123",
                "amountUsd", "283.72", "invoiceNumber", "NG-2026-000010", "reason", "Receipt unreadable",
                "receiptCode", "PYN-1", "payoutReference", "PAY-9", "creditCode", "NG-CR-XYZ234", "expiresOn", "2027-11-18");
        for (String type : EventCatalog.EVENTS.keySet()) {
            String[] withData = EventCatalog.render(type, full);
            String[] empty = EventCatalog.render(type, Map.of());
            assertThat(withData[0]).as(type + " title").isNotBlank();
            assertThat(withData[1]).as(type + " body").isNotBlank().doesNotContain("null").doesNotContain("NPR");
            assertThat(empty[1]).as(type + " body w/o data").isNotBlank().doesNotContain("null");
        }
    }

    @Test
    void moneyIsFormattedAsUsd() {
        assertThat(EventCatalog.render("BILL_ISSUED", Map.of("destinationName", "Poon Hill", "amountUsd", "1234.5"))[1]).contains("$1,234.50");
        assertThat(EventCatalog.render("REFUND_SENT", Map.of("amountUsd", 90))[1]).contains("$90.00");
    }

    @Test
    void unknownEventIsRejected() {
        assertThatThrownBy(() -> EventCatalog.render("HACKED", Map.of())).isInstanceOf(IllegalArgumentException.class);
    }
}

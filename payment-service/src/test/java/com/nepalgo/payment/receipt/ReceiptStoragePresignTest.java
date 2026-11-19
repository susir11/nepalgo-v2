package com.nepalgo.payment.receipt;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

/** Presigning is pure local crypto (no network), so this needs no running store. */
class ReceiptStoragePresignTest {

    @Test
    void linksAreSignedForThePublicHostNotTheInternalOne() {
        var storage = new ReceiptStorage("http://rustfs:9000", "https://files.example.com",
                "test-access", "test-secret", "payment-receipts");
        URI link = URI.create(storage.presignedViewUrl("receipts/42/abc.png"));
        assertThat(link.getHost()).isEqualTo("files.example.com");
        assertThat(link.getPath()).isEqualTo("/payment-receipts/receipts/42/abc.png");   // path-style
        assertThat(link.getQuery()).contains("X-Amz-Expires=300").contains("X-Amz-Signature=");
    }

    @Test
    void sameHostWhenOnlyOneEndpointIsConfigured() {   // local dev: both are http://localhost:9000
        var storage = new ReceiptStorage("http://localhost:9000", "http://localhost:9000",
                "test-access", "test-secret", "payment-receipts");
        assertThat(URI.create(storage.presignedViewUrl("k.png")).getAuthority()).isEqualTo("localhost:9000");
    }
}

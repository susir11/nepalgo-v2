package com.nepalgo.booking.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

// booking-service -> payment-service (/internal/**, via Eureka, no gateway).
//
// FAIL-CLOSED: a booking only moves forward if payment-service explicitly said
// yes. Network error / 5xx => PaymentUnavailableException (503) and the booking
// stays where it was. Both payment calls are idempotent, so the admin can just
// click again — the same invoice number comes back, never a second one.
@Component
public class PaymentClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentClient.class);
    private static final String HEADER = "X-Internal-Api-Key";

    private final RestClient.Builder loadBalancedRestClientBuilder;
    private final String internalApiKey;

    // Explicit constructor so @Qualifier lands on the parameter (Lombok wouldn't copy it).
    public PaymentClient(@Qualifier("loadBalancedRestClientBuilder") RestClient.Builder loadBalancedRestClientBuilder,
                         @Value("${nepalgo.internal.api-key}") String internalApiKey) {
        this.loadBalancedRestClientBuilder = loadBalancedRestClientBuilder;
        this.internalApiKey = internalApiKey;
    }

    public BillStatus issueBill(Long bookingId, Long adminId) {
        return post("/internal/bills/issue", Map.of("bookingId", bookingId, "adminId", adminId));
    }

    public BillStatus lockBill(Long bookingId, Long adminId, String buyerName) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("bookingId", bookingId);
        body.put("adminId", adminId);
        body.put("buyerName", buyerName);   // may be null; Map.of() would reject that
        return post("/internal/bills/lock", body);
    }

    // Cancellation money (RefundRequest and/or Travel Credit). Fail-closed + idempotent.
    public CancellationOutcome cancel(Long bookingId, String tier, BigDecimal refundPercent, boolean forceMajeure) {
        Map<String, Object> body = Map.of("bookingId", bookingId, "tier", tier,
                "refundPercent", refundPercent, "forceMajeure", forceMajeure);
        return exchange("/internal/cancellations", () -> loadBalancedRestClientBuilder.build()
                .post().uri("http://payment-service/internal/cancellations")
                .header(HEADER, internalApiKey).body(body)
                .retrieve().body(CancellationOutcome.class));
    }

    // Give reserved travel credits back when a trip request dies (withdrawn,
    // rejected, expired). Deliberately NOT fail-closed: this direction only ever
    // helps the customer, so we never block their withdrawal on it. If it fails,
    // payment-service self-heals: the next time the customer applies that credit,
    // it sees the booking is dead and releases it.
    public void releaseCreditsQuietly(Long bookingId) {
        try {
            exchange("/internal/store-credits/release", () -> loadBalancedRestClientBuilder.build()
                    .post().uri("http://payment-service/internal/store-credits/release")
                    .header(HEADER, internalApiKey).body(Map.of("bookingId", bookingId))
                    .retrieve().toBodilessEntity());
        } catch (RuntimeException e) {
            log.warn("Could not release travel credits for booking {} (will self-heal): {}", bookingId, e.getMessage());
        }
    }

    // Forwards the customer's receipt image + code to payment-service (multipart).
    public StoredReceipt storeReceipt(Long bookingId, Long customerId, String receiptCode, MultipartFile file) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("bookingId", String.valueOf(bookingId));
        parts.add("customerId", String.valueOf(customerId));
        parts.add("receiptCode", receiptCode == null ? "" : receiptCode);
        try {
            // ByteArrayResource needs a filename, otherwise it is sent as a plain field, not a file part.
            String name = file == null || file.getOriginalFilename() == null ? "receipt" : file.getOriginalFilename();
            byte[] bytes = file == null ? new byte[0] : file.getBytes();
            parts.add("file", new ByteArrayResource(bytes) {
                @Override
                public String getFilename() { return name; }
            });
        } catch (java.io.IOException e) {
            throw new PaymentRejectedException(400, "BAD_FILE", "Could not read the uploaded file");
        }
        return exchange("/internal/receipts", () -> loadBalancedRestClientBuilder.build()
                .post()
                .uri("http://payment-service/internal/receipts")
                .header(HEADER, internalApiKey)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .retrieve()
                .body(StoredReceipt.class));
    }

    /**
     * Current payment window from Billing Settings (payment-service owns it, spec 15).
     * Throws PaymentUnavailableException if payment-service can't answer; the
     * expiry job then skips that run instead of guessing a value.
     */
    public int paymentWindowHours() {
        Map<?, ?> r = exchange("/internal/settings/payment-window", () -> loadBalancedRestClientBuilder.build()
                .get()
                .uri("http://payment-service/internal/settings/payment-window")
                .header(HEADER, internalApiKey)
                .retrieve()
                .body(Map.class));
        if (r == null || !(r.get("paymentWindowHours") instanceof Number n) || n.intValue() < 1) {
            throw new PaymentUnavailableException("payment-service returned no payment window", null);
        }
        return n.intValue();
    }

    private BillStatus post(String path, Map<String, Object> body) {
        return exchange(path, () -> loadBalancedRestClientBuilder.build()
                .post()
                .uri("http://payment-service" + path)
                .header(HEADER, internalApiKey)
                .body(body)
                .retrieve()
                .body(BillStatus.class));
    }

    // One place that turns HTTP outcomes into our two failure types.
    private <T> T exchange(String path, java.util.function.Supplier<T> call) {
        try {
            return call.get();
        } catch (HttpClientErrorException e) {
            // 4xx = payment-service understood and said NO (e.g. bill not priced yet).
            // Pass its own code + message through so the admin sees the real reason.
            Map<?, ?> problem = safeBody(e);
            String code = problem.get("code") instanceof String s ? s : "PAYMENT_REJECTED";
            String detail = problem.get("detail") instanceof String s ? s : e.getStatusText();
            throw new PaymentRejectedException(e.getStatusCode().value(), code, detail);
        } catch (Exception e) {
            log.error("payment-service call {} failed", path, e);
            throw new PaymentUnavailableException("payment-service is unavailable, try again shortly", e);
        }
    }

    private static Map<?, ?> safeBody(HttpClientErrorException e) {
        try {
            Map<?, ?> m = e.getResponseBodyAs(Map.class);
            return m == null ? Map.of() : m;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    public record StoredReceipt(Long receiptId, String receiptCode) {}

    public record CancellationOutcome(Long bookingId, String tier, BigDecimal refundPercent, Long refundRequestId,
                                      BigDecimal cashRefundUsd, String creditCode, BigDecimal creditUsd) {}

    public record BillStatus(Long billId, Long bookingId, String invoiceNumber, String status, boolean issued,
                             BigDecimal payableUsd) {}

    public static class PaymentRejectedException extends RuntimeException {
        private final int status;
        private final String code;

        public PaymentRejectedException(int status, String code, String detail) {
            super(detail);
            this.status = status;
            this.code = code;
        }

        public int getStatus() { return status; }
        public String getCode() { return code; }
    }

    public static class PaymentUnavailableException extends RuntimeException {
        public PaymentUnavailableException(String message, Throwable cause) { super(message, cause); }
    }
}

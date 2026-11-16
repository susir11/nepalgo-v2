package com.nepalgo.booking.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

// booking-service -> legal-service: "has this customer agreed to the CURRENT
// Terms for this booking?" Fail-closed: no answer => no payment accepted.
@Component
public class LegalClient {

    private static final Logger log = LoggerFactory.getLogger(LegalClient.class);

    private final RestClient.Builder lb;
    private final String internalApiKey;

    public LegalClient(@Qualifier("loadBalancedRestClientBuilder") RestClient.Builder lb,
                       @Value("${nepalgo.internal.api-key}") String internalApiKey) {
        this.lb = lb;
        this.internalApiKey = internalApiKey;
    }

    public record ConsentCheck(boolean valid, String reason, Long consentId, String versionLabel,
                               Long currentTermsDocumentId) {}

    public ConsentCheck checkConsent(Long bookingId, Long customerId) {
        try {
            ConsentCheck r = lb.build().get()
                    .uri("http://legal-service/internal/consents/check?bookingId={b}&customerId={c}", bookingId, customerId)
                    .header("X-Internal-Api-Key", internalApiKey)
                    .retrieve().body(ConsentCheck.class);
            if (r == null) throw new IllegalStateException("empty response");
            return r;
        } catch (Exception e) {
            log.error("legal-service consent check failed for booking {}", bookingId, e);
            throw new LegalUnavailableException();
        }
    }

    public static class LegalUnavailableException extends RuntimeException {
        public LegalUnavailableException() { super("legal-service is unavailable"); }
    }
}

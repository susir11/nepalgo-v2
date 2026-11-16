package com.nepalgo.booking.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

// booking-service -> auth-service: is this customer allowed to start NEW
// activity? (spec 15.2: suspension blocks new requests and payments.)
// Fail-closed: if auth can't answer, the new booking/payment is refused (503).
@Component
public class AuthClient {

    private static final Logger log = LoggerFactory.getLogger(AuthClient.class);

    private final RestClient.Builder lb;
    private final String internalApiKey;

    public AuthClient(@Qualifier("loadBalancedRestClientBuilder") RestClient.Builder lb,
                      @Value("${nepalgo.internal.api-key}") String internalApiKey) {
        this.lb = lb;
        this.internalApiKey = internalApiKey;
    }

    public record CustomerStatus(Long id, String nepalgoId, String email, String displayName, String status) {}

    public CustomerStatus customer(Long customerId) {
        try {
            CustomerStatus c = lb.build().get().uri("http://auth-service/internal/customers/{id}", customerId)
                    .header("X-Internal-Api-Key", internalApiKey)
                    .retrieve().body(CustomerStatus.class);
            if (c == null) throw new IllegalStateException("empty response");
            return c;
        } catch (Exception e) {
            log.error("auth-service customer lookup failed for {}", customerId, e);
            throw new AuthUnavailableException();
        }
    }

    public static class AuthUnavailableException extends RuntimeException {
        public AuthUnavailableException() { super("auth-service is unavailable"); }
    }
}

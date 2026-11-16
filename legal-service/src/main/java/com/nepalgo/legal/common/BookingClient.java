package com.nepalgo.legal.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

// legal-service -> booking-service: "who owns booking X and what state is it in?"
// Fail-closed: if booking-service can't answer, no consent is recorded (503).
@Component
public class BookingClient {

    private static final Logger log = LoggerFactory.getLogger(BookingClient.class);

    private final RestClient.Builder lb;
    private final String internalApiKey;

    // Explicit constructor: Lombok wouldn't copy @Qualifier (pitfall from Phase 3).
    public BookingClient(@Qualifier("loadBalancedRestClientBuilder") RestClient.Builder lb,
                         @Value("${nepalgo.internal.api-key}") String internalApiKey) {
        this.lb = lb;
        this.internalApiKey = internalApiKey;
    }

    public record BookingSummary(Long id, Long customerId, String status) {}

    public BookingSummary get(Long bookingId) {
        try {
            return lb.build().get().uri("http://booking-service/internal/bookings/{id}", bookingId)
                    .header("X-Internal-Api-Key", internalApiKey)
                    .retrieve().body(BookingSummary.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw LegalErrors.notFound("BOOKING_NOT_FOUND", "Booking not found: " + bookingId);
        } catch (Exception e) {
            log.error("booking-service call failed for booking {}", bookingId, e);
            throw LegalErrors.unavailable("booking-service is unavailable, try again shortly");
        }
    }

    // Same two-builder pattern as the other services: a @Primary plain builder for
    // framework code (Eureka's own client), a @LoadBalanced one for service names.
    @Configuration
    public static class RestClientConfig {
        @Bean
        @Primary
        public RestClient.Builder restClientBuilder() {
            return RestClient.builder();
        }

        @Bean
        @LoadBalanced
        public RestClient.Builder loadBalancedRestClientBuilder() {
            return RestClient.builder();
        }
    }
}

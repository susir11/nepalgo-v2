package com.nepalgo.payment.client;

import com.nepalgo.payment.PaymentExceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

// payment-service -> booking-service, service-to-service via Eureka (no gateway).
// FAIL-CLOSED: if booking-service can't be reached we refuse the money action
// (503) rather than guess. A remittance system that can't confirm the
// beneficiary account doesn't send the money "optimistically" either.
@Component
public class BookingClient {

    private static final Logger log = LoggerFactory.getLogger(BookingClient.class);
    public static final String INTERNAL_KEY_HEADER = "X-Internal-Api-Key";

    private final RestClient.Builder loadBalancedRestClientBuilder;
    private final String internalApiKey;

    // Explicit constructor: Lombok would NOT copy @Qualifier onto a generated one.
    public BookingClient(@Qualifier("loadBalancedRestClientBuilder") RestClient.Builder loadBalancedRestClientBuilder,
                         @Value("${nepalgo.internal.api-key}") String internalApiKey) {
        this.loadBalancedRestClientBuilder = loadBalancedRestClientBuilder;
        this.internalApiKey = internalApiKey;
    }

    public BookingSummary getBooking(Long bookingId) {
        try {
            return loadBalancedRestClientBuilder.build()
                    .get()
                    .uri("http://booking-service/internal/bookings/{id}", bookingId)
                    .header(INTERNAL_KEY_HEADER, internalApiKey)
                    .retrieve()
                    .body(BookingSummary.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new PaymentExceptions.BookingNotFound(bookingId);
        } catch (Exception e) {
            log.error("booking-service call failed for booking {}", bookingId, e);
            throw new PaymentExceptions.DependencyUnavailable("booking-service", e);
        }
    }

    // Minimal view of a booking that payment-service needs. Owned by booking-service.
    public record BookingSummary(Long id, Long customerId, String status, String origin, String voucherCode,
                                 String destinationName, LocalDate arrivalDate, int durationDays, int partySize,
                                 boolean guideRequested, boolean porterRequested, String specialRequestText,
                                 String leadBookerLegalName, String leadBookerEmail) {}
}

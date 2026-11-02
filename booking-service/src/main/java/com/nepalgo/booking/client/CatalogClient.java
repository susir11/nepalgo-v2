package com.nepalgo.booking.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CatalogClient {

    private static final Logger log = LoggerFactory.getLogger(CatalogClient.class);

    private final RestClient.Builder loadBalancedRestClientBuilder;

    // Written explicitly (not via Lombok's @RequiredArgsConstructor) so the
    // @Qualifier annotation definitely lands on the real constructor
    // parameter — Lombok only copies annotations like this onto its
    // generated constructors when lombok.config explicitly lists them
    // under lombok.copyableAnnotations, which this project doesn't have.
    public CatalogClient(@Qualifier("loadBalancedRestClientBuilder") RestClient.Builder loadBalancedRestClientBuilder) {
        this.loadBalancedRestClientBuilder = loadBalancedRestClientBuilder;
    }

    public CatalogDestinationResponse getDestination(Long destinationId) {
        try {
            return loadBalancedRestClientBuilder.build()
                    .get()
                    .uri("http://catalog-service/api/destinations/{id}", destinationId)
                    .retrieve()
                    .body(CatalogDestinationResponse.class);
        } catch (Exception e) {
            log.error("Failed to fetch destination {} from catalog-service", destinationId, e);
            throw new CatalogUnavailableException("Could not fetch destination " + destinationId, e);
        }
    }

    public record CatalogDestinationResponse(
            Long id, String name,
            java.math.BigDecimal basePriceUsd,
            ItineraryVersionResponse defaultItinerary
    ) {}

    public record ItineraryVersionResponse(Long id, String tier, java.util.List<ItineraryDayResponse> days) {}
    public record ItineraryDayResponse(int dayNumber, String narrative, String hotelName,
                                       java.math.BigDecimal hotelLat, java.math.BigDecimal hotelLng) {}
}
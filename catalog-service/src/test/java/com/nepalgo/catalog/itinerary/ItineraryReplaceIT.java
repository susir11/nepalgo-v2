package com.nepalgo.catalog.itinerary;

import com.nepalgo.catalog.destination.Destination;
import com.nepalgo.catalog.destination.DestinationRepository;
import com.nepalgo.catalog.dto.ItineraryDayRequest;
import com.nepalgo.catalog.dto.ItineraryVersionRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression (2026-11-19): saving an EXISTING itinerary again returned 500
 * "duplicate key ... uq_itinerary_days_version_day". Replace = delete old days +
 * insert new ones in one transaction, but Hibernate flushes INSERTs before DELETEs,
 * so "day 1" briefly existed twice. Real Postgres + the real Flyway migrations,
 * because the bug lives in the unique constraint, which an in-memory DB wouldn't mirror.
 */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.default_schema=catalog")   // prod role defaults to its schema; the test user does not
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ItineraryVersionService.class, ItineraryReplaceIT.Pg.class})
class ItineraryReplaceIT {

    // No time-zone workaround needed: com.nepalgo.catalog.UtcTimeZone (spring.factories) sets UTC on startup.

    @TestConfiguration(proxyBeanMethods = false)
    static class Pg {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer(DockerImageName.parse("postgres:17"));   // same major as the real DB
        }
    }

    @Autowired ItineraryVersionService service;
    @Autowired DestinationRepository destinations;
    @Autowired ItineraryDayRepository days;

    private static ItineraryVersionRequest req(int count, String prefix) {
        return new ItineraryVersionRequest("DEFAULT", IntStream.rangeClosed(1, count)
                .mapToObj(n -> new ItineraryDayRequest(n, prefix + " day " + n, null, null, null)).toList());
    }

    private Long newDestination() {
        Destination d = new Destination();
        d.setName("Test trek");
        d.setRegion("Khumbu");
        d.setType("Trek");
        d.setDescription("Test");
        d.setBasePriceUsd(new BigDecimal("100.00"));
        return destinations.saveAndFlush(d).getId();
    }

    @Test
    void savingAnExistingItineraryAgainReplacesItsDays() {
        Long id = newDestination();
        var first = service.createOrReplace(id, req(2, "v1"));
        days.flush();
        var second = service.createOrReplace(id, req(3, "v2"));   // used to fail on flush with the unique constraint
        days.flush();

        assertThat(second.id()).isEqualTo(first.id());              // same version row, days replaced
        List<ItineraryDay> stored = days.findByItineraryVersionIdOrderByDayNumber(second.id());
        assertThat(stored).extracting(ItineraryDay::getDayNumber).containsExactly(1, 2, 3);
        assertThat(stored).extracting(ItineraryDay::getNarrative).containsExactly("v2 day 1", "v2 day 2", "v2 day 3");
    }

    @Test
    void shorteningAnItineraryRemovesTheExtraDays() {
        Long id = newDestination();
        service.createOrReplace(id, req(4, "long"));
        days.flush();
        var v = service.createOrReplace(id, req(2, "short"));
        days.flush();
        assertThat(days.findByItineraryVersionIdOrderByDayNumber(v.id())).hasSize(2);
    }

    @Test
    void duplicateDayNumbersAreA400NotA500() {
        Long id = newDestination();
        var dup = new ItineraryVersionRequest("DEFAULT", List.of(
                new ItineraryDayRequest(1, "a", null, null, null), new ItineraryDayRequest(1, "b", null, null, null)));
        assertThatThrownBy(() -> service.createOrReplace(id, dup)).isInstanceOf(IllegalArgumentException.class);
    }
}

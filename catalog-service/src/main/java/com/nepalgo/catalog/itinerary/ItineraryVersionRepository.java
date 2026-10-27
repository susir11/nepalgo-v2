package com.nepalgo.catalog.itinerary;

import com.nepalgo.catalog.destination.Destination;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ItineraryVersionRepository extends JpaRepository<ItineraryVersion, Long> {
    Optional<ItineraryVersion> findByDestinationAndTier(Destination destination, ItineraryTier tier);
    Optional<ItineraryVersion> findByDestinationIdAndTier(Long destinationId, ItineraryTier tier);
}
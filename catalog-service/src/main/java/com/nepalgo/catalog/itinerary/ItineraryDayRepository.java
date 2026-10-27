package com.nepalgo.catalog.itinerary;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ItineraryDayRepository extends JpaRepository<ItineraryDay, Long> {
    List<ItineraryDay> findByItineraryVersionIdOrderByDayNumber(Long itineraryVersionId);
    int countByItineraryVersionId(Long itineraryVersionId); // used for the duration filter in Step 5
}
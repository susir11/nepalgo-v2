package com.nepalgo.catalog.itinerary;

import com.nepalgo.catalog.destination.Destination;
import com.nepalgo.catalog.destination.DestinationNotFoundException;
import com.nepalgo.catalog.destination.DestinationRepository;
import com.nepalgo.catalog.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ItineraryVersionService {

    private final DestinationRepository destinationRepository;
    private final ItineraryVersionRepository versionRepository;
    private final ItineraryDayRepository dayRepository;

    @Transactional
    public ItineraryVersionResponse createOrReplace(Long destinationId, ItineraryVersionRequest request) {
        Destination destination = destinationRepository.findById(destinationId)
                .orElseThrow(() -> new DestinationNotFoundException(destinationId));
        ItineraryTier tier = ItineraryTier.valueOf(request.tier());

        ItineraryVersion version = versionRepository.findByDestinationAndTier(destination, tier)
                .orElseGet(() -> {
                    ItineraryVersion v = new ItineraryVersion();
                    v.setDestination(destination);
                    v.setTier(tier);
                    return versionRepository.save(v);
                });

        // Whole-document replace: wipe existing days, insert the submitted
        // set. Simpler and safer than diffing — admin always submits the
        // complete itinerary text, never a partial day list.
        dayRepository.deleteAll(dayRepository.findByItineraryVersionIdOrderByDayNumber(version.getId()));

        List<ItineraryDay> days = request.days().stream().map(dto -> {
            ItineraryDay day = new ItineraryDay();
            day.setItineraryVersion(version);
            day.setDayNumber(dto.dayNumber());
            day.setNarrative(dto.narrative());
            day.setHotelName(dto.hotelName());
            day.setHotelLat(dto.hotelLat());
            day.setHotelLng(dto.hotelLng());
            return day;
        }).toList();
        dayRepository.saveAll(days);

        return new ItineraryVersionResponse(version.getId(), version.getTier().name(),
                days.stream().map(d -> new ItineraryDayResponse(
                        d.getDayNumber(), d.getNarrative(), d.getHotelName(), d.getHotelLat(), d.getHotelLng())).toList());
    }

    @Transactional
    public void delete(Long destinationId, Long versionId) {
        versionRepository.deleteById(versionId); // cascades to its days
    }
}
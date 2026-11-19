package com.nepalgo.catalog.destination;

import com.nepalgo.catalog.dto.*;
import com.nepalgo.catalog.itinerary.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DestinationService {

    private final DestinationRepository destinationRepository;
    private final DestinationImageRepository imageRepository;
    private final ItineraryVersionRepository itineraryVersionRepository;
    private final ItineraryDayRepository itineraryDayRepository;
    private final com.nepalgo.catalog.image.ImageUrls imageUrls;

    @Transactional
    public DestinationDetailResponse create(CreateDestinationRequest request) {
        Destination d = new Destination();
        applyFields(d, request.name(), request.region(), request.type(), request.description(),
                request.bestSeason(), request.basePriceUsd(), request.permitNotes(),
                request.latitude(), request.longitude());
        destinationRepository.save(d);
        return toDetail(d);
    }

    @Transactional
    public DestinationDetailResponse update(Long id, UpdateDestinationRequest request) {
        Destination d = getOrThrow(id);
        applyFields(d, request.name(), request.region(), request.type(), request.description(),
                request.bestSeason(), request.basePriceUsd(), request.permitNotes(),
                request.latitude(), request.longitude());
        destinationRepository.save(d);
        return toDetail(d);
    }

    @Transactional
    public void delete(Long id) {
        destinationRepository.deleteById(id); // cascades to images + itinerary versions + days
    }

    public DestinationDetailResponse getById(Long id) {
        return toDetail(getOrThrow(id));
    }

    private void applyFields(Destination d, String name, String region, String type, String description,
                             String bestSeason, BigDecimal basePriceUsd, String permitNotes,
                             BigDecimal latitude, BigDecimal longitude) {
        d.setName(name);
        d.setRegion(region);
        d.setType(type);
        d.setDescription(description);
        d.setBestSeason(bestSeason);
        d.setBasePriceUsd(basePriceUsd);
        d.setPermitNotes(permitNotes);
        d.setLatitude(latitude);
        d.setLongitude(longitude);
    }

    private Destination getOrThrow(Long id) {
        return destinationRepository.findById(id)
                .orElseThrow(() -> new DestinationNotFoundException(id));
    }

    private DestinationDetailResponse toDetail(Destination d) {
        List<DestinationDetailResponse.Image> images = imageRepository.findByDestinationIdOrderByDisplayOrder(d.getId())
                .stream().map(i -> new DestinationDetailResponse.Image(i.getId(), imageUrls.of(i.getThumbnailKey()),
                        imageUrls.of(i.getMediumKey()), imageUrls.of(i.getFullKey()))).toList();

        ItineraryVersionResponse defaultItinerary = itineraryVersionRepository
                .findByDestinationIdAndTier(d.getId(), ItineraryTier.DEFAULT)
                .map(v -> new ItineraryVersionResponse(
                        v.getId(),
                        v.getTier().name(),
                        itineraryDayRepository.findByItineraryVersionIdOrderByDayNumber(v.getId()).stream()
                                .map(day -> new ItineraryDayResponse(
                                        day.getDayNumber(), day.getNarrative(), day.getHotelName(),
                                        day.getHotelLat(), day.getHotelLng()))
                                .toList()))
                .orElse(null);

        return new DestinationDetailResponse(d.getId(), d.getName(), d.getRegion(), d.getType(),
                d.getDescription(), d.getBestSeason(), d.getBasePriceUsd(), d.getPermitNotes(),
                images.stream().map(DestinationDetailResponse.Image::fullUrl).toList(), defaultItinerary, images);
    }
}
package com.nepalgo.catalog.destination;

import com.nepalgo.catalog.dto.DestinationDetailResponse;
import com.nepalgo.catalog.dto.DestinationPageResponse;
import com.nepalgo.catalog.dto.DestinationSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.nepalgo.catalog.dto.WeatherResponse;
import com.nepalgo.catalog.weather.WeatherService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/destinations")
@RequiredArgsConstructor
public class DestinationPublicController {

    private final DestinationRepository destinationRepository;
    private final com.nepalgo.catalog.image.ImageUrls imageUrls;
    private final DestinationImageRepository imageRepository;
    private final DestinationService destinationService;
    private final WeatherService weatherService;

    // Plain, unfiltered list for now — Step 5 adds search/filter query
    // params onto this same endpoint rather than creating a separate one,
    // since "all destinations" is just "search with no filters applied."
    @GetMapping
    public ResponseEntity<DestinationPageResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) java.math.BigDecimal minPrice,
            @RequestParam(required = false) java.math.BigDecimal maxPrice,
            @RequestParam(required = false) Integer minDuration,
            @RequestParam(required = false) Integer maxDuration,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<Destination> result = destinationRepository.search(
                q, region, type, minPrice, maxPrice, minDuration, maxDuration, PageRequest.of(page, size));

        List<Long> ids = result.getContent().stream().map(Destination::getId).toList();
        Map<Long, String> thumbnails = imageRepository.findCoverThumbnailKeys(ids);

        List<DestinationSummaryResponse> content = result.getContent().stream()
                .map(d -> new DestinationSummaryResponse(
                        d.getId(), d.getName(), d.getRegion(), d.getType(),
                        d.getBasePriceUsd(), imageUrls.of(thumbnails.get(d.getId()))))
                .toList();

        return ResponseEntity.ok(new DestinationPageResponse(
                content, page, size, result.getTotalElements(), result.getTotalPages()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DestinationDetailResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(destinationService.getById(id));
    }

    @GetMapping("/{id}/weather")
    public ResponseEntity<WeatherResponse> getWeather(@PathVariable Long id) {
        return ResponseEntity.ok(weatherService.getWeatherForDestination(id));
    }
}
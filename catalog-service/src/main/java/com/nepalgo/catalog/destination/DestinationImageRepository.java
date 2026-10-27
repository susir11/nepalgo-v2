package com.nepalgo.catalog.destination;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Map;

public interface DestinationImageRepository extends JpaRepository<DestinationImage, Long> {
    List<DestinationImage> findByDestinationIdOrderByDisplayOrder(Long destinationId);

    // Fetches only the first image per destination (display_order = 0 or
// lowest), for use in list views — avoids loading every image for every
// destination just to show one thumbnail each.
    default Map<Long, String> findThumbnailUrlsByDestinationIds(List<Long> destinationIds) {
        return findAll().stream()
                .filter(img -> destinationIds.contains(img.getDestination().getId()))
                .collect(java.util.stream.Collectors.groupingBy(
                        img -> img.getDestination().getId(),
                        java.util.stream.Collectors.collectingAndThen(
                                java.util.stream.Collectors.minBy(java.util.Comparator.comparingInt(DestinationImage::getDisplayOrder)),
                                opt -> opt.map(DestinationImage::getThumbnailUrl).orElse(null))));
    }
}


package com.nepalgo.catalog.destination;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Map;

public interface DestinationImageRepository extends JpaRepository<DestinationImage, Long> {
    List<DestinationImage> findByDestinationIdOrderByDisplayOrder(Long destinationId);

    List<DestinationImage> findByDestinationIdInOrderByDisplayOrder(List<Long> destinationIds);

    /** Cover photo KEY (lowest display_order) per destination, for list views. */
    default Map<Long, String> findCoverThumbnailKeys(List<Long> destinationIds) {
        if (destinationIds.isEmpty()) return Map.of();
        Map<Long, String> out = new java.util.HashMap<>();
        for (DestinationImage img : findByDestinationIdInOrderByDisplayOrder(destinationIds)) {
            out.putIfAbsent(img.getDestination().getId(), img.getThumbnailKey());   // first = lowest order
        }
        return out;
    }
}


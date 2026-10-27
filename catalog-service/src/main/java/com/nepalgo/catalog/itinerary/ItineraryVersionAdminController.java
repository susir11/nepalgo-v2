package com.nepalgo.catalog.itinerary;

import com.nepalgo.catalog.dto.ItineraryVersionRequest;
import com.nepalgo.catalog.dto.ItineraryVersionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/destinations/{destinationId}/itinerary-versions")
@RequiredArgsConstructor
public class ItineraryVersionAdminController {

    private final ItineraryVersionService itineraryVersionService;

    // POST and PUT do the same thing — createOrReplace — since the
    // one-version-per-tier constraint means there's never ambiguity about
    // which row is being written to. Having both verbs just matches
    // conventional REST expectations for "create" vs "update" intent.
    @PostMapping
    public ResponseEntity<ItineraryVersionResponse> create(@PathVariable Long destinationId, @Valid @RequestBody ItineraryVersionRequest request) {
        return ResponseEntity.ok(itineraryVersionService.createOrReplace(destinationId, request));
    }

    @PutMapping("/{versionId}")
    public ResponseEntity<ItineraryVersionResponse> update(@PathVariable Long destinationId, @PathVariable Long versionId, @Valid @RequestBody ItineraryVersionRequest request) {
        return ResponseEntity.ok(itineraryVersionService.createOrReplace(destinationId, request));
    }

    @DeleteMapping("/{versionId}")
    public ResponseEntity<Void> delete(@PathVariable Long destinationId, @PathVariable Long versionId) {
        itineraryVersionService.delete(destinationId, versionId);
        return ResponseEntity.noContent().build();
    }
}
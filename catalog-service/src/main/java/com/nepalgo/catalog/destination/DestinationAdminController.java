package com.nepalgo.catalog.destination;

import com.nepalgo.catalog.dto.CreateDestinationRequest;
import com.nepalgo.catalog.dto.DestinationDetailResponse;
import com.nepalgo.catalog.dto.UpdateDestinationRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/destinations")
@RequiredArgsConstructor
public class DestinationAdminController {

    private final DestinationService destinationService;

    @PostMapping
    public ResponseEntity<DestinationDetailResponse> create(@Valid @RequestBody CreateDestinationRequest request) {
        return ResponseEntity.ok(destinationService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DestinationDetailResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateDestinationRequest request) {
        return ResponseEntity.ok(destinationService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        destinationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
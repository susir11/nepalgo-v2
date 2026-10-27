package com.nepalgo.catalog.image;

import com.nepalgo.catalog.destination.DestinationImage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/destinations/{destinationId}/images")
@RequiredArgsConstructor
public class ImageAdminController {

    private final ImageUploadService imageUploadService;

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<ImageResponse> upload(@PathVariable Long destinationId, @RequestParam("file") MultipartFile file) {
        DestinationImage image = imageUploadService.upload(destinationId, file);
        return ResponseEntity.ok(new ImageResponse(image.getId(), image.getThumbnailUrl(), image.getMediumUrl(), image.getFullUrl()));
    }

    @DeleteMapping("/{imageId}")
    public ResponseEntity<Void> delete(@PathVariable Long destinationId, @PathVariable Long imageId) {
        imageUploadService.delete(destinationId, imageId);
        return ResponseEntity.noContent().build();
    }

    public record ImageResponse(Long id, String thumbnailUrl, String mediumUrl, String fullUrl) {}
}
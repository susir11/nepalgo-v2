package com.nepalgo.catalog.image;

import com.nepalgo.catalog.destination.DestinationImage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/destinations/{destinationId}/images")
@RequiredArgsConstructor
public class ImageAdminController {

    private final ImageUploadService imageUploadService;
    private final ImageUrls imageUrls;

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<ImageResponse> upload(@PathVariable Long destinationId, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(view(imageUploadService.upload(destinationId, file)));
    }

    /** Add a photo by pasting its web address; the server downloads and re-encodes it. */
    @PostMapping(path = "/from-url", consumes = "application/json")
    public ResponseEntity<ImageResponse> fromUrl(@PathVariable Long destinationId, @Valid @RequestBody FromUrlRequest req) {
        return ResponseEntity.ok(view(imageUploadService.importFromUrl(destinationId, req.url())));
    }

    @DeleteMapping("/{imageId}")
    public ResponseEntity<Void> delete(@PathVariable Long destinationId, @PathVariable Long imageId) {
        imageUploadService.delete(destinationId, imageId);
        return ResponseEntity.noContent().build();
    }

    private ImageResponse view(DestinationImage i) {
        return new ImageResponse(i.getId(), imageUrls.of(i.getThumbnailKey()), imageUrls.of(i.getMediumKey()), imageUrls.of(i.getFullKey()));
    }

    public record FromUrlRequest(@NotBlank @Size(max = 2000) String url) {}

    public record ImageResponse(Long id, String thumbnailUrl, String mediumUrl, String fullUrl) {}
}

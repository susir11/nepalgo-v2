package com.nepalgo.catalog.image;

import com.nepalgo.catalog.destination.Destination;
import com.nepalgo.catalog.destination.DestinationImage;
import com.nepalgo.catalog.destination.DestinationImageRepository;
import com.nepalgo.catalog.destination.DestinationNotFoundException;
import com.nepalgo.catalog.destination.DestinationRepository;
import lombok.RequiredArgsConstructor;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ImageUploadService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png");

    private final DestinationRepository destinationRepository;
    private final DestinationImageRepository imageRepository;
    private final S3Client s3Client;

    @Value("${nepalgo.storage.bucket}")
    private String bucket;

    @Value("${nepalgo.storage.public-url-base}")
    private String publicUrlBase;

    @Transactional
    public DestinationImage upload(Long destinationId, MultipartFile file) {
        Destination destination = destinationRepository.findById(destinationId)
                .orElseThrow(() -> new DestinationNotFoundException(destinationId));

        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new InvalidImageException("Only JPEG and PNG images are accepted");
        }

        try {
            String baseKey = "destinations/" + destinationId + "/" + UUID.randomUUID();

            // 300/800/1600px width, aspect ratio preserved automatically.
            // useExifOrientation(true) reads the EXIF orientation flag
            // (phones often save landscape photos "sideways" with a flag
            // saying how to rotate them for display) and physically rotates
            // pixels to match BEFORE the file is re-encoded — otherwise a
            // portrait photo could come out sideways once EXIF is gone.
            // The re-encode itself is what strips EXIF (D5): Thumbnailator
            // writes a fresh JPEG/PNG with no metadata section at all,
            // which also means no GPS coordinates survive from a phone photo.
            String thumbnailUrl = resizeAndUpload(file, baseKey + "-thumb.jpg", 300);
            String mediumUrl = resizeAndUpload(file, baseKey + "-medium.jpg", 800);
            String fullUrl = resizeAndUpload(file, baseKey + "-full.jpg", 1600);

            DestinationImage image = new DestinationImage();
            image.setDestination(destination);
            image.setDisplayOrder(imageRepository.findByDestinationIdOrderByDisplayOrder(destinationId).size());
            image.setThumbnailUrl(thumbnailUrl);
            image.setMediumUrl(mediumUrl);
            image.setFullUrl(fullUrl);
            return imageRepository.save(image);

        } catch (Exception e) {
            throw new ImageProcessingException("Failed to process image", e);
        }
    }

    @Transactional
    public void delete(Long destinationId, Long imageId) {
        imageRepository.deleteById(imageId); // S3 objects are left orphaned for v1 — acceptable for a dev project
    }

    private String resizeAndUpload(MultipartFile file, String key, int widthPx) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Thumbnails.of(file.getInputStream())
                .useExifOrientation(true)
                .width(widthPx)
                .outputFormat("jpg")
                .toOutputStream(output);

        byte[] bytes = output.toByteArray();
        s3Client.putObject(
                PutObjectRequest.builder().bucket(bucket).key(key).contentType("image/jpeg").build(),
                RequestBody.fromInputStream(new ByteArrayInputStream(bytes), bytes.length));

        return publicUrlBase + "/" + key;
    }
}
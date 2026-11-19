package com.nepalgo.catalog.image;

import com.nepalgo.catalog.destination.Destination;
import com.nepalgo.catalog.destination.DestinationImage;
import com.nepalgo.catalog.destination.DestinationImageRepository;
import com.nepalgo.catalog.destination.DestinationNotFoundException;
import com.nepalgo.catalog.destination.DestinationRepository;
import lombok.RequiredArgsConstructor;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

/**
 * Image pipeline (spec 17) for destination photos, from an upload OR a URL.
 * Both paths end in the same place: magic-byte check -> re-encode to 3 JPEG sizes
 * (strips EXIF/GPS) -> store under our own keys. The original file is never kept.
 */
@Service
@RequiredArgsConstructor
public class ImageUploadService {

    private static final Logger log = LoggerFactory.getLogger(ImageUploadService.class);
    static final int MAX_IMAGES_PER_DESTINATION = 12;

    private final DestinationRepository destinationRepository;
    private final DestinationImageRepository imageRepository;
    private final S3Client s3Client;
    private final SafeImageFetcher fetcher;

    @Value("${nepalgo.storage.bucket}")
    private String bucket;

    @Transactional
    public DestinationImage upload(Long destinationId, MultipartFile file) {
        try {
            return store(destinationId, file.getBytes());
        } catch (java.io.IOException e) {
            throw new InvalidImageException("Could not read the uploaded file.");
        }
    }

    /** "Add photo by URL": download (SSRF-guarded), then exactly the upload pipeline. */
    @Transactional
    public DestinationImage importFromUrl(Long destinationId, String url) {
        requireDestination(destinationId);   // fail fast before any network call
        return store(destinationId, fetcher.fetch(url));
    }

    private DestinationImage store(Long destinationId, byte[] original) {
        Destination destination = requireDestination(destinationId);
        // Trust the bytes, not the declared Content-Type or file name (same rule as payment receipts).
        if (!isJpegOrPng(original)) {
            throw new InvalidImageException("Only JPEG and PNG images are accepted.");
        }
        int count = imageRepository.findByDestinationIdOrderByDisplayOrder(destinationId).size();
        if (count >= MAX_IMAGES_PER_DESTINATION) {
            throw new InvalidImageException("A destination can have at most " + MAX_IMAGES_PER_DESTINATION + " photos.");
        }
        String baseKey = "destinations/" + destinationId + "/" + UUID.randomUUID();
        try {
            // 300/800/1600 px wide, aspect ratio kept. useExifOrientation rotates pixels per
            // the phone's EXIF flag BEFORE re-encoding; the re-encode drops all metadata (incl. GPS).
            DestinationImage image = new DestinationImage();
            image.setDestination(destination);
            image.setDisplayOrder(count);
            image.setThumbnailKey(resizeAndPut(original, baseKey + "-thumb.jpg", 300));
            image.setMediumKey(resizeAndPut(original, baseKey + "-medium.jpg", 800));
            image.setFullKey(resizeAndPut(original, baseKey + "-full.jpg", 1600));
            return imageRepository.save(image);
        } catch (InvalidImageException e) {
            throw e;
        } catch (Exception e) {
            throw new ImageProcessingException("Failed to process image", e);
        }
    }

    @Transactional
    public void delete(Long destinationId, Long imageId) {
        DestinationImage img = imageRepository.findById(imageId)
                .filter(i -> i.getDestination().getId().equals(destinationId))   // id must belong to THIS destination
                .orElseThrow(() -> new InvalidImageException("Photo not found for this destination."));
        imageRepository.delete(img);
        // Keep display_order dense (0..n-1) so the first photo is always the cover.
        var rest = imageRepository.findByDestinationIdOrderByDisplayOrder(destinationId);
        for (int i = 0; i < rest.size(); i++) rest.get(i).setDisplayOrder(i);
        for (String key : new String[]{img.getThumbnailKey(), img.getMediumKey(), img.getFullKey()}) {
            try {
                s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
            } catch (Exception e) {
                log.warn("Could not delete image object {} (row already removed): {}", key, e.getMessage());
            }
        }
    }

    private Destination requireDestination(Long id) {
        return destinationRepository.findById(id).orElseThrow(() -> new DestinationNotFoundException(id));
    }

    static boolean isJpegOrPng(byte[] b) {
        if (b == null || b.length < 8) return false;
        boolean jpeg = (b[0] & 0xff) == 0xFF && (b[1] & 0xff) == 0xD8 && (b[2] & 0xff) == 0xFF;
        boolean png = (b[0] & 0xff) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A;
        return jpeg || png;
    }

    private String resizeAndPut(byte[] original, String key, int widthPx) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Thumbnails.of(new ByteArrayInputStream(original))
                .useExifOrientation(true)
                .width(widthPx)
                .outputFormat("jpg")
                .toOutputStream(output);
        byte[] bytes = output.toByteArray();
        s3Client.putObject(
                PutObjectRequest.builder().bucket(bucket).key(key).contentType("image/jpeg")
                        .cacheControl("public, max-age=31536000, immutable")   // keys are unique per upload
                        .build(),
                RequestBody.fromBytes(bytes));
        return key;
    }
}

package com.nepalgo.booking.booking;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

// Dormant by design (per spec): accepts a file, confirms receipt, does
// nothing further. Real storage/processing arrives whenever a future
// phase actually needs document uploads (passport scans, etc.) — this
// endpoint exists now purely so the API surface/contract is stable for
// a frontend to build against early.
@RestController
@RequestMapping("/api/bookings/{id}/documents")
public class DocumentUploadController {

    private final FeatureFlagController flags;

    public DocumentUploadController(FeatureFlagController flags) {
        this.flags = flags;
    }

    @PostMapping
    public ResponseEntity<DocumentUploadAck> upload(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        // Spec 20: document_upload_enabled is OFF -> the endpoint must not be
        // reachable at all (404, as if it didn't exist), not just hidden in the app.
        if (!flags.documentUploadEnabled()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new DocumentUploadAck(file.getOriginalFilename(), file.getSize(), "received_dormant"));
    }

    public record DocumentUploadAck(String filename, long sizeBytes, String status) {}
}
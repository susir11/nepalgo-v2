package com.nepalgo.booking.booking;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Read-only feature-flag viewer data (spec 20) for the flags booking-service owns.
// The admin portal merges every service's list into one registry screen.
@RestController
public class FeatureFlagController {

    public record Flag(String key, boolean enabled, String owner, String description) {}

    @Value("${nepalgo.flags.document-upload-enabled:false}")
    private boolean documentUploadEnabled;

    @GetMapping("/api/admin/feature-flags/booking")
    public List<Flag> flags() {
        return List.of(new Flag("document_upload_enabled", documentUploadEnabled, "booking-service",
                "Lets customers upload passport/visa photos for admin verification, replacing the typed "
                        + "self-declaration. Fully built; not reachable while off."));
    }

    public boolean documentUploadEnabled() {
        return documentUploadEnabled;
    }
}

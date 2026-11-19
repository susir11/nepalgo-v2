package com.nepalgo.catalog.image;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Turns a stored object key into the URL clients load.
 *
 * public-url-base is config, not data:
 *  - local dev:  "/media/destination-images" (RELATIVE). The gateway proxies /media/**
 *    to the image store, so the phone/emulator loads images from the same host it
 *    already uses for the API (10.0.2.2:8080), and the admin portal from its own origin.
 *  - production: an absolute CDN URL, e.g. "https://cdn.example.com/destination-images".
 * Clients resolve a relative URL against their API base (standard URL resolution).
 */
@Component
public class ImageUrls {

    private final String base;

    public ImageUrls(@Value("${nepalgo.storage.public-url-base}") String base) {
        this.base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    public String of(String key) {
        return key == null ? null : base + "/" + key;
    }
}

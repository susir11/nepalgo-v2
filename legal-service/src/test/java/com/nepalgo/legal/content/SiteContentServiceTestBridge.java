package com.nepalgo.legal.content;

import tools.jackson.databind.JsonNode;

// Same package as SiteContentService, so tests can reach its package-private validate().
public final class SiteContentServiceTestBridge {
    private SiteContentServiceTestBridge() {}

    public static void validate(SiteContent.Key key, JsonNode payload) {
        SiteContentService.validate(key, payload);
    }
}

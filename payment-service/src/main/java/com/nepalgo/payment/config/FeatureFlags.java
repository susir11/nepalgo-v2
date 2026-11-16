package com.nepalgo.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Binds nepalgo.flags.* from feature-flags.yml. A record = immutable, so
// nothing at runtime can flip a flag by accident. Missing flag => false.
@ConfigurationProperties(prefix = "nepalgo.flags")
public record FeatureFlags(boolean makerCheckerSegregationEnforced) {}
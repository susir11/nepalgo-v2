package com.nepalgo.payment.config;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Read-only viewer data for the flags payment-service owns (spec 20).
@RestController
@RequiredArgsConstructor
public class FeatureFlagController {

    private final FeatureFlags flags;

    public record Flag(String key, boolean enabled, String owner, String description) {}

    @GetMapping("/api/admin/feature-flags/payment")
    public List<Flag> flags() {
        return List.of(new Flag("maker_checker_segregation_enforced", flags.makerCheckerSegregationEnforced(),
                "payment-service", "When on, a different admin must approve an FX-rate change than the one who "
                + "proposed it. While off, the same admin may fill both roles."));
    }
}

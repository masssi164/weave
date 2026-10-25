package com.massimotter.weave.backend.health;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** A domain's configured status and deferred, support-safe provider health probe. */
public record ProviderHealthContribution(
        String domain,
        BooleanSupplier configured,
        Supplier<ProviderCapabilityProbeResult> probe) {
    public ProviderHealthContribution {
        if (domain == null || !domain.matches("[a-z][a-z0-9-]*")) {
            throw new IllegalArgumentException("health contribution requires a stable domain identity");
        }
        Objects.requireNonNull(configured, "configured");
        Objects.requireNonNull(probe, "probe");
    }
}

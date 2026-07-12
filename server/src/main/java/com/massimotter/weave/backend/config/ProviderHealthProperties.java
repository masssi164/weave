package com.massimotter.weave.backend.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "weave.provider.health")
public record ProviderHealthProperties(
        Duration minimumInterval,
        Duration jitter,
        Duration staleAfter,
        Duration maximumBackoff) {

    private static final Duration CONTRACT_MINIMUM_INTERVAL = Duration.ofSeconds(60);

    public ProviderHealthProperties {
        minimumInterval = atLeast(defaultIfInvalid(minimumInterval, CONTRACT_MINIMUM_INTERVAL), CONTRACT_MINIMUM_INTERVAL);
        jitter = nonNegative(jitter, Duration.ofSeconds(15));
        staleAfter = defaultIfInvalid(staleAfter, Duration.ofMinutes(5));
        maximumBackoff = atLeast(defaultIfInvalid(maximumBackoff, Duration.ofMinutes(15)), minimumInterval);
    }

    private static Duration defaultIfInvalid(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }

    private static Duration nonNegative(Duration value, Duration fallback) {
        return value == null || value.isNegative() ? fallback : value;
    }

    private static Duration atLeast(Duration value, Duration minimum) {
        return value.compareTo(minimum) < 0 ? minimum : value;
    }
}

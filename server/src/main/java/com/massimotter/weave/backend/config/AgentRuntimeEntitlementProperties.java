package com.massimotter.weave.backend.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Keycloak Organization-backed ARC entitlement policy; it contains no runtime configuration. */
@ConfigurationProperties(prefix = "weave.agent-runtime.entitlement")
public record AgentRuntimeEntitlementProperties(
        boolean enabled,
        Duration observationTtl,
        List<String> allowedCapabilities) {

    public AgentRuntimeEntitlementProperties {
        observationTtl = observationTtl == null ? Duration.ofMinutes(5) : observationTtl;
        if (observationTtl.compareTo(Duration.ofSeconds(30)) < 0
                || observationTtl.compareTo(Duration.ofMinutes(15)) > 0) {
            throw new IllegalArgumentException(
                    "observationTtl must be between 30 seconds and 15 minutes");
        }
        allowedCapabilities = normalized(allowedCapabilities, List.of("files.read"));
    }

    public static AgentRuntimeEntitlementProperties disabled() {
        return new AgentRuntimeEntitlementProperties(false, null, null);
    }

    private static List<String> normalized(List<String> values, List<String> defaults) {
        if (values == null || values.isEmpty()) {
            return defaults;
        }
        List<String> result = values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .sorted()
                .toList();
        return result.isEmpty() ? defaults : result;
    }
}

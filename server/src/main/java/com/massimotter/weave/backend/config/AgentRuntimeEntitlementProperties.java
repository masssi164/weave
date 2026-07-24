package com.massimotter.weave.backend.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Keycloak-backed ARC entitlement input; it contains no runtime/container configuration. */
@ConfigurationProperties(prefix = "weave.agent-runtime.entitlement")
public record AgentRuntimeEntitlementProperties(
        boolean enabled,
        List<String> enabledGroups,
        List<String> allowedCapabilities) {

    public AgentRuntimeEntitlementProperties {
        enabledGroups = normalized(enabledGroups, List.of("/weave/weaver-runtime"));
        allowedCapabilities = normalized(allowedCapabilities, List.of("calendar.read"));
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

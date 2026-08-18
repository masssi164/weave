package com.massimotter.weave.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "weave.platform")
public record PlatformContractProperties(
        String publicBaseUrl,
        String apiBaseUrl,
        String authBaseUrl,
        String matrixHomeserverUrl,
        String filesProductUrl,
        String calendarProductUrl,
        String nextcloudBaseUrl,
        Targets targets) {

    public PlatformContractProperties {
        publicBaseUrl = defaultIfBlank(publicBaseUrl, "https://weave.test");
        apiBaseUrl = defaultIfBlank(apiBaseUrl, "https://api.weave.test/api");
        authBaseUrl = defaultIfBlank(authBaseUrl, "https://auth.weave.test");
        matrixHomeserverUrl = defaultIfBlank(matrixHomeserverUrl, matrixProjectionBaseUrl(apiBaseUrl));
        filesProductUrl = defaultIfBlank(filesProductUrl, "https://weave.test/files");
        calendarProductUrl = defaultIfBlank(calendarProductUrl, "https://weave.test/calendar");
        nextcloudBaseUrl = defaultIfBlank(nextcloudBaseUrl, "https://files.weave.test");
        targets = targets == null ? new Targets(true, true, false) : targets;
    }

    private static String defaultIfBlank(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private static String matrixProjectionBaseUrl(String apiBaseUrl) {
        String normalized = defaultIfBlank(apiBaseUrl, "https://api.weave.test/api");
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.endsWith("/api")
                ? normalized.substring(0, normalized.length() - "/api".length())
                : normalized;
    }

    public String agentRuntimeControlResource() {
        String normalized = apiBaseUrl;
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized + "/v1/agent-runtime";
    }

    public record Targets(boolean mobile, boolean desktop, boolean web) {
    }
}

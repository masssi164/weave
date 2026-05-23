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
        publicBaseUrl = defaultIfBlank(publicBaseUrl, "https://weave.local");
        apiBaseUrl = defaultIfBlank(apiBaseUrl, "https://api.weave.local/api");
        authBaseUrl = defaultIfBlank(authBaseUrl, "https://auth.weave.local");
        matrixHomeserverUrl = defaultIfBlank(matrixHomeserverUrl, "https://matrix.weave.local");
        filesProductUrl = defaultIfBlank(filesProductUrl, "https://weave.local/files");
        calendarProductUrl = defaultIfBlank(calendarProductUrl, "https://weave.local/calendar");
        nextcloudBaseUrl = defaultIfBlank(nextcloudBaseUrl, "https://files.weave.local");
        targets = targets == null ? new Targets(true, true, false) : targets;
    }

    private static String defaultIfBlank(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    public record Targets(boolean mobile, boolean desktop, boolean web) {
    }
}

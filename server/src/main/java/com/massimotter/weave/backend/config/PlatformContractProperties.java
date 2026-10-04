package com.massimotter.weave.backend.config;

import java.net.URI;
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
        matrixHomeserverUrl = matrixFacadeOrigin(defaultIfBlank(matrixHomeserverUrl, apiOrigin(apiBaseUrl)));
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

    private static String apiOrigin(String apiBaseUrl) {
        URI api = URI.create(apiBaseUrl);
        if (!"https".equalsIgnoreCase(api.getScheme()) || api.getHost() == null || api.getRawAuthority() == null
                || api.getRawUserInfo() != null || api.getRawQuery() != null || api.getRawFragment() != null) {
            throw new IllegalArgumentException("The Weave User API base must have a DNS-hosted HTTPS origin");
        }
        return "https://" + api.getRawAuthority();
    }

    private static String matrixFacadeOrigin(String value) {
        URI facade = URI.create(value);
        if (!"https".equalsIgnoreCase(facade.getScheme()) || facade.getHost() == null
                || facade.getRawAuthority() == null || facade.getRawUserInfo() != null
                || facade.getRawQuery() != null || facade.getRawFragment() != null
                || (facade.getRawPath() != null && !facade.getRawPath().isEmpty()
                && !"/".equals(facade.getRawPath()))) {
            throw new IllegalArgumentException("The advertised Weave Matrix facade must be a credential-free HTTPS origin");
        }
        return "https://" + facade.getRawAuthority();
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

package com.massimotter.weave.backend.meetings.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "weave.calls.sfu.livekit")
public record LiveKitSfuProviderProperties(
        boolean enabled,
        String url,
        String apiKey,
        String apiSecret,
        String tokenEndpoint) {

    public LiveKitSfuProviderProperties {
        url = clean(url);
        apiKey = clean(apiKey);
        apiSecret = clean(apiSecret);
        tokenEndpoint = clean(tokenEndpoint);
    }

    public boolean urlConfigured() {
        return hasText(url);
    }

    public boolean apiKeyConfigured() {
        return hasText(apiKey);
    }

    public boolean apiSecretConfigured() {
        return hasText(apiSecret);
    }

    public boolean tokenEndpointConfigured() {
        return hasText(tokenEndpoint);
    }

    public boolean directCredentialModeConfigured() {
        return urlConfigured() && apiKeyConfigured() && apiSecretConfigured();
    }

    public boolean tokenEndpointModeConfigured() {
        return urlConfigured() && tokenEndpointConfigured();
    }

    public boolean configured() {
        return directCredentialModeConfigured() || tokenEndpointModeConfigured();
    }

    @Override
    public String toString() {
        return "LiveKitSfuProviderProperties[enabled=" + enabled
                + ", urlConfigured=" + urlConfigured()
                + ", apiKey=[REDACTED], apiSecret=[REDACTED], tokenEndpointConfigured="
                + tokenEndpointConfigured() + "]";
    }

    private static String clean(String value) {
        return hasText(value) ? value.trim() : "";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

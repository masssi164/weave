package com.massimotter.weave.backend.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "weave.weaver.pa-chat")
public record WeaverPaChatProperties(
        boolean enabled,
        String bridgeUrl,
        Duration timeout,
        String runtimeTokenRef) {

    public WeaverPaChatProperties {
        bridgeUrl = hasText(bridgeUrl) ? bridgeUrl.trim() : "";
        timeout = timeout == null ? Duration.ofSeconds(120) : timeout;
        runtimeTokenRef = hasText(runtimeTokenRef) ? runtimeTokenRef.trim() : "credentialref://weave/channels/weave-chat/runtime-token";
    }

    public boolean bridgeConfigured() {
        return enabled && hasText(bridgeUrl);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

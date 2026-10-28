package com.massimotter.weave.backend.chat.config;

import com.massimotter.weave.backend.chat.config.ChatRuntimeProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatRuntimePropertiesTest {

    @Test
    void runtimePropertiesDefaultToNativeAndRejectUnknownProviders() {
        ChatRuntimeProperties defaults = new ChatRuntimeProperties(null, null);

        assertThat(defaults.provider()).isEqualTo(ChatRuntimeProperties.WEAVE_NATIVE_PROVIDER);
        assertThat(defaults.weaveNativeSelected()).isTrue();
        assertThatThrownBy(() -> new ChatRuntimeProperties("unknown-provider", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported Chat provider");
    }

    @Test
    void readinessCacheCannotBeConfiguredBelowTheProviderProtectionFloor() {
        ChatRuntimeProperties.Matrix properties = new ChatRuntimeProperties.Matrix(
                "http://synapse:8008",
                "matrix.weave.test",
                "weave-chat-synapse",
                "_weave_",
                "/run/secrets/as-token",
                "/run/secrets/hs-token",
                Duration.ofSeconds(5),
                Duration.ofSeconds(10),
                Duration.ofSeconds(1),
                1_048_576,
                100);

        assertThat(properties.readinessCacheTtl()).isEqualTo(Duration.ofSeconds(60));
    }
}

package com.massimotter.weave.backend.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatRuntimeContractTest {

    @Test
    void applicationConfigurationUsesOnlyTheStableSynapseApplicationServiceContract() throws IOException {
        Path resource = Files.isRegularFile(Path.of("src/main/resources/application.yml"))
                ? Path.of("src/main/resources/application.yml")
                : Path.of("server/src/main/resources/application.yml");
        String configuration = Files.readString(resource);

        assertThat(configuration)
                .contains("${WEAVE_CHAT_PROVIDER:matrix-synapse}")
                .contains("${WEAVE_CHAT_MATRIX_INTERNAL_BASE_URL:}")
                .contains("${WEAVE_CHAT_MATRIX_SERVER_NAME:}")
                .contains("${WEAVE_CHAT_MATRIX_APPSERVICE_ID:weave-chat-synapse}")
                .contains("${WEAVE_CHAT_MATRIX_VIRTUAL_USER_PREFIX:_weave_}")
                .contains("${WEAVE_CHAT_MATRIX_APPSERVICE_AS_TOKEN_FILE:}")
                .contains("${WEAVE_CHAT_MATRIX_APPSERVICE_HS_TOKEN_FILE:}")
                .doesNotContain("WEAVE_CHAT_STORAGE_MODE")
                .doesNotContain("WEAVE_CHAT_MATRIX_AS_TOKEN:")
                .doesNotContain("WEAVE_CHAT_MATRIX_HS_TOKEN:");
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

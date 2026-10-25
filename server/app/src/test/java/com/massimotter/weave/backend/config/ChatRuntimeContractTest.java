package com.massimotter.weave.backend.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ChatRuntimeContractTest {
    @Test
    void applicationConfigurationDefaultsToNativeAndKeepsMatrixSecretsOptional() throws IOException {
        Path resource = Files.isRegularFile(Path.of("src/main/resources/application-base.yml"))
                ? Path.of("src/main/resources/application-base.yml")
                : Path.of("server/app/src/main/resources/application-base.yml");
        String configuration = Files.readString(resource);

        assertThat(configuration)
                .contains("${WEAVE_CHAT_PROVIDER:weave-native}")
                .doesNotContain("WEAVE_CHAT_STORAGE_MODE")
                .contains("${WEAVE_CHAT_MATRIX_INTERNAL_BASE_URL:}")
                .contains("${WEAVE_CHAT_MATRIX_SERVER_NAME:}")
                .contains("${WEAVE_CHAT_MATRIX_APPSERVICE_ID:weave-chat-synapse}")
                .contains("${WEAVE_CHAT_MATRIX_VIRTUAL_USER_PREFIX:_weave_}")
                .contains("${WEAVE_CHAT_MATRIX_APPSERVICE_AS_TOKEN_FILE:}")
                .contains("${WEAVE_CHAT_MATRIX_APPSERVICE_HS_TOKEN_FILE:}")
                .doesNotContain("WEAVE_CHAT_MATRIX_AS_TOKEN:")
                .doesNotContain("WEAVE_CHAT_MATRIX_HS_TOKEN:");
    }

}

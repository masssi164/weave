package com.massimotter.weave.backend.chat.provider.synapse;

import com.massimotter.weave.backend.chat.config.ChatRuntimeProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class MatrixApplicationServiceSecretsTest {
    @TempDir Path directory;

    @Test
    void graphConstructionNeedsNoSecretsButReadinessRejectsMissingMountsAndCachesValidTokens() throws Exception {
        var properties = properties();
        var secrets = new MatrixApplicationServiceSecrets(properties);
        assertThatCode(() -> new SynapseApplicationServiceClient(properties, secrets,
                JsonMapper.builder().build(), Clock.systemUTC())).doesNotThrowAnyException();
        assertThatThrownBy(secrets::requireReady).isInstanceOf(IllegalStateException.class);
        Files.writeString(directory.resolve("as"), "as-token-value-0123456789");
        Files.writeString(directory.resolve("hs"), "hs-token-value-0123456789");
        secrets.requireReady();
        Files.delete(directory.resolve("as"));
        Files.delete(directory.resolve("hs"));
        assertThat(secrets.asBearerValue()).isEqualTo("as-token-value-0123456789");
        assertThat(secrets.matchesHomeserverToken("hs-token-value-0123456789".getBytes(StandardCharsets.UTF_8))).isTrue();
        assertThat(secrets.matchesHomeserverToken("as-token-value-0123456789".getBytes(StandardCharsets.UTF_8))).isFalse();
    }

    @Test
    void equalAndInvalidTokensStillFailClosedAtReadiness() throws Exception {
        Files.writeString(directory.resolve("as"), "same-token-value-0123456789");
        Files.writeString(directory.resolve("hs"), "same-token-value-0123456789");
        assertThatThrownBy(new MatrixApplicationServiceSecrets(properties())::requireReady)
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("must be distinct");
        Files.writeString(directory.resolve("hs"), "short");
        assertThatThrownBy(new MatrixApplicationServiceSecrets(properties())::requireReady)
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("invalid size");
    }

    private ChatRuntimeProperties.Matrix properties() {
        return new ChatRuntimeProperties.Matrix("http://127.0.0.1:1", "matrix.example.invalid",
                null, null, directory.resolve("as").toString(), directory.resolve("hs").toString(),
                null, null, null, 0, 0);
    }
}

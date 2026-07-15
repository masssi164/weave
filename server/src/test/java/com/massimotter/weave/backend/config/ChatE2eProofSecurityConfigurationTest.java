package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.chat.provider.synapse.MatrixApplicationServiceSecrets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatE2eProofSecurityConfigurationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void proofTokenMustDifferFromApplicationServiceToken() throws IOException {
        assertTokenConflict("proof-token-that-is-distinct", "proof-token-that-is-distinct", "homeserver-token-distinct");
    }

    @Test
    void proofTokenMustDifferFromHomeserverToken() throws IOException {
        assertTokenConflict("proof-token-that-is-distinct", "application-service-distinct", "proof-token-that-is-distinct");
    }

    private void assertTokenConflict(String proof, String applicationService, String homeserver) throws IOException {
        Path proofFile = tokenFile("proof", proof);
        Path asFile = tokenFile("as", applicationService);
        Path hsFile = tokenFile("hs", homeserver);
        ChatE2eProofProperties proofProperties = new ChatE2eProofProperties(
                true, proofFile.toString(), "isolated-run-1234", "isolated");
        MatrixApplicationServiceSecrets applicationServiceSecrets = new MatrixApplicationServiceSecrets(
                new ChatRuntimeProperties.Matrix(
                        "http://matrix.internal:8008",
                        "matrix.internal",
                        "weave-chat-synapse",
                        "_weave_",
                        asFile.toString(),
                        hsFile.toString(),
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(10),
                        Duration.ofSeconds(60),
                        65_536,
                        100));

        assertThatThrownBy(() -> new ChatE2eProofSecurityConfiguration()
                        .chatE2eProofSecrets(proofProperties, applicationServiceSecrets))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be distinct");
    }

    private Path tokenFile(String name, String value) throws IOException {
        Path path = temporaryDirectory.resolve(name + ".token");
        Files.writeString(path, value);
        return path;
    }
}

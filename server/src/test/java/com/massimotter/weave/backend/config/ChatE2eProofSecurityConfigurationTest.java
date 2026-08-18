package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.chat.provider.synapse.MatrixApplicationServiceSecrets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatE2eProofSecurityConfigurationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void proofAuthenticationFilterIsOwnedOnlyByItsSecurityChain() throws IOException {
        ChatE2eProofSecurityConfiguration configuration = new ChatE2eProofSecurityConfiguration();
        ChatE2eProofProperties proofProperties = new ChatE2eProofProperties(
                true,
                tokenFile("proof-registration", "proof-registration-token").toString(),
                "isolated-run-1234",
                "isolated");
        MatrixApplicationServiceSecrets applicationServiceSecrets = new MatrixApplicationServiceSecrets(
                new ChatRuntimeProperties.Matrix(
                        "http://matrix.internal:8008",
                        "matrix.internal",
                        "weave-chat-synapse",
                        "_weave_",
                        tokenFile("as-registration", "application-service-registration-token").toString(),
                        tokenFile("hs-registration", "homeserver-registration-token").toString(),
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(10),
                        Duration.ofSeconds(60),
                        65_536,
                        100));
        var secrets = configuration.chatE2eProofSecrets(
                proofProperties, Optional.of(applicationServiceSecrets));
        var filter = configuration.chatE2eProofAuthenticationFilter(secrets);
        var registration = configuration.chatE2eProofAuthenticationFilterRegistration(filter);

        assertThat(registration.isEnabled()).isFalse();
        assertThat(registration.getFilter()).isSameAs(filter);
    }

    @Test
    void proofTokenMustDifferFromApplicationServiceToken() throws IOException {
        assertTokenConflict("proof-token-that-is-distinct", "proof-token-that-is-distinct", "homeserver-token-distinct");
    }

    @Test
    void proofTokenMustDifferFromHomeserverToken() throws IOException {
        assertTokenConflict("proof-token-that-is-distinct", "application-service-distinct", "proof-token-that-is-distinct");
    }

    @Test
    void nativeChatProofDoesNotRequireMatrixApplicationServiceAuthority() throws IOException {
        ChatE2eProofProperties proofProperties = new ChatE2eProofProperties(
                true,
                tokenFile("native-proof", "native-proof-token-value-1234").toString(),
                "isolated-run-1234",
                "isolated");

        assertThat(new ChatE2eProofSecurityConfiguration()
                        .chatE2eProofSecrets(proofProperties, Optional.empty()))
                .isNotNull();
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
                        .chatE2eProofSecrets(
                                proofProperties, Optional.of(applicationServiceSecrets)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be distinct");
    }

    private Path tokenFile(String name, String value) throws IOException {
        Path path = temporaryDirectory.resolve(name + ".token");
        Files.writeString(path, value);
        return path;
    }
}

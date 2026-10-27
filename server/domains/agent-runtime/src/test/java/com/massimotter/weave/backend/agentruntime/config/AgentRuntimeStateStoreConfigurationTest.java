package com.massimotter.weave.backend.agentruntime.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentRuntimeStateStoreConfigurationTest {

    @TempDir
    Path temporary;

    @Test
    void credentialConstructionIsOfflineAndFirstResolutionCachesOnlyValidatedMountedValues() throws Exception {
        Path access = temporary.resolve("late-access");
        Path secret = temporary.resolve("late-secret");
        var properties = new AgentRuntimeStateStoreProperties();
        properties.setAccessKeyFile(access);
        properties.setSecretKeyFile(secret);
        var credentials = new RuntimeStateCredentials(properties);
        assertThat(Files.exists(access)).isFalse();
        assertThatThrownBy(credentials::resolveCredentials).isInstanceOf(IllegalStateException.class);
        privateSecret("late-access", "mounted-access");
        privateSecret("late-secret", "mounted-secret");
        var resolved = credentials.resolveCredentials();
        assertThat(resolved.accessKeyId()).isEqualTo("mounted-access");
        assertThat(resolved.secretAccessKey()).isEqualTo("mounted-secret");
        Files.delete(access);
        Files.delete(secret);
        assertThat(credentials.resolveCredentials()).isSameAs(resolved);
        assertThat(credentials.toString()).doesNotContain("mounted-access", "mounted-secret");
    }

    @Test
    void mountedCredentialFilesMustBeAbsolutePrivateRegularFiles() throws Exception {
        Path access = privateSecret("access-key", "test-access");
        Path secret = privateSecret("secret-key", "test-secret");
        AgentRuntimeStateStoreProperties properties = new AgentRuntimeStateStoreProperties();
        properties.setCredentialRef("secretref:runtime-state/dogfood");
        properties.setAccessKeyFile(access);
        properties.setSecretKeyFile(secret);

        assertThat(properties.requiredCredentialRef()).isEqualTo("secretref:runtime-state/dogfood");
        assertThat(properties.readAccessKey()).isEqualTo("test-access");
        assertThat(properties.readSecretKey()).isEqualTo("test-secret");

        properties.setAccessKeyFile(Path.of("relative-secret"));
        assertThatThrownBy(properties::readAccessKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("absolute mounted SecretRef file")
                .hasMessageNotContaining("relative-secret")
                .hasMessageNotContaining("test-access");
    }

    @Test
    void mountedCredentialSymlinksAreRejectedWithoutDisclosingTheTarget() throws Exception {
        Path target = privateSecret("target-secret", "must-not-leak");
        Path link = temporary.resolve("credential-link");
        Files.createSymbolicLink(link, target);
        AgentRuntimeStateStoreProperties properties = new AgentRuntimeStateStoreProperties();
        properties.setAccessKeyFile(link.toAbsolutePath());

        assertThatThrownBy(properties::readAccessKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unavailable or unsafe")
                .hasMessageNotContaining("target-secret")
                .hasMessageNotContaining("must-not-leak")
                .hasMessageNotContaining(temporary.toString());
    }

    private Path privateSecret(String fileName, String value) throws Exception {
        Path secret = Files.writeString(temporary.resolve(fileName), value);
        try {
            Files.setPosixFilePermissions(secret, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX test hosts retain no-follow, absolute-path and size checks.
        }
        return secret.toAbsolutePath();
    }
}

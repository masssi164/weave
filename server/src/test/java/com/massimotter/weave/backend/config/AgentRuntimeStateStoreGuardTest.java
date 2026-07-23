package com.massimotter.weave.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.Environment;

class AgentRuntimeStateStoreGuardTest {

    @TempDir
    Path temporary;

    @Test
    void releaseProfilesCannotActivateTheCrossStoreAdapterBeforeReconciliationEvidence() {
        Environment dogfood = mock(Environment.class);
        when(dogfood.getActiveProfiles()).thenReturn(new String[] {"dogfood"});
        when(dogfood.getProperty("weave.deployment.profile", "")).thenReturn("dogfood");

        assertThatThrownBy(() -> AgentRuntimeStateStoreConfiguration.rejectReleaseActivation(dogfood))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("RuntimeStateStore remains Guarded until durable cross-store reconciliation evidence passes")
                .hasMessageNotContaining("S3")
                .hasMessageNotContaining("credential")
                .hasMessageNotContaining("bucket");
    }

    @Test
    void testProfileCanExerciseTheGuardedAdapterWithoutPromotingIt() {
        Environment test = mock(Environment.class);
        when(test.getActiveProfiles()).thenReturn(new String[] {"test"});
        when(test.getProperty("weave.deployment.profile", "")).thenReturn("test");

        AgentRuntimeStateStoreConfiguration.rejectReleaseActivation(test);
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

package com.massimotter.weave.backend.agentruntime.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.agentruntime.port.RuntimeStateKeyWrapper;
import com.massimotter.weave.backend.agentruntime.port.RuntimeStateStoreException;
import com.massimotter.weave.backend.agentruntime.port.RuntimeStateWrappingKeyLifecycle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileRuntimeStateKeyWrapperTest {
    private static final Instant NOW = Instant.parse("2026-07-20T12:00:00Z");

    @TempDir
    Path temporary;

    private FileRuntimeStateKeyWrapper keys;

    @BeforeEach
    void setUp() {
        keys = new FileRuntimeStateKeyWrapper(
                temporary.resolve("runtime-state-keys").toAbsolutePath(),
                tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                new SecureRandom(),
                FileSecretStoreAccess.READ_WRITE);
    }

    @Test
    void startupDoesNotCreateAMissingWrappingKey() {
        assertThat(keys.readiness().ready()).isFalse();
        assertThatThrownBy(keys::current)
                .isInstanceOf(RuntimeStateStoreException.class)
                .hasMessageContaining("not initialized");
    }

    @Test
    void initializationIsExplicitIdempotentAndContextBound() {
        RuntimeStateWrappingKeyLifecycle.KeyRingState initialized = keys.initialize("operator:init:001");
        RuntimeStateWrappingKeyLifecycle.KeyRingState repeated = keys.initialize("operator:init:001");
        byte[] dataKey = new byte[32];
        new SecureRandom().nextBytes(dataKey);
        byte[] context = "organization/person/cell/generation/profile".getBytes(StandardCharsets.UTF_8);

        RuntimeStateKeyWrapper.WrappedDataKey wrapped = keys.wrap(dataKey, context);

        assertThat(repeated).isEqualTo(initialized);
        assertThat(keys.readiness().ready()).isTrue();
        assertThat(keys.unwrap(wrapped, context)).isEqualTo(dataKey);
        assertThatThrownBy(() -> keys.unwrap(
                        wrapped, "another/person/cell/generation/profile".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(RuntimeStateStoreException.class)
                .hasMessageContaining("binding");
        assertThatThrownBy(() -> keys.initialize("operator:init:another"))
                .isInstanceOf(RuntimeStateStoreException.class)
                .hasMessageContaining("already initialized");
    }

    @Test
    void rotationChangesTheActiveKeyAndRetainsOverlapDecryption() {
        RuntimeStateWrappingKeyLifecycle.KeyRingState initialized = keys.initialize("operator:init:001");
        byte[] dataKey = new byte[32];
        new SecureRandom().nextBytes(dataKey);
        byte[] context = "organization/person/cell/generation/profile".getBytes(StandardCharsets.UTF_8);
        RuntimeStateKeyWrapper.WrappedDataKey before = keys.wrap(dataKey, context);

        RuntimeStateWrappingKeyLifecycle.KeyRingState rotated = keys.rotate("operator:rotate:001");
        RuntimeStateWrappingKeyLifecycle.KeyRingState replay = keys.rotate("operator:rotate:001");
        RuntimeStateKeyWrapper.WrappedDataKey after = keys.wrap(dataKey, context);

        assertThat(replay).isEqualTo(rotated);
        assertThat(rotated.activeKeyRef()).isNotEqualTo(initialized.activeKeyRef());
        assertThat(rotated.keys()).extracting(RuntimeStateWrappingKeyLifecycle.KeyState::status)
                .containsExactlyInAnyOrder(
                        RuntimeStateWrappingKeyLifecycle.Status.ACTIVE,
                        RuntimeStateWrappingKeyLifecycle.Status.OVERLAP);
        assertThat(before.keyRef()).isNotEqualTo(after.keyRef());
        assertThat(keys.unwrap(before, context)).isEqualTo(dataKey);
        assertThat(keys.unwrap(after, context)).isEqualTo(dataKey);
    }

    @Test
    void readOnlyRuntimeReadsWithoutChangingDirectoriesOrCreatingLocks() throws Exception {
        RuntimeStateWrappingKeyLifecycle.KeyRingState initialized = keys.initialize("operator:init:001");
        Path root = temporary.resolve("runtime-state-keys");
        Path keyDirectory = root.resolve("keys");
        if (!Files.getFileStore(root).supportsFileAttributeView("posix")) {
            return;
        }
        Path lock = root.resolve(".runtime-state-wrapping-keys.lock");
        Files.delete(lock);
        Files.setPosixFilePermissions(keyDirectory, PosixFilePermissions.fromString("r-x------"));
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("r-x------"));
        try {
            FileRuntimeStateKeyWrapper runtimeKeys = new FileRuntimeStateKeyWrapper(
                    root.toAbsolutePath(),
                    tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build(),
                    Clock.fixed(NOW, ZoneOffset.UTC),
                    new SecureRandom(),
                    FileSecretStoreAccess.READ_ONLY);

            assertThat(runtimeKeys.current()).isEqualTo(initialized);
            assertThat(lock).doesNotExist();
            assertThat(Files.getPosixFilePermissions(root))
                    .isEqualTo(PosixFilePermissions.fromString("r-x------"));
            assertThat(Files.getPosixFilePermissions(keyDirectory))
                    .isEqualTo(PosixFilePermissions.fromString("r-x------"));
            assertThatThrownBy(() -> runtimeKeys.rotate("operator:rotate:read-only"))
                    .isInstanceOf(RuntimeStateStoreException.class)
                    .hasMessageContaining("read-only");
        } finally {
            Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
            Files.setPosixFilePermissions(keyDirectory, PosixFilePermissions.fromString("rwx------"));
        }
    }
}

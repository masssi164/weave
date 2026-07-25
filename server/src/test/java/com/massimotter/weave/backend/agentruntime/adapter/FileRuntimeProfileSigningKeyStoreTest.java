package com.massimotter.weave.backend.agentruntime.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeMemberBinding;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeProfile;
import com.massimotter.weave.backend.agentruntime.domain.SignedRuntimeProfile;
import com.massimotter.weave.backend.agentruntime.port.InvalidRuntimeProfileException;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileSigningKeyException;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileSigningKeyLifecycle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileRuntimeProfileSigningKeyStoreTest {
    private static final Duration KEY_LIFETIME = Duration.ofDays(365);
    private static final Duration OVERLAP = Duration.ofMinutes(10);
    private static final Duration PROFILE_TTL = Duration.ofMinutes(5);

    @TempDir
    Path temporary;

    private ObjectMapper mapper;
    private MutableClock clock;
    private FileRuntimeProfileSigningKeyStore store;

    @BeforeEach
    void setUp() {
        mapper = tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build();
        clock = new MutableClock(Instant.parse("2026-07-20T10:00:00Z"));
        store = new FileRuntimeProfileSigningKeyStore(
                temporary,
                mapper,
                clock,
                new SecureRandom(),
                KEY_LIFETIME,
                OVERLAP,
                PROFILE_TTL);
    }

    @Test
    void initializationIsExplicitIdempotentAndPublishesNoPrivateMaterial() throws Exception {
        assertThat(store.publishedKeys(clock.instant())).isEmpty();
        assertThatThrownBy(store::activeKey)
                .isInstanceOf(RuntimeProfileSigningKeyException.class)
                .hasMessageContaining("not initialized");

        RuntimeProfileSigningKeyLifecycle.KeyRingState initialized =
                store.initialize("bootstrap:weave-local:2026-07-20");

        assertThat(store.initialize("bootstrap:weave-local:2026-07-20")).isEqualTo(initialized);
        assertThat(initialized.pendingKeyId()).isNull();
        assertThat(initialized.keys()).singleElement()
                .satisfies(key -> {
                    assertThat(key.status()).isEqualTo(RuntimeProfileSigningKeyLifecycle.Status.ACTIVE);
                    assertThat(key.privateMaterialPresent()).isTrue();
                });
        assertThat(store.publishedKeys(clock.instant())).singleElement()
                .extracting("keyId").isEqualTo(initialized.activeKeyId());

        String manifest = Files.readString(temporary.resolve("runtime-profile-signing-keys.json"));
        assertThat(manifest)
                .contains("publicKeyX509")
                .doesNotContain("privateKeyPkcs8")
                .doesNotContain("BEGIN PRIVATE KEY");
        Path privateKey = Files.list(temporary)
                .filter(path -> path.getFileName().toString().endsWith(".pk8"))
                .findFirst()
                .orElseThrow();
        if (Files.getFileStore(privateKey).supportsFileAttributeView("posix")) {
            assertThat(Files.getPosixFilePermissions(privateKey))
                    .isEqualTo(PosixFilePermissions.fromString("rw-------"));
        }

        assertThatThrownBy(() -> store.initialize("bootstrap:a-different-installation"))
                .isInstanceOf(RuntimeProfileSigningKeyException.class)
                .hasMessageContaining("already initialized");
    }

    @Test
    void rotationPublishesBeforeUseRetainsOldTrustAndDeletesOldPrivateMaterial() throws Exception {
        RuntimeProfileSigningKeyLifecycle.KeyRingState initial =
                store.initialize("bootstrap:weave-local:2026-07-20");
        Ed25519JcsRuntimeProfileSigner signer = new Ed25519JcsRuntimeProfileSigner(mapper, store);
        Ed25519JcsRuntimeProfileVerifier verifier = new Ed25519JcsRuntimeProfileVerifier(mapper, store);
        SignedRuntimeProfile signedByInitial = signer.sign(profile(clock.instant()));
        Path oldPrivate = temporary.resolve("key-" + initial.activeKeyId() + ".pk8");
        assertThat(oldPrivate).exists();

        RuntimeProfileSigningKeyLifecycle.KeyRingState prepared =
                store.prepareRotation("rotation:2026-q3");
        assertThat(store.prepareRotation("rotation:2026-q3")).isEqualTo(prepared);
        assertThat(prepared.activeKeyId()).isEqualTo(initial.activeKeyId());
        assertThat(prepared.pendingKeyId()).isNotNull();
        assertThat(store.publishedKeys(clock.instant())).hasSize(2);

        RuntimeProfileSigningKeyLifecycle.KeyRingState activated =
                store.activateRotation("rotation:2026-q3");
        assertThat(store.activateRotation("rotation:2026-q3")).isEqualTo(activated);
        assertThat(activated.activeKeyId()).isEqualTo(prepared.pendingKeyId());
        assertThat(activated.keys()).extracting(RuntimeProfileSigningKeyLifecycle.PublishedKeyState::status)
                .containsExactlyInAnyOrder(
                        RuntimeProfileSigningKeyLifecycle.Status.ACTIVE,
                        RuntimeProfileSigningKeyLifecycle.Status.PREVIOUS);
        assertThat(oldPrivate).doesNotExist();
        assertThat(store.publishedKeys(clock.instant())).hasSize(2);

        SignedRuntimeProfile signedByNext = signer.sign(profile(clock.instant()));
        assertThat(signedByNext.keyId()).isEqualTo(activated.activeKeyId());
        assertThat(signedByInitial.keyId()).isEqualTo(initial.activeKeyId());
        assertThat(verifier.verify(signedByInitial, clock.instant().plusSeconds(1)).profileId())
                .isEqualTo("rp_key_store_test");
        assertThat(verifier.verify(signedByNext, clock.instant().plusSeconds(1)).profileId())
                .isEqualTo("rp_key_store_test");

        assertThatThrownBy(() -> store.completeRetirement("rotation:2026-q3"))
                .isInstanceOf(RuntimeProfileSigningKeyException.class)
                .hasMessageContaining("overlap has not elapsed");

        clock.advance(OVERLAP.plusSeconds(1));
        RuntimeProfileSigningKeyLifecycle.KeyRingState retired =
                store.completeRetirement("rotation:2026-q3");
        assertThat(store.completeRetirement("rotation:2026-q3")).isEqualTo(retired);
        assertThat(retired.keys()).singleElement()
                .extracting(RuntimeProfileSigningKeyLifecycle.PublishedKeyState::keyId)
                .isEqualTo(activated.activeKeyId());
        assertThat(store.publishedKeys(clock.instant())).singleElement()
                .extracting("keyId").isEqualTo(activated.activeKeyId());
        assertThatThrownBy(() -> verifier.verify(signedByInitial, clock.instant()))
                .isInstanceOf(InvalidRuntimeProfileException.class);
    }

    @Test
    void concurrentRotationReferencesCannotOvertakeEachOther() {
        store.initialize("bootstrap:weave-local:2026-07-20");
        store.prepareRotation("rotation:2026-q3");

        assertThatThrownBy(() -> store.prepareRotation("rotation:2026-q4"))
                .isInstanceOf(RuntimeProfileSigningKeyException.class)
                .hasMessageContaining("different RuntimeProfile signing-key rotation");
        assertThatThrownBy(() -> store.activateRotation("rotation:2026-q4"))
                .isInstanceOf(RuntimeProfileSigningKeyException.class)
                .hasMessageContaining("different RuntimeProfile signing-key rotation");
    }

    @Test
    void activeKeyFailsClosedBeforeAProfileCouldOutliveItsTrustWindow() {
        store.initialize("bootstrap:weave-local:2026-07-20");
        clock.advance(KEY_LIFETIME.minus(PROFILE_TTL));

        assertThatThrownBy(store::activeKey)
                .isInstanceOf(RuntimeProfileSigningKeyException.class)
                .hasMessageContaining("safe issuance window");
    }

    @Test
    void groupReadablePrivateMaterialFailsClosed() throws Exception {
        RuntimeProfileSigningKeyLifecycle.KeyRingState initialized =
                store.initialize("bootstrap:weave-local:2026-07-20");
        Path privateKey = temporary.resolve("key-" + initialized.activeKeyId() + ".pk8");
        if (Files.getFileStore(privateKey).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(privateKey, PosixFilePermissions.fromString("rw-r-----"));

            assertThatThrownBy(store::activeKey)
                    .isInstanceOf(RuntimeProfileSigningKeyException.class)
                    .hasMessageContaining("too broadly accessible");
        }
    }

    private static RuntimeProfile profile(Instant issuedAt) {
        return new RuntimeProfile(
                RuntimeProfile.VERSION,
                "rp_key_store_test",
                "org:test",
                "person:test",
                new RuntimeMemberBinding("https://auth.weave.test/realms/weave", "member-test"),
                "cell:test",
                new RuntimeProfile.WorkloadIdentity(
                        "https://auth.weave.test/realms/weave",
                        "service-account-weaver-cell-test",
                        "weaver-cell-test",
                        "weaver-runtime",
                        RuntimeProfile.AuthenticationMethod.PRIVATE_KEY_JWT),
                issuedAt,
                issuedAt.plus(PROFILE_TTL),
                "entitlement:test",
                "workspace:test",
                "workspace-manifest:test",
                "runtime-state://org-test/person-test",
                true,
                new RuntimeProfile.ModelPolicy(List.of(), List.of(), List.of(), null, null),
                new RuntimeProfile.MatrixPolicy(
                        "matrix-account:test", "matrix-homeserver:test", null, List.of(),
                        RuntimeProfile.AutoJoin.OFF, true),
                new RuntimeProfile.McpPolicy(List.of(), List.of()),
                new RuntimeProfile.ApprovalPolicy(
                        "openclaw",
                        new RuntimeProfile.PluginRouting(
                                false, RuntimeProfile.PluginRoutingMode.LOCAL_ONLY, null),
                        RuntimeProfile.ExecMode.DENY,
                        RuntimeProfile.PersistentTrustPolicy.DISABLED),
                new RuntimeProfile.SandboxPolicy(
                        RuntimeProfile.SandboxMode.REQUIRED,
                        RuntimeProfile.NetworkPolicy.DENY,
                        null,
                        RuntimeProfile.FilesystemPolicy.WORKSPACE_ONLY,
                        null),
                new RuntimeProfile.AutomationPolicy(false, RuntimeProfile.SchedulePolicy.DISABLED));
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}

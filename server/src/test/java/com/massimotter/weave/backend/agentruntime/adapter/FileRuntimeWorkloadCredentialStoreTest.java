package com.massimotter.weave.backend.agentruntime.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadBinding;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadCredentialState;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadCredentialState.RotationPhase;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadCredentialStore.CreateCredentialCommand;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadCredentialStore.DeleteCredentialCommand;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadCredentialStore.RetireCredentialCommand;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadCredentialStore.RotateCredentialCommand;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileRuntimeWorkloadCredentialStoreTest {
    private static final String CLIENT_ID = "weaver-cell-example_01";
    private static final String OWNER = "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String OTHER_OWNER = "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @TempDir
    Path temporary;

    private ObjectMapper mapper;
    private FileRuntimeWorkloadCredentialStore store;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        store = new FileRuntimeWorkloadCredentialStore(
                temporary,
                mapper,
                Clock.fixed(Instant.parse("2026-07-20T10:00:00Z"), ZoneOffset.UTC),
                new SecureRandom());
    }

    @Test
    void createsOneOwnerOnlyPrivateKeyEnvelopeButReturnsPublicJwksOnly() throws Exception {
        RuntimeWorkloadCredentialState first = store.create(command());
        RuntimeWorkloadCredentialState replay = store.create(command());

        assertThat(replay).isEqualTo(first);
        assertThat(first.rotationPhase()).isEqualTo(RotationPhase.NONE);
        assertThat(first.acceptedKeyIds()).containsExactly(first.activeKeyId());
        JsonNode publicJwks = mapper.readTree(first.publicJwks());
        assertThat(publicJwks.path("keys").size()).isEqualTo(1);
        assertThat(publicJwks.path("keys").get(0).has("d")).isFalse();
        assertThat(publicJwks.path("keys").get(0).path("alg").asText()).isEqualTo("PS256");

        Path secret = secretPath();
        assertThat(Files.readString(secret)).contains("\"d\"").contains("\"qi\"");
        if (Files.getFileStore(secret).supportsFileAttributeView("posix")) {
            assertThat(Files.getPosixFilePermissions(secret))
                    .isEqualTo(PosixFilePermissions.fromString("rw-------"));
        }
    }

    @Test
    void rotationPublishesTheNextKeyBeforeActivationAndRetiresThePreviousKeyExplicitly() {
        RuntimeWorkloadCredentialState initial = store.create(command());
        RotateCredentialCommand rotation = new RotateCredentialCommand(CLIENT_ID, OWNER, "rotation:0000000000000001");

        RuntimeWorkloadCredentialState prepared = store.prepareRotation(rotation);
        RuntimeWorkloadCredentialState replay = store.prepareRotation(rotation);
        assertThat(replay).isEqualTo(prepared);
        assertThat(prepared.rotationPhase()).isEqualTo(RotationPhase.PREPARED);
        assertThat(prepared.activeKeyId()).isEqualTo(initial.activeKeyId());
        assertThat(prepared.acceptedKeyIds()).hasSize(2);

        RuntimeWorkloadCredentialState activated = store.activateRotation(rotation);
        assertThat(activated.rotationPhase()).isEqualTo(RotationPhase.ACTIVE_OVERLAP);
        assertThat(activated.activeKeyId()).isNotEqualTo(initial.activeKeyId());
        assertThat(activated.acceptedKeyIds()).contains(initial.activeKeyId(), activated.activeKeyId());

        RetireCredentialCommand retirement = new RetireCredentialCommand(
                CLIENT_ID, OWNER, "rotation:0000000000000001");
        RuntimeWorkloadCredentialState activeOnlyPlan = store.prepareRetirement(retirement);
        assertThat(activeOnlyPlan.rotationPhase()).isEqualTo(RotationPhase.NONE);
        assertThat(activeOnlyPlan.acceptedKeyIds()).containsExactly(activated.activeKeyId());

        RuntimeWorkloadCredentialState retired = store.completeRetirement(retirement);
        assertThat(retired).isEqualTo(store.completeRetirement(retirement));
        assertThat(retired.rotationPhase()).isEqualTo(RotationPhase.NONE);
        assertThat(retired.acceptedKeyIds()).containsExactly(activated.activeKeyId());
    }

    @Test
    void aSecondRotationCannotOvertakeAnUnretiredOverlap() {
        store.create(command());
        store.prepareRotation(new RotateCredentialCommand(CLIENT_ID, OWNER, "rotation:0000000000000001"));

        assertThatThrownBy(() -> store.prepareRotation(
                new RotateCredentialCommand(CLIENT_ID, OWNER, "rotation:0000000000000002")))
                .isInstanceOf(RuntimeWorkloadIdentityException.class)
                .hasMessageContaining("different workload credential rotation");
    }

    @Test
    void immutableOwnerMismatchCannotReadMutateOrDeleteAnotherCellsCredential() {
        store.create(command());

        assertThatThrownBy(() -> store.create(new CreateCredentialCommand(
                CLIENT_ID, OTHER_OWNER, RuntimeWorkloadBinding.AuthenticationMethod.PRIVATE_KEY_JWT)))
                .isInstanceOf(RuntimeWorkloadIdentityException.class)
                .hasMessageContaining("another immutable cell binding");
        assertThatThrownBy(() -> store.delete(new DeleteCredentialCommand(CLIENT_ID, OTHER_OWNER)))
                .isInstanceOf(RuntimeWorkloadIdentityException.class)
                .hasMessageContaining("another immutable cell binding");
        assertThat(store.find(CLIENT_ID)).isPresent();
    }

    @Test
    void secretCallbackBytesAreClearedAndDeleteIsIdempotent() {
        RuntimeWorkloadCredentialState created = store.create(command());
        AtomicReference<byte[]> observed = new AtomicReference<>();

        int size = store.withSecret(created.credentialRef(), bytes -> {
            observed.set(bytes);
            assertThat(bytes).containsAnyOf((byte) '{');
            return bytes.length;
        });

        assertThat(size).isPositive();
        assertThat(observed.get()).containsOnly((byte) 0);
        DeleteCredentialCommand delete = new DeleteCredentialCommand(CLIENT_ID, OWNER);
        store.delete(delete);
        store.delete(delete);
        assertThat(store.find(CLIENT_ID)).isEmpty();
    }

    @Test
    void groupReadableSecretFilesFailClosed() throws Exception {
        store.create(command());
        Path secret = secretPath();
        if (Files.getFileStore(secret).supportsFileAttributeView("posix")) {
            Set<PosixFilePermission> broad = PosixFilePermissions.fromString("rw-r-----");
            Files.setPosixFilePermissions(secret, broad);
            assertThatThrownBy(() -> store.find(CLIENT_ID))
                    .isInstanceOf(RuntimeWorkloadIdentityException.class)
                    .hasMessageContaining("permissions are too broad");
        }
    }

    @Test
    void concurrentInProcessCreatesConvergeOnOneCredentialEnvelope() throws Exception {
        try (var workers = Executors.newFixedThreadPool(8)) {
            CountDownLatch ready = new CountDownLatch(8);
            CountDownLatch start = new CountDownLatch(1);
            var futures = java.util.stream.IntStream.range(0, 8)
                    .mapToObj(ignored -> workers.submit(() -> {
                        ready.countDown();
                        start.await();
                        return store.create(command());
                    }))
                    .toList();
            ready.await();
            start.countDown();

            RuntimeWorkloadCredentialState expected = futures.getFirst().get();
            for (var future : futures) {
                assertThat(future.get()).isEqualTo(expected);
            }
            assertThat(mapper.readTree(Files.readString(secretPath())).path("keys")).hasSize(1);
        }
    }

    @Test
    void registrationAuthorityIsBoundToTheExactPublicRealmAndClient() {
        URI issuer = URI.create("https://auth.weave.test/realms/weave");
        FileRuntimeWorkloadCredentialStore strict =
                new FileRuntimeWorkloadCredentialStore(temporary, mapper, issuer);
        strict.create(command());
        byte[] token = "fixture-registration-authority".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> strict.bindRegistrationAuthority(
                CLIENT_ID,
                OWNER,
                OWNER,
                OWNER,
                OWNER,
                URI.create(
                        "https://foreign.example/realms/weave/clients-registrations/"
                                + "openid-connect/" + CLIENT_ID),
                token,
                "service-account-subject"))
                .isInstanceOf(RuntimeWorkloadIdentityException.class)
                .hasMessageContaining("registration authority is inconsistent");
        assertThatThrownBy(() -> strict.bindRegistrationAuthority(
                CLIENT_ID,
                OWNER,
                OWNER,
                OWNER,
                OWNER,
                URI.create(
                        issuer + "/clients-registrations/openid-connect/"
                                + CLIENT_ID + "?destination=elsewhere"),
                token,
                "service-account-subject"))
                .isInstanceOf(RuntimeWorkloadIdentityException.class)
                .hasMessageContaining("registration authority is inconsistent");

        URI expected = URI.create(
                issuer + "/clients-registrations/openid-connect/" + CLIENT_ID);
        strict.bindRegistrationAuthority(
                CLIENT_ID,
                OWNER,
                OWNER,
                OWNER,
                OWNER,
                expected,
                token,
                "service-account-subject");

        assertThat(strict.registrationAuthority(CLIENT_ID, OWNER))
                .hasValueSatisfying(authority ->
                        assertThat(authority.registrationUri()).isEqualTo(expected));
    }

    @Test
    void rotatedRegistrationAuthorityIsJournaledOwnerOnlyUntilTheExactCommit() throws Exception {
        URI issuer = URI.create("https://auth.weave.test/realms/weave");
        FileRuntimeWorkloadCredentialStore strict =
                new FileRuntimeWorkloadCredentialStore(temporary, mapper, issuer);
        strict.create(command());
        URI registration =
                URI.create(issuer + "/clients-registrations/openid-connect/" + CLIENT_ID);
        byte[] initial = "fixture-registration-authority-initial"
                .getBytes(StandardCharsets.UTF_8);
        byte[] rotated = "fixture-registration-authority-rotated"
                .getBytes(StandardCharsets.UTF_8);
        strict.bindRegistrationAuthority(
                CLIENT_ID,
                OWNER,
                OWNER,
                OWNER,
                OWNER,
                registration,
                initial,
                "service-account-subject");

        strict.stageRegistrationRecovery(
                CLIENT_ID,
                OWNER,
                registration,
                rotated,
                "service-account-subject",
                false,
                FileRuntimeWorkloadCredentialStore.RegistrationRecoveryAction.COMMIT);

        Path recovery = temporary.resolve(
                "weave/agent-runtime/registration-recovery/" + CLIENT_ID);
        assertThat(Files.isRegularFile(recovery)).isTrue();
        if (Files.getFileStore(recovery).supportsFileAttributeView("posix")) {
            assertThat(Files.getPosixFilePermissions(recovery))
                    .isEqualTo(PosixFilePermissions.fromString("rw-------"));
        }
        assertThat(strict.registrationRecoveryCommitted(CLIENT_ID, OWNER)).isFalse();
        AtomicReference<byte[]> observed = new AtomicReference<>();
        strict.withRegistrationRecoveryAccessToken(
                CLIENT_ID,
                OWNER,
                (pending, token) -> {
                    observed.set(token);
                    assertThat(pending.action())
                            .isEqualTo(FileRuntimeWorkloadCredentialStore
                                    .RegistrationRecoveryAction.COMMIT);
                    assertThat(token).isEqualTo(rotated);
                    return null;
                });
        assertThat(observed.get()).containsOnly((byte) 0);
        assertThatThrownBy(() -> strict.registrationRecovery(CLIENT_ID, OTHER_OWNER))
                .isInstanceOf(RuntimeWorkloadIdentityException.class)
                .hasMessageContaining("another immutable cell binding");

        String previousFingerprint =
                strict.registrationAuthority(CLIENT_ID, OWNER).orElseThrow().tokenFingerprint();
        strict.replaceRegistrationAuthority(
                CLIENT_ID,
                OWNER,
                previousFingerprint,
                registration,
                rotated,
                "service-account-subject",
                false);
        assertThat(strict.registrationRecoveryCommitted(CLIENT_ID, OWNER)).isTrue();
        String rotatedFingerprint =
                strict.registrationRecovery(CLIENT_ID, OWNER).orElseThrow().tokenFingerprint();
        strict.clearRegistrationRecovery(CLIENT_ID, OWNER, rotatedFingerprint);

        assertThat(strict.registrationRecovery(CLIENT_ID, OWNER)).isEmpty();
        assertThat(Files.exists(recovery)).isFalse();
    }

    private static CreateCredentialCommand command() {
        return new CreateCredentialCommand(
                CLIENT_ID, OWNER, RuntimeWorkloadBinding.AuthenticationMethod.PRIVATE_KEY_JWT);
    }

    private Path secretPath() {
        return temporary.resolve("weave/agent-runtime/cells").resolve(CLIENT_ID);
    }
}

package com.massimotter.weave.backend.agentruntime.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.agentruntime.domain.RestoredRuntimeState;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeStateGeneration;
import com.massimotter.weave.backend.agentruntime.port.RuntimeStateStore;
import com.massimotter.weave.backend.agentruntime.port.RuntimeStateStoreAdmin;
import com.massimotter.weave.backend.agentruntime.port.RuntimeStateStoreException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class S3EncryptedRuntimeStateStoreTest {
    private static final Instant NOW = Instant.parse("2026-07-20T12:30:00Z");
    private static final String HASH = "sha256:" + "a".repeat(64);

    @TempDir
    Path temporary;

    private EmbeddedDatabase database;
    private JdbcTemplate jdbc;
    private FileRuntimeStateKeyWrapper keys;
    private S3EncryptedRuntimeStateStore store;
    private Map<String, byte[]> objects;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("arc-state-" + UUID.randomUUID() + ";MODE=PostgreSQL")
                .build();
        new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration/V013__agent_runtime_external_state.sql")).execute(database);
        jdbc = new JdbcTemplate(database);
        keys = new FileRuntimeStateKeyWrapper(
                temporary.resolve("keys").toAbsolutePath(),
                tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                new SecureRandom());
        keys.initialize("operator:init:runtime-state");
        objects = new ConcurrentHashMap<>();
        store = new S3EncryptedRuntimeStateStore(
                runtimeStateAuthority(database),
                com.massimotter.weave.backend.testing.JpaTestDatabase.transactionManager(database),
                objectStore(objects),
                "weave-runtime-state-test",
                keys,
                new SecureRandom(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                1024 * 1024);
    }

    @Test
    void commitsOneImmutableCiphertextObjectAndRestoresAuthenticatedState() {
        byte[] state = ("runtime-secret-session-and-plugin-state-" + "x".repeat(9_000))
                .getBytes(StandardCharsets.UTF_8);

        RuntimeStateGeneration committed = store.commit(command("alice", 0, state, "state-write-alice-0001"));
        Optional<RestoredRuntimeState> restored = store.current(read("alice"));

        assertThat(committed.generation()).isEqualTo(1);
        assertThat(committed.chunkCount()).isEqualTo(1);
        assertThat(restored).isPresent();
        assertThat(restored.orElseThrow().state()).isEqualTo(state);
        assertThat(jdbc.queryForObject(
                "select count(*) from weave_agent_runtime_state_chunks", Integer.class))
                .isZero();
        assertThat(objects).hasSize(1);
        byte[] ciphertext = objects.values().iterator().next();
        assertThat(new String(ciphertext, java.nio.charset.StandardCharsets.ISO_8859_1))
                .doesNotContain("runtime-secret-session-and-plugin-state-");
        assertThat(store.readiness()).isEqualTo(
                new RuntimeStateStore.StoreReadiness(
                        false,
                        "guarded-cross-store-reconciliation-required",
                        1));
    }

    @Test
    void generationCasAndIdempotencyFailClosed() {
        byte[] first = "first-runtime-state".getBytes(StandardCharsets.UTF_8);
        RuntimeStateStore.CommitRuntimeStateCommand command = command(
                "alice", 0, first, "state-write-alice-0001");
        RuntimeStateGeneration committed = store.commit(command);

        assertThat(store.commit(command)).isEqualTo(committed);
        assertThatThrownBy(() -> store.commit(command(
                        "alice", 0, "changed".getBytes(StandardCharsets.UTF_8),
                        "state-write-alice-0001")))
                .isInstanceOf(RuntimeStateStoreException.class)
                .hasMessageContaining("reused");
        assertThatThrownBy(() -> store.commit(command(
                        "alice", 0, "second".getBytes(StandardCharsets.UTF_8),
                        "state-write-alice-0002")))
                .isInstanceOf(RuntimeStateStoreException.class)
                .hasMessageContaining("compare-and-swap");

        RuntimeStateGeneration second = store.commit(command(
                "alice", 1, "second".getBytes(StandardCharsets.UTF_8),
                "state-write-alice-0002"));
        assertThat(second.generation()).isEqualTo(2);
    }

    @Test
    void crossCellReadsAndCiphertextTamperingAreRejected() {
        store.commit(command(
                "alice", 0, "bound state".getBytes(StandardCharsets.UTF_8),
                "state-write-alice-0001"));

        assertThatThrownBy(() -> store.current(new RuntimeStateStore.ReadRuntimeStateCommand(
                        "org:example", "person:bob", "cell:bob", storeRef("alice"))))
                .isInstanceOf(RuntimeStateStoreException.class)
                .hasMessageContaining("binding");

        String objectKey = objects.keySet().iterator().next();
        byte[] tampered = objects.get(objectKey).clone();
        tampered[0] ^= 0x01;
        objects.put(objectKey, tampered);
        assertThatThrownBy(() -> store.current(read("alice")))
                .isInstanceOf(RuntimeStateStoreException.class)
                .hasMessageContaining("authentication");
    }

    @Test
    void overlapRotationKeepsOldStateReadableAndUsesTheNewKeyForNewState() {
        RuntimeStateGeneration before = store.commit(command(
                "alice", 0, "before rotation".getBytes(StandardCharsets.UTF_8),
                "state-write-alice-0001"));
        keys.rotate("operator:rotate:runtime-state:001");
        RuntimeStateGeneration after = store.commit(command(
                "alice", 1, "after rotation".getBytes(StandardCharsets.UTF_8),
                "state-write-alice-0002"));

        assertThat(after.wrappingKeyRef()).isNotEqualTo(before.wrappingKeyRef());
        assertThat(store.current(read("alice")).orElseThrow().state())
                .isEqualTo("after rotation".getBytes(StandardCharsets.UTF_8));
        jdbc.update("""
                update weave_agent_runtime_state_heads
                   set current_generation = 1, current_generation_ref = ?
                 where runtime_state_store_ref = ?
                """, before.generationRef(), storeRef("alice"));
        assertThat(store.current(read("alice")).orElseThrow().state())
                .isEqualTo("before rotation".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void explicitDeletionIsScopedIdempotentAndPreservesOtherRuntimeState() {
        store.commit(command(
                "alice", 0, "alice state".getBytes(StandardCharsets.UTF_8),
                "state-write-alice-0001"));
        store.commit(command(
                "bob", 0, "bob state".getBytes(StandardCharsets.UTF_8),
                "state-write-bob-00001"));
        RuntimeStateStoreAdmin.DeleteRuntimeStateCommand delete =
                new RuntimeStateStoreAdmin.DeleteRuntimeStateCommand(
                        "org:example", "person:alice", "cell:alice", storeRef("alice"),
                        "state-delete-alice-01", "audit:delete-alice");

        store.deleteRuntimeState(delete);
        store.deleteRuntimeState(delete);

        assertThat(store.current(read("alice"))).isEmpty();
        assertThat(store.current(read("bob")).orElseThrow().state())
                .isEqualTo("bob state".getBytes(StandardCharsets.UTF_8));
        assertThat(objects).hasSize(1);
        assertThat(jdbc.queryForObject(
                "select deleted_generation_count from weave_agent_runtime_state_deletions "
                        + "where person_ref = 'person:alice'",
                Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select completed_at from weave_agent_runtime_state_deletions "
                        + "where person_ref = 'person:alice'",
                Timestamp.class)).isEqualTo(Timestamp.from(NOW));
    }

    @Test
    void ambiguousObjectWriteLeavesAnOrphanAndThereforeCannotPromoteTheCapability() {
        Map<String, byte[]> ambiguousObjects = new ConcurrentHashMap<>();
        S3EncryptedRuntimeStateStore guarded = new S3EncryptedRuntimeStateStore(
                runtimeStateAuthority(database),
                com.massimotter.weave.backend.testing.JpaTestDatabase.transactionManager(database),
                objectStore(ambiguousObjects, true),
                "weave-runtime-state-test",
                keys,
                new SecureRandom(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                1024 * 1024);

        assertThatThrownBy(() -> guarded.commit(command(
                        "alice", 0, "ambiguous state".getBytes(StandardCharsets.UTF_8),
                        "state-write-alice-0001")))
                .isInstanceOf(RuntimeStateStoreException.class)
                .hasMessageContaining("could not be committed");
        assertThat(ambiguousObjects).hasSize(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from weave_agent_runtime_state_generations", Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from weave_agent_runtime_state_heads", Integer.class)).isZero();
        assertThat(guarded.readiness().ready()).isFalse();
        assertThat(guarded.readiness().state())
                .isEqualTo("guarded-cross-store-reconciliation-required");
    }

    private static RuntimeStateStore.CommitRuntimeStateCommand command(
            String person,
            long expectedGeneration,
            byte[] state,
            String idempotencyKey) {
        return new RuntimeStateStore.CommitRuntimeStateCommand(
                "org:example",
                "person:" + person,
                "cell:" + person,
                storeRef(person),
                expectedGeneration,
                HASH,
                state,
                idempotencyKey,
                "audit:" + person);
    }

    private static RuntimeStateStore.ReadRuntimeStateCommand read(String person) {
        return new RuntimeStateStore.ReadRuntimeStateCommand(
                "org:example", "person:" + person, "cell:" + person, storeRef(person));
    }

    private static String storeRef(String person) {
        return "runtime-state://org/example/person/" + person + "/state/v1";
    }

    private static RuntimeStateJpaAuthority runtimeStateAuthority(EmbeddedDatabase database) {
        return new RuntimeStateJpaAuthority(
                com.massimotter.weave.backend.testing.JpaTestDatabase.repository(
                        database, RuntimeStateHeadJpaRepository.class),
                com.massimotter.weave.backend.testing.JpaTestDatabase.repository(
                        database, RuntimeStateGenerationJpaRepository.class),
                com.massimotter.weave.backend.testing.JpaTestDatabase.repository(
                        database, RuntimeStateDeletionJpaRepository.class));
    }

    private static S3Client objectStore(Map<String, byte[]> objects) {
        return objectStore(objects, false);
    }

    private static S3Client objectStore(Map<String, byte[]> objects, boolean ambiguousPut) {
        S3Client client = mock(S3Client.class);
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenAnswer(invocation -> {
                    PutObjectRequest request = invocation.getArgument(0);
                    RequestBody body = invocation.getArgument(1);
                    assertThat(request.ifNoneMatch()).isEqualTo("*");
                    try (var input = body.contentStreamProvider().newStream()) {
                        objects.put(request.key(), input.readAllBytes());
                    }
                    if (ambiguousPut) {
                        throw SdkClientException.create("simulated ambiguous object-store response");
                    }
                    return PutObjectResponse.builder().eTag("test-etag").build();
                });
        when(client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenAnswer(invocation -> {
                    GetObjectRequest request = invocation.getArgument(0);
                    byte[] value = objects.get(request.key());
                    if (value == null) {
                        throw new IllegalStateException("test object is missing");
                    }
                    return ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), value.clone());
                });
        when(client.deleteObject(any(DeleteObjectRequest.class)))
                .thenAnswer(invocation -> {
                    DeleteObjectRequest request = invocation.getArgument(0);
                    objects.remove(request.key());
                    return DeleteObjectResponse.builder().build();
                });
        when(client.headBucket(any(HeadBucketRequest.class)))
                .thenReturn(HeadBucketResponse.builder().build());
        return client;
    }
}

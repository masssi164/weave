package com.massimotter.weave.backend.files.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.files.application.FilesEtags;
import com.massimotter.weave.backend.files.application.FilesDigests;
import com.massimotter.weave.backend.files.application.CanonicalFilesMutationPlanner;
import com.massimotter.weave.backend.files.application.FilesLockService;
import com.massimotter.weave.backend.files.application.FilesLockService.FileLockedException;
import com.massimotter.weave.backend.files.application.FilesMutationIntentService;
import com.massimotter.weave.backend.files.application.FilesMutationTargetCodec;
import com.massimotter.weave.backend.files.application.FilesScope;
import com.massimotter.weave.backend.files.application.NativeFilesBlobCleanupCoordinator;
import com.massimotter.weave.backend.files.application.NativeFilesBlobCleanupCoordinator.CleanupResult;
import com.massimotter.weave.backend.files.application.NativeFilesMutationRepository;
import com.massimotter.weave.backend.files.application.NativeFilesMutationRepository.FinalizationResult;
import com.massimotter.weave.backend.files.application.NativeFilesMutationRepository.CommitOutcome;
import com.massimotter.weave.backend.files.domain.FilesAuthority.CanonicalFileRecord;
import com.massimotter.weave.backend.files.domain.FilesAuthority.Lifecycle;
import com.massimotter.weave.backend.files.domain.FilesChangeStream.ChangeKind;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileId;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileObject;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileVersion;
import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import com.massimotter.weave.backend.files.port.BlobStorePort;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobReceipt;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobReference;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobScope;
import com.massimotter.weave.backend.files.port.FilesAuthorityRepository.ConcurrentMutationException;
import com.massimotter.weave.backend.files.port.FilesMutationPlan;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.Draft;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.Sealed;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.Target;
import com.massimotter.weave.backend.files.port.ReplayableFileContent;
import com.massimotter.weave.backend.files.port.StoredFileRecord;
import com.massimotter.weave.backend.files.port.StoredFileRecord.BlobBinding;
import com.massimotter.weave.backend.operation.adapter.OperationIntentJpaTestFactory;
import com.massimotter.weave.backend.operation.application.OperationIntentService;
import com.massimotter.weave.backend.operation.domain.OperationIntent;
import com.massimotter.weave.backend.operation.domain.OperationIntent.HumanActor;
import com.massimotter.weave.backend.operation.domain.OperationIntent.ProtocolProjection;
import com.massimotter.weave.backend.operation.domain.OperationIntent.State;
import com.massimotter.weave.backend.testing.JpaTestDatabase;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@Tag("postgres")
class JpaFilesAuthorityRepositoryPostgresTest {

    private static final String DIGEST_A = "sha256:" + "a".repeat(64);
    private static final String DIGEST_B = "sha256:" + "b".repeat(64);
    private static final String DIGEST_C = "sha256:" + "c".repeat(64);
    private static final Instant MUTATION_CREATED_AT = Instant.parse("2026-08-20T10:00:00Z");
    private static final Instant MUTATION_SEALED_AT = Instant.parse("2026-08-20T10:00:01Z");
    private static final Instant MUTATION_RESULT_AT = Instant.parse("2026-08-20T10:00:02Z");
    private static final Instant MUTATION_COMMITTED_AT = Instant.parse("2026-08-20T10:00:03Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void movePreservesCanonicalIdentityAndLocksPersistOnlyTokenDigests() {
        DriverManagerDataSource dataSource = dataSource();
        JpaTestDatabase.initializeSchema(dataSource);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        var repository = repository(dataSource);
        Instant now = Instant.parse("2026-07-22T01:00:00Z");
        FileId stableId = new FileId("file:stable-document");
        FilePath original = new FilePath("/Documents/plan.md");
        FilePath moved = new FilePath("/Documents/core-plan.md");
        String blobReference =
                "v1/file/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        repository.save(new StoredFileRecord(
                new CanonicalFileRecord(
                        "org:example", "space:home",
                        new FileObject(stableId, original, Kind.FILE, 12, "text/markdown", now, false),
                        new FileVersion("etag-1"),
                        "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        1, Lifecycle.ACTIVE, now),
                new BlobBinding(blobReference)));

        StoredFileRecord afterMove = repository.move(
                "org:example", "space:home", stableId, original, moved, now.plusSeconds(1));
        var locks = new FilesLockService(repository, Clock.fixed(now.plusSeconds(2), ZoneOffset.UTC));
        var granted = locks.acquire(
                "org:example", "space:home", moved, "person:alice", Duration.ofMinutes(30));

        assertThat(afterMove.metadata().object().id()).isEqualTo(stableId);
        assertThat(afterMove.metadata().object().path()).isEqualTo(moved);
        assertThat(afterMove.blobBinding()).isEqualTo(new BlobBinding(blobReference));
        assertThat(jdbc.queryForObject(
                "select storage_reference from weave_files_objects "
                        + "where organization_ref = ? and space_ref = ? and file_id = ?",
                String.class,
                "org:example",
                "space:home",
                stableId.value()))
                .isEqualTo(blobReference);
        assertThat(jdbc.queryForObject(
                "select token_digest from weave_file_locks where canonical_path = ?",
                String.class,
                moved.value()))
                .startsWith("sha256:")
                .isNotEqualTo(granted.token());
        assertThatThrownBy(() -> locks.requireUnlocked(
                "org:example", "space:home", moved, "opaquelocktoken:wrong", "person:alice"))
                .isInstanceOf(FileLockedException.class);
        assertThatThrownBy(() -> locks.requireUnlocked(
                "org:example", "space:home",
                new FilePath("/Documents/core-plan.md/attachment.txt"),
                null,
                "person:alice"))
                .isInstanceOf(FileLockedException.class);

        var afterRestart = new FilesLockService(
                repository(dataSource),
                Clock.fixed(now.plusSeconds(3), ZoneOffset.UTC));
        assertThatThrownBy(() -> afterRestart.requireUnlocked(
                "org:example", "space:home", moved, null, "person:alice"))
                .isInstanceOf(FileLockedException.class);

        FilePath movedAgain = new FilePath("/Archive/core-plan.md");
        afterRestart.move(
                "org:example", "space:home", moved, movedAgain,
                granted.token(), "person:alice");
        assertThat(repository.activeLock(
                "org:example", "space:home", moved, now.plusSeconds(3))).isEmpty();
        assertThat(repository.activeLock(
                "org:example", "space:home", movedAgain, now.plusSeconds(3))).isPresent();
        afterRestart.requireUnlocked(
                "org:example", "space:home", movedAgain,
                granted.token(), "person:alice");
        afterRestart.release(
                "org:example", "space:home", movedAgain,
                granted.token(), "person:alice");
        assertThat(repository.activeLock(
                "org:example", "space:home", movedAgain, now.plusSeconds(3))).isEmpty();
    }

    @Test
    void activationTranslatesActivePathRaceIntoCanonicalPortFailure() {
        DriverManagerDataSource dataSource = dataSource();
        JpaTestDatabase.initializeSchema(dataSource);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        var repository = repository(dataSource);
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String organization = "org:activation:" + suffix;
        String space = "space:home";
        FilePath path = new FilePath("/Activation/" + suffix + ".txt");
        Instant now = Instant.parse("2026-08-19T02:00:00Z");
        StoredFileRecord first = activeFile(
                organization,
                space,
                new FileId("file:first:" + suffix),
                path,
                "a",
                now);
        StoredFileRecord competing = activeFile(
                organization,
                space,
                new FileId("file:second:" + suffix),
                path,
                "b",
                now.plusSeconds(1));

        repository.activate(first);

        assertThatThrownBy(() -> repository.activate(competing))
                .isInstanceOf(ConcurrentMutationException.class)
                .hasMessageContaining(path.value());
        assertThat(jdbc.queryForObject(
                """
                select count(*)
                  from weave_files_objects
                 where organization_ref = ?
                   and space_ref = ?
                   and canonical_path = ?
                   and lifecycle_state = 'ACTIVE'
                """,
                Integer.class,
                organization,
                space,
                path.value()))
                .isEqualTo(1);
    }

    @Test
    void replaceTreePersistsPrivateBindingsAndRollsBackMetadataWithBindingOnConflict() {
        DriverManagerDataSource dataSource = dataSource();
        JpaTestDatabase.initializeSchema(dataSource);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        var repository = repository(dataSource);
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String organization = "org:replacement:" + suffix;
        String space = "space:home";
        Instant now = Instant.parse("2026-08-19T04:00:00Z");

        StoredFileRecord source = activeFile(
                organization,
                space,
                new FileId("file:source:" + suffix),
                new FilePath("/Source/" + suffix + ".txt"),
                "c",
                now);
        StoredFileRecord copied = activeFile(
                organization,
                space,
                new FileId("file:copy:" + suffix),
                new FilePath("/Copy/" + suffix + ".txt"),
                "d",
                now.plusSeconds(1));
        repository.save(source);

        repository.replaceTree(copied.metadata().object().path(), List.of(), List.of(copied));

        assertThat(repository.findById(
                organization,
                space,
                copied.metadata().object().id()).orElseThrow().blobBinding())
                .isEqualTo(copied.blobBinding())
                .isNotEqualTo(source.blobBinding());
        assertThat(persistedBinding(jdbc, source)).isEqualTo(source.blobBinding().opaqueReference());
        assertThat(persistedBinding(jdbc, copied)).isEqualTo(copied.blobBinding().opaqueReference());

        StoredFileRecord copiedTombstone = tombstone(copied, now.plusSeconds(2));
        repository.replaceTree(
                copied.metadata().object().path(),
                List.of(copiedTombstone),
                List.of());

        assertThat(persistedLifecycle(jdbc, copied)).isEqualTo(Lifecycle.TOMBSTONED.name());
        assertThat(persistedBinding(jdbc, copied)).isEqualTo(copied.blobBinding().opaqueReference());

        StoredFileRecord original = activeFile(
                organization,
                space,
                new FileId("file:original:" + suffix),
                new FilePath("/Atomic/" + suffix + ".txt"),
                "e",
                now.plusSeconds(3));
        StoredFileRecord occupied = activeFile(
                organization,
                space,
                new FileId("file:occupied:" + suffix),
                new FilePath("/Occupied/" + suffix + ".txt"),
                "f",
                now.plusSeconds(4));
        StoredFileRecord colliding = activeFile(
                organization,
                space,
                new FileId("file:colliding:" + suffix),
                occupied.metadata().object().path(),
                "0",
                now.plusSeconds(5));
        repository.save(original);
        repository.save(occupied);

        assertThatThrownBy(() -> repository.replaceTree(
                original.metadata().object().path(),
                List.of(tombstone(original, now.plusSeconds(5))),
                List.of(colliding)))
                .isInstanceOf(ConcurrentMutationException.class)
                .hasMessageContaining(original.metadata().object().path().value());

        assertThat(persistedLifecycle(jdbc, original)).isEqualTo(Lifecycle.ACTIVE.name());
        assertThat(persistedBinding(jdbc, original)).isEqualTo(original.blobBinding().opaqueReference());
        assertThat(persistedLifecycle(jdbc, occupied)).isEqualTo(Lifecycle.ACTIVE.name());
        assertThat(persistedBinding(jdbc, occupied)).isEqualTo(occupied.blobBinding().opaqueReference());
        assertThat(jdbc.queryForObject(
                """
                select count(*)
                  from weave_files_objects
                 where organization_ref = ?
                   and space_ref = ?
                   and file_id = ?
                """,
                Integer.class,
                organization,
                space,
                colliding.metadata().object().id().value()))
                .isZero();
    }

    @Test
    void nativeMutationTx1FailsClosedWhenScopeProvisioningWasSkipped() {
        MutationFixture fixture = mutationFixture("tx1_missing_head");
        fixture.jdbc().update("delete from weave_files_stream_heads");
        PlannedMutation mutation = plannedFile(fixture, "tx1-missing-head", "/missing-head.txt");
        FilesScope scope = new FilesScope(
                mutation.intent().organizationRef(), mutation.plan().spaceRef());

        assertThatThrownBy(() -> fixture.mutations().begin(
                mutation.intent(), scope, () -> mutation.plan()))
                .isInstanceOf(JpaFilesMutationRepository.CorruptFilesMutationException.class)
                .hasMessageContaining("stream head is missing");

        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_files_stream_heads", Integer.class)).isZero();
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_operation_intents", Integer.class)).isZero();
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_files_mutation_plans", Integer.class)).isZero();
    }

    @Test
    void explicitScopeBootstrapIsIdempotentUnderConcurrentProvisioning() throws Exception {
        MutationFixture fixture = mutationFixture("scope_bootstrap_concurrency");
        fixture.jdbc().update("delete from weave_files_stream_heads");
        FilesScope scope = new FilesScope("org:files-mutation", "space:files-mutation");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> {
                ready.countDown();
                start.await();
                fixture.mutations().provisionScope(scope, MUTATION_CREATED_AT);
                return null;
            });
            var second = workers.submit(() -> {
                ready.countDown();
                start.await();
                fixture.mutations().provisionScope(scope, MUTATION_CREATED_AT);
                return null;
            });
            assertThat(ready.await(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(30, java.util.concurrent.TimeUnit.SECONDS);
            second.get(30, java.util.concurrent.TimeUnit.SECONDS);
        }

        fixture.mutations().provisionScope(scope, MUTATION_CREATED_AT.plusSeconds(1));
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_files_stream_heads "
                        + "where organization_ref = ? and space_ref = ?",
                Integer.class,
                scope.organizationRef(),
                scope.spaceRef()))
                .isEqualTo(1);
        assertThat(fixture.jdbc().queryForObject(
                "select latest_revision from weave_files_stream_heads "
                        + "where organization_ref = ? and space_ref = ?",
                Long.class,
                scope.organizationRef(),
                scope.spaceRef()))
                .isZero();
    }

    @Test
    void bootstrapAndRetryRefuseToRepairAMissingHeadAfterTx1Activity() {
        MutationFixture fixture = mutationFixture("scope_bootstrap_after_activity");
        PlannedMutation mutation = plannedFile(
                fixture,
                "scope-bootstrap-after-activity",
                "/scope-bootstrap-after-activity.txt");
        fixture.mutations().begin(mutation.intent(), mutation.plan());
        fixture.jdbc().update("delete from weave_files_stream_heads");
        FilesScope scope = new FilesScope(
                mutation.plan().organizationRef(),
                mutation.plan().spaceRef());

        assertThatThrownBy(() -> fixture.mutations().provisionScope(
                        scope,
                        MUTATION_CREATED_AT.plusSeconds(1)))
                .isInstanceOf(JpaFilesMutationRepository.CorruptFilesMutationException.class)
                .hasMessageContaining("after scope activity");
        assertThatThrownBy(() -> fixture.mutations().probe(
                        mutation.intent().operationRef()))
                .isInstanceOf(JpaFilesMutationRepository.CorruptFilesMutationException.class)
                .hasMessageContaining("stream head is missing");
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_files_stream_heads",
                Integer.class)).isZero();
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_operation_intents",
                Integer.class)).isEqualTo(1);
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_files_mutation_plans",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void explicitScopeBootstrapRefusesToRepairAMissingHeadAfterLockOnlyActivity() {
        MutationFixture fixture = mutationFixture("scope_bootstrap_after_lock_activity");
        FilesScope scope = new FilesScope(
                "org:files-mutation",
                "space:files-mutation");
        fixture.jdbc().update(
                """
                insert into weave_file_locks (
                    canonical_path,
                    organization_ref,
                    space_ref,
                    created_at_utc,
                    expires_at_utc,
                    fence,
                    owner_ref,
                    token_digest,
                    version
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "/lock-only.txt",
                scope.organizationRef(),
                scope.spaceRef(),
                java.time.OffsetDateTime.ofInstant(MUTATION_CREATED_AT, ZoneOffset.UTC),
                java.time.OffsetDateTime.ofInstant(
                        MUTATION_CREATED_AT.plusSeconds(300), ZoneOffset.UTC),
                1L,
                "person:lock-only",
                "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                0L);
        fixture.jdbc().update("delete from weave_files_stream_heads");

        assertThatThrownBy(() -> fixture.mutations().provisionScope(
                        scope,
                        MUTATION_CREATED_AT.plusSeconds(1)))
                .isInstanceOf(JpaFilesMutationRepository.CorruptFilesMutationException.class)
                .hasMessageContaining("after scope activity");
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_files_stream_heads",
                Integer.class)).isZero();
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_file_locks",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void nativeMutationTx1CommitsIntentSealedPlanAndTargetsWithoutInitialOutbox() {
        MutationFixture fixture = mutationFixture("tx1");
        PlannedMutation mutation = plannedFile(fixture, "tx1", "/tx1.txt");

        var begun = fixture.mutations().begin(mutation.intent(), mutation.plan());

        assertThat(begun.created()).isTrue();
        assertThat(begun.intent().state()).isEqualTo(State.CREATED);
        assertThat(begun.plan()).isEqualTo(mutation.plan());
        assertThat(fixture.mutations().ingressProtection(mutation.intent().operationRef()))
                .isEqualTo(NativeFilesMutationRepository.IngressProtection.PROTECTED);
        assertThat(fixture.mutations().recoverablePutMutations(16))
                .containsExactly(new NativeFilesMutationRepository.RecoveryCandidate(
                        begun.intent(), mutation.plan()));
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_operation_intents",
                Integer.class)).isEqualTo(1);
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_operation_outbox",
                Integer.class)).isZero();
        assertThat(fixture.jdbc().queryForObject(
                "select plan_state from weave_files_mutation_plans where operation_ref = ?",
                String.class,
                mutation.intent().operationRef())).isEqualTo("SEALED");
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_files_mutation_targets where operation_ref = ?",
                Integer.class,
                mutation.intent().operationRef())).isEqualTo(1);
        assertThat(fixture.jdbc().queryForObject(
                "select latest_revision from weave_files_stream_heads "
                        + "where organization_ref = ? and space_ref = ?",
                Long.class,
                mutation.intent().organizationRef(),
                mutation.plan().spaceRef())).isZero();
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_files_objects",
                Integer.class)).isZero();
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_files_changes",
                Integer.class)).isZero();
    }

    @Test
    void recoverablePutPagingAdvancesAcrossAFullCorruptPage() {
        MutationFixture fixture = mutationFixture("recovery_cursor_corrupt_page");
        List<PlannedMutation> mutations = java.util.stream.IntStream.rangeClosed(0, 16)
                .mapToObj(index -> plannedFile(
                        fixture,
                        "recovery-cursor-%02d".formatted(index),
                        "/recovery-cursor-%02d.txt".formatted(index)))
                .toList();
        mutations.forEach(mutation -> fixture.mutations().begin(mutation.intent(), mutation.plan()));
        String corruptDigest = "sha256:" + "0".repeat(64);
        // Simulate out-of-band physical corruption that bypasses the normal immutable-plan guard.
        fixture.jdbc().execute(
                "alter table weave_files_mutation_plans disable trigger trg_weave_files_v7_plan_immutability");
        try {
            mutations.subList(0, 16).forEach(mutation -> fixture.jdbc().update(
                    "update weave_files_mutation_plans set targets_digest = ? where operation_ref = ?",
                    corruptDigest,
                    mutation.intent().operationRef()));
        } finally {
            fixture.jdbc().execute(
                    "alter table weave_files_mutation_plans enable trigger trg_weave_files_v7_plan_immutability");
        }

        var corruptPage = fixture.mutations().recoverablePutMutations(null, 16);
        var healthyPage = fixture.mutations().recoverablePutMutations(
                corruptPage.lastScannedOperationRef(), 16);

        assertThat(corruptPage.candidates()).isEmpty();
        assertThat(corruptPage.scannedCount()).isEqualTo(16);
        assertThat(corruptPage.lastScannedOperationRef())
                .isEqualTo(mutations.get(15).intent().operationRef());
        assertThat(healthyPage.scannedCount()).isEqualTo(1);
        assertThat(healthyPage.candidates())
                .containsExactly(new NativeFilesMutationRepository.RecoveryCandidate(
                        mutations.get(16).intent(),
                        mutations.get(16).plan()));
    }

    @Test
    void nativeMutationTx2AtomicallyCommitsBindingJournalHeadSuccessAndReservedOutbox() {
        MutationFixture fixture = mutationFixture("tx2");
        PlannedMutation mutation = plannedFile(fixture, "tx2", "/tx2.txt");
        var begun = fixture.mutations().begin(mutation.intent(), mutation.plan());

        FinalizationResult result = fixture.mutations().finalizeSuccess(
                begun.intent(),
                mutation.plan(),
                DIGEST_C,
                "audit:files:tx2",
                null);

        assertThat(result.intent().state()).isEqualTo(State.SUCCEEDED);
        assertThat(fixture.mutations().ingressProtection(mutation.intent().operationRef()))
                .isEqualTo(NativeFilesMutationRepository.IngressProtection.UNPROTECTED);
        assertThat(fixture.mutations().recoverablePutMutations(16)).isEmpty();
        assertThat(result.rangeStart()).isEqualTo(1);
        assertThat(result.rangeEnd()).isEqualTo(1);
        Map<String, Object> file = fixture.jdbc().queryForMap("""
            select canonical_path, lifecycle_state, storage_reference, content_digest,
                   version_token
              from weave_files_objects
             where organization_ref = ? and space_ref = ? and file_id = ?
            """,
                mutation.intent().organizationRef(),
                mutation.plan().spaceRef(),
                mutation.target().targetFileRef());
        assertThat(file)
                .containsEntry("canonical_path", mutation.target().targetPath())
                .containsEntry("lifecycle_state", Lifecycle.ACTIVE.name())
                .containsEntry("storage_reference", mutation.target().resultBlobBinding())
                .containsEntry("content_digest", mutation.target().resultContentDigest())
                .containsEntry("version_token", mutation.target().resultFileVersion());
        Map<String, Object> change = fixture.jdbc().queryForMap("""
            select revision, operation_ref, file_ref, change_kind, range_start, range_end,
                   resulting_content_digest, resulting_file_version, resulting_etag
              from weave_files_changes
             where organization_ref = ? and space_ref = ?
            """,
                mutation.intent().organizationRef(),
                mutation.plan().spaceRef());
        assertThat(change)
                .containsEntry("revision", 1L)
                .containsEntry("operation_ref", mutation.intent().operationRef())
                .containsEntry("file_ref", mutation.target().targetFileRef())
                .containsEntry("change_kind", mutation.target().changeKind().name())
                .containsEntry("range_start", 1L)
                .containsEntry("range_end", 1L)
                .containsEntry("resulting_content_digest", mutation.target().resultContentDigest())
                .containsEntry("resulting_file_version", mutation.target().resultFileVersion())
                .containsEntry("resulting_etag", mutation.target().resultStrongEtag());
        assertThat(fixture.jdbc().queryForObject(
                "select latest_revision from weave_files_stream_heads "
                        + "where organization_ref = ? and space_ref = ?",
                Long.class,
                mutation.intent().organizationRef(),
                mutation.plan().spaceRef())).isEqualTo(1);
        assertThat(fixture.jdbc().queryForMap("""
            select intent_state, result_digest, audit_ref
              from weave_operation_intents
             where operation_ref = ?
            """, mutation.intent().operationRef()))
                .containsEntry("intent_state", State.SUCCEEDED.name())
                .containsEntry("result_digest", DIGEST_C)
                .containsEntry("audit_ref", "audit:files:tx2");
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_operation_outbox where operation_ref = ?",
                Integer.class,
                mutation.intent().operationRef())).isEqualTo(1);
        assertThat(fixture.jdbc().queryForObject(
                "select outbox_ref from weave_operation_outbox where operation_ref = ?",
                String.class,
                mutation.intent().operationRef())).isEqualTo(mutation.intent().outboxRef());
        assertThat(fixture.jdbc().queryForObject(
                "select event_type from weave_operation_outbox where operation_ref = ?",
                String.class,
                mutation.intent().operationRef())).isEqualTo("operation.succeeded");
    }

    @Test
    void nativeMutationRetryReturnsTheCommittedRangeWithoutDuplicateEffects() {
        MutationFixture fixture = mutationFixture("retry");
        PlannedMutation mutation = plannedFile(fixture, "retry", "/retry.txt");
        var begun = fixture.mutations().begin(mutation.intent(), mutation.plan());
        FinalizationResult first = fixture.mutations().finalizeSuccess(
                begun.intent(), mutation.plan(), DIGEST_C, "audit:files:retry", null);

        FinalizationResult retry = fixture.mutations().finalizeSuccess(
                begun.intent(), mutation.plan(), DIGEST_C, "audit:files:retry", null);

        assertThat(retry).isEqualTo(first);
        assertThat(fixture.jdbc().queryForObject(
                "select latest_revision from weave_files_stream_heads",
                Long.class)).isEqualTo(1);
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_files_changes",
                Integer.class)).isEqualTo(1);
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_files_objects",
                Integer.class)).isEqualTo(1);
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_operation_outbox",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void nativeMutationReplayUsesImmutableCommitEvidenceAfterLaterPutMoveAndDelete() {
        MutationFixture fixture = mutationFixture("replay-after-later-commits");
        FilePath originalPath = new FilePath("/replay-history.txt");
        FilePath movedPath = new FilePath("/replay-history-moved.txt");
        PlannedMutation original = plannedFile(
                fixture,
                "replay-history-original",
                originalPath.value());
        var originalBegin = fixture.mutations().begin(original.intent(), original.plan());
        FinalizationResult originalResult = fixture.mutations().finalizeSuccess(
                originalBegin.intent(),
                original.plan(),
                DIGEST_C,
                "audit:files:replay-history-original",
                null);

        PlannedMutation laterPut = plannedPutFromCurrent(
                fixture,
                "replay-history-put",
                originalPath,
                new byte[] {1, 2, 3, 4, 5});
        finalizePlanned(fixture, laterPut, "audit:files:replay-history-put");

        PlannedMutation laterMove = plannedMoveFromCurrent(
                fixture,
                "replay-history-move",
                originalPath,
                movedPath);
        finalizePlanned(fixture, laterMove, "audit:files:replay-history-move");

        PlannedMutation laterDelete = plannedDeleteFromCurrent(
                fixture,
                "replay-history-delete",
                movedPath);
        finalizePlanned(fixture, laterDelete, "audit:files:replay-history-delete");

        assertThat(fixture.mutations().probe(original.intent().operationRef()).outcome())
                .isEqualTo(CommitOutcome.SUCCEEDED);
        FinalizationResult replay = fixture.mutations().finalizeSuccess(
                originalBegin.intent(),
                original.plan(),
                DIGEST_C,
                "audit:files:replay-history-original",
                null);

        assertThat(replay).isEqualTo(originalResult);
        assertThat(fixture.jdbc().queryForObject(
                "select latest_revision from weave_files_stream_heads",
                Long.class)).isEqualTo(4);
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_files_changes",
                Integer.class)).isEqualTo(4);
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_operation_outbox",
                Integer.class)).isEqualTo(4);
        assertThat(fixture.jdbc().queryForObject("""
                select lifecycle_state
                  from weave_files_objects
                 where organization_ref = ? and space_ref = ? and file_id = ?
                """,
                String.class,
                original.intent().organizationRef(),
                original.plan().spaceRef(),
                original.target().targetFileRef())).isEqualTo(Lifecycle.TOMBSTONED.name());
    }

    @Test
    void nativeMutationProbeFailsClosedOnAnImpossiblePartialCommittedEffect() {
        MutationFixture fixture = mutationFixture("corrupt-probe");
        PlannedMutation mutation = plannedFile(fixture, "corrupt-probe", "/corrupt-probe.txt");
        var begun = fixture.mutations().begin(mutation.intent(), mutation.plan());
        fixture.mutations().finalizeSuccess(
                begun.intent(), mutation.plan(), DIGEST_C, "audit:files:corrupt-probe", null);

        fixture.jdbc().execute(
                "alter table weave_files_changes disable trigger trg_weave_files_v7_change_immutable");
        try {
            fixture.jdbc().update(
                    "delete from weave_files_changes where operation_ref = ?",
                    mutation.intent().operationRef());
        } finally {
            fixture.jdbc().execute(
                    "alter table weave_files_changes enable trigger trg_weave_files_v7_change_immutable");
        }

        var probe = fixture.mutations().probe(mutation.intent().operationRef());

        assertThat(probe.outcome()).isEqualTo(CommitOutcome.CORRUPT);
        assertThat(probe.intent().state()).isEqualTo(State.SUCCEEDED);
        assertThat(probe.rangeStart()).isNull();
        assertThat(probe.rangeEnd()).isNull();
    }

    @Test
    void nativeMutationConflictRollsBackEveryTx2Effect() {
        MutationFixture fixture = mutationFixture("conflict");
        PlannedMutation mutation = plannedFile(fixture, "conflict", "/conflict.txt");
        var begun = fixture.mutations().begin(mutation.intent(), mutation.plan());
        StoredFileRecord occupant = activeFile(
                mutation.intent().organizationRef(),
                mutation.plan().spaceRef(),
                new FileId("file:occupant:conflict"),
                new FilePath(mutation.target().targetPath()),
                "f",
                MUTATION_RESULT_AT.plusSeconds(1));
        fixture.authority().save(occupant);

        assertThatThrownBy(() -> fixture.mutations().finalizeSuccess(
                begun.intent(), mutation.plan(), DIGEST_C, "audit:files:conflict", null))
                .isInstanceOf(JpaFilesMutationRepository.ConcurrentFilesMutationException.class)
                .hasMessageContaining(mutation.intent().operationRef());

        assertThat(fixture.jdbc().queryForObject(
                "select latest_revision from weave_files_stream_heads",
                Long.class)).isZero();
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_files_changes",
                Integer.class)).isZero();
        assertThat(fixture.jdbc().queryForObject(
                "select intent_state from weave_operation_intents where operation_ref = ?",
                String.class,
                mutation.intent().operationRef())).isEqualTo(State.CREATED.name());
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_operation_outbox",
                Integer.class)).isZero();
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_files_objects where file_id = ?",
                Integer.class,
                mutation.target().targetFileRef())).isZero();
        assertThat(persistedLifecycle(fixture.jdbc(), occupant)).isEqualTo(Lifecycle.ACTIVE.name());
        assertThat(persistedBinding(fixture.jdbc(), occupant))
                .isEqualTo(occupant.blobBinding().opaqueReference());
    }

    @Test
    void persistedIfNoneMatchStarRaceReturnsPreconditionFailedUnderHeadLock() {
        MutationFixture fixture = mutationFixture("if-none-match-race");
        PlannedMutation base = plannedFile(fixture, "if-none-match-race", "/if-none-match-race.txt");
        Draft draft = new Draft(
                base.plan().operationRef(),
                base.plan().organizationRef(),
                base.plan().spaceRef(),
                base.plan().canonicalArgumentsDigest(),
                base.plan().operationKind(),
                base.plan().providerBindingRevision(),
                FilesMutationPlan.EntityTagCondition.notSupplied(),
                FilesMutationPlan.EntityTagCondition.parseHeader("*"),
                false,
                base.plan().targets(),
                base.plan().fences());
        Sealed plan = draft.seal(
                fixture.codec().targetsDigest(draft.targets()),
                fixture.codec().fencesDigest(draft.fences()),
                MUTATION_SEALED_AT);
        PlannedMutation mutation = new PlannedMutation(base.intent(), plan, base.target());
        var begun = fixture.mutations().begin(mutation.intent(), mutation.plan());
        fixture.authority().save(activeFile(
                mutation.intent().organizationRef(),
                mutation.plan().spaceRef(),
                new FileId("file:if-none-match-race:occupant"),
                new FilePath(mutation.target().targetPath()),
                "e",
                MUTATION_RESULT_AT.plusSeconds(1)));

        assertThatThrownBy(() -> fixture.mutations().finalizeSuccess(
                begun.intent(), mutation.plan(), DIGEST_C, "audit:files:if-none-match-race", null))
                .isInstanceOf(JpaFilesMutationRepository.RequestPreconditionException.class);
        assertUncommittedFinalization(fixture, mutation);
    }

    @Test
    void subtreeMembershipFenceDetectsDescendantInsertedAfterTx1() {
        MutationFixture fixture = mutationFixture("subtree-fence-race");
        String rootPath = "/subtree-fence";
        fixture.authority().save(activeCollection(
                "org:files-mutation",
                "space:files-mutation",
                new FileId("collection:subtree-fence"),
                new FilePath(rootPath),
                MUTATION_RESULT_AT));
        fixture.authority().save(activeFile(
                "org:files-mutation",
                "space:files-mutation",
                new FileId("file:subtree-fence:child"),
                new FilePath(rootPath + "/child.txt"),
                "a",
                MUTATION_RESULT_AT));
        String suffix = "subtree-fence-race";
        Draft draft = new CanonicalFilesMutationPlanner(
                fixture.authority(),
                Clock.fixed(MUTATION_RESULT_AT, ZoneOffset.UTC))
                .delete(
                        new CanonicalFilesMutationPlanner.MutationScope(
                                "operation:files-mutation:" + suffix,
                                "org:files-mutation",
                                "space:files-mutation",
                                DIGEST_B,
                                1),
                        new FilePath(rootPath),
                        FileVersion.unknown());
        PlannedMutation base = planned(
                fixture, suffix, FilesMutationPlan.OperationKind.DELETE, draft.targets());
        Sealed plan = draft.seal(
                fixture.codec().targetsDigest(draft.targets()),
                fixture.codec().fencesDigest(draft.fences()),
                MUTATION_SEALED_AT);
        PlannedMutation mutation = new PlannedMutation(base.intent(), plan, draft.targets().getFirst());
        var begun = fixture.mutations().begin(mutation.intent(), mutation.plan());
        fixture.authority().save(activeFile(
                "org:files-mutation",
                "space:files-mutation",
                new FileId("file:subtree-fence:new-child"),
                new FilePath(rootPath + "/new-child.txt"),
                "b",
                MUTATION_RESULT_AT.plusSeconds(1)));

        assertThatThrownBy(() -> fixture.mutations().finalizeSuccess(
                begun.intent(), mutation.plan(), DIGEST_C, "audit:files:subtree-fence", null))
                .isInstanceOf(JpaFilesMutationRepository.ConcurrentFilesMutationException.class);
        assertThat(fixture.jdbc().queryForObject(
                "select latest_revision from weave_files_stream_heads",
                Long.class)).isZero();
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_files_changes",
                Integer.class)).isZero();
        assertThat(fixture.jdbc().queryForObject(
                "select intent_state from weave_operation_intents where operation_ref = ?",
                String.class,
                mutation.intent().operationRef())).isEqualTo(State.CREATED.name());
    }

    @Test
    void sealedFenceRowsAreImmutableInPostgresql() {
        MutationFixture fixture = mutationFixture("fence-immutable");
        PlannedMutation mutation = plannedFile(fixture, "fence-immutable", "/fence-immutable.txt");
        fixture.mutations().begin(mutation.intent(), mutation.plan());

        assertThatThrownBy(() -> fixture.jdbc().update(
                "update weave_files_mutation_fences set canonical_path = '/altered' where operation_ref = ?",
                mutation.intent().operationRef()))
                .isInstanceOf(org.springframework.dao.DataAccessException.class)
                .hasMessageContaining("Files mutation fences are insert-only");
        assertThatThrownBy(() -> fixture.jdbc().update("""
                insert into weave_files_mutation_fences (
                    operation_ref, fence_ordinal, fence_version, fence_role, canonical_path,
                    expected_presence, snapshot_digest)
                values (?, 1, 'weave.files-mutation-fence/v1', 'REQUEST_TARGET',
                        '/extra', 'ABSENT', ?)
                """, mutation.intent().operationRef(), DIGEST_A))
                .isInstanceOf(org.springframework.dao.DataAccessException.class)
                .hasMessageContaining("Files mutation fences require one OPEN plan");
    }

    @Test
    void nativeMutationRechecksAuthorizationUnderTheStreamHeadLock() {
        AtomicBoolean allowed = new AtomicBoolean(true);
        MutationFixture fixture = mutationFixture(
                "authorization-revoked",
                (intent, spaceRef) -> allowed.get());
        PlannedMutation mutation = plannedFile(
                fixture, "authorization-revoked", "/authorization-revoked.txt");
        var begun = fixture.mutations().begin(mutation.intent(), mutation.plan());
        allowed.set(false);

        assertThatThrownBy(() -> fixture.mutations().finalizeSuccess(
                begun.intent(), mutation.plan(), DIGEST_C, "audit:files:authorization", null))
                .isInstanceOf(JpaFilesMutationRepository.AuthorizationDeniedException.class);
        assertUncommittedFinalization(fixture, mutation);
    }

    @Test
    void nativeMutationRechecksLocksAcquiredAfterPlanning() {
        MutationFixture fixture = mutationFixture("lock-interleaving");
        PlannedMutation mutation = plannedFile(
                fixture, "lock-interleaving", "/lock-interleaving.txt");
        var begun = fixture.mutations().begin(mutation.intent(), mutation.plan());
        new FilesLockService(
                fixture.authority(),
                Clock.fixed(MUTATION_COMMITTED_AT.minusSeconds(1), ZoneOffset.UTC))
                .acquire(
                        mutation.intent().organizationRef(),
                        mutation.plan().spaceRef(),
                        new FilePath(mutation.target().targetPath()),
                        "person:bob",
                        Duration.ofMinutes(30));

        assertThatThrownBy(() -> fixture.mutations().finalizeSuccess(
                begun.intent(), mutation.plan(), DIGEST_C, "audit:files:lock", null))
                .isInstanceOf(JpaFilesMutationRepository.LockPreconditionException.class);
        assertUncommittedFinalization(fixture, mutation);
    }

    private void assertUncommittedFinalization(
            MutationFixture fixture,
            PlannedMutation mutation) {
        assertThat(fixture.jdbc().queryForObject(
                "select latest_revision from weave_files_stream_heads",
                Long.class)).isZero();
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_files_changes",
                Integer.class)).isZero();
        assertThat(fixture.jdbc().queryForObject(
                "select intent_state from weave_operation_intents where operation_ref = ?",
                String.class,
                mutation.intent().operationRef())).isEqualTo(State.CREATED.name());
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_operation_outbox",
                Integer.class)).isZero();
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_files_objects where file_id = ?",
                Integer.class,
                mutation.target().targetFileRef())).isZero();
    }

    @Test
    void concurrentNativeMutationFinalizersReserveGapFreeNonInterleavedRanges() throws Exception {
        MutationFixture fixture = mutationFixture("concurrent");
        PlannedMutation first = plannedCollections(fixture, "concurrent-a", "/concurrent-a");
        PlannedMutation second = plannedCollections(fixture, "concurrent-b", "/concurrent-b");
        var firstBegun = fixture.mutations().begin(first.intent(), first.plan());
        var secondBegun = fixture.mutations().begin(second.intent(), second.plan());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        List<FinalizationResult> ranges;
        try {
            var firstResult = executor.submit(() -> finalizeAfterGate(
                    fixture, firstBegun.intent(), first.plan(), "audit:files:concurrent-a", ready, start));
            var secondResult = executor.submit(() -> finalizeAfterGate(
                    fixture, secondBegun.intent(), second.plan(), "audit:files:concurrent-b", ready, start));
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            ranges = List.of(
                    firstResult.get(1, TimeUnit.MINUTES),
                    secondResult.get(1, TimeUnit.MINUTES)).stream()
                    .sorted(Comparator.comparingLong(FinalizationResult::rangeStart))
                    .toList();
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(ranges)
                .extracting(FinalizationResult::rangeStart, FinalizationResult::rangeEnd)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1L, 2L),
                        org.assertj.core.groups.Tuple.tuple(3L, 4L));
        assertThat(fixture.jdbc().queryForList(
                "select revision from weave_files_changes order by revision",
                Long.class)).containsExactly(1L, 2L, 3L, 4L);
        assertThat(fixture.jdbc().queryForObject(
                "select latest_revision from weave_files_stream_heads",
                Long.class)).isEqualTo(4);
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_operation_intents where intent_state = 'SUCCEEDED'",
                Integer.class)).isEqualTo(2);
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_operation_outbox",
                Integer.class)).isEqualTo(2);
    }

    @Test
    void nativeChangeRepositoryReadsOnlyTheCapturedHighWaterInRevisionOrder() {
        MutationFixture fixture = mutationFixture("change-reader");
        PlannedMutation first = plannedCollections(fixture, "reader-a", "/reader-a");
        PlannedMutation second = plannedCollections(fixture, "reader-b", "/reader-b");
        var firstBegun = fixture.mutations().begin(first.intent(), first.plan());
        fixture.mutations().finalizeSuccess(
                firstBegun.intent(), first.plan(), DIGEST_C, "audit:files:reader-a", null);
        long capturedHighWater = fixture.changeRepository()
                .findHead(first.intent().organizationRef(), first.plan().spaceRef())
                .orElseThrow()
                .latestRevision();

        var secondBegun = fixture.mutations().begin(second.intent(), second.plan());
        fixture.mutations().finalizeSuccess(
                secondBegun.intent(), second.plan(), DIGEST_C, "audit:files:reader-b", null);

        assertThat(capturedHighWater).isEqualTo(2);
        assertThat(fixture.changeRepository().findChanges(
                        first.intent().organizationRef(),
                        first.plan().spaceRef(),
                        0,
                        capturedHighWater,
                        100))
                .extracting(change -> change.revision())
                .containsExactly(1L, 2L);
        assertThat(fixture.changeRepository().findChanges(
                        first.intent().organizationRef(),
                        first.plan().spaceRef(),
                        capturedHighWater,
                        4,
                        100))
                .extracting(change -> change.revision())
                .containsExactly(3L, 4L);
        assertThat(fixture.changeRepository().findChanges(
                "org:other", first.plan().spaceRef(), 0, 4, 100)).isEmpty();
    }

    @Test
    void nonterminalAndUncleanedTerminalPlansProtectBindingsUntilEveryDispositionExists() {
        MutationFixture fixture = mutationFixture("blob_protection");
        PlannedMutation created = plannedCopy(fixture, "protected-created");
        PlannedMutation reconciling = plannedCopy(fixture, "protected-reconciling");
        PlannedMutation terminal = plannedCopy(fixture, "unprotected-terminal");
        fixture.mutations().begin(created.intent(), created.plan());
        var reconcilingBegin = fixture.mutations().begin(reconciling.intent(), reconciling.plan());
        fixture.jdbc().update("""
                update weave_operation_intents
                   set intent_state = 'RECONCILING',
                       provider_correlation_hash = ?,
                       reconciliation_attempts = 1,
                       reconciliation_outcome = 'PENDING',
                       reconciliation_last_attempt_at_utc = ?,
                       updated_at_utc = ?
                 where operation_ref = ?
                """,
                DIGEST_A,
                MUTATION_COMMITTED_AT.atOffset(ZoneOffset.UTC),
                MUTATION_COMMITTED_AT.atOffset(ZoneOffset.UTC),
                reconcilingBegin.intent().operationRef());
        var terminalBegin = fixture.mutations().begin(terminal.intent(), terminal.plan());
        OperationIntent failed = fixture.mutations().recordFailure(
                terminalBegin.intent(),
                false,
                DIGEST_C,
                "audit:files:blob-protection");

        var protectedBindings = fixture.mutations().protectedBindings(
                new FilesScope("org:files-mutation", "space:files-mutation"));

        assertThat(fixture.jdbc().queryForObject(
                "select intent_state from weave_operation_intents where operation_ref = ?",
                String.class,
                reconcilingBegin.intent().operationRef()))
                .isEqualTo(State.RECONCILING.name());
        assertThat(failed.state()).isEqualTo(State.FAILED);
        assertThat(protectedBindings).containsExactlyInAnyOrder(
                binding(created.target().sourceReadBlobBinding()),
                binding(created.target().resultBlobBinding()),
                binding(reconciling.target().sourceReadBlobBinding()),
                binding(reconciling.target().resultBlobBinding()),
                binding(terminal.target().sourceReadBlobBinding()),
                binding(terminal.target().resultBlobBinding()));

        recordCleanupDisposition(
                fixture,
                terminal,
                terminal.target().sourceReadBlobBinding(),
                "ALREADY_ABSENT");
        assertThat(fixture.mutations().protectedBindings(
                        new FilesScope("org:files-mutation", "space:files-mutation")))
                .contains(
                        binding(terminal.target().sourceReadBlobBinding()),
                        binding(terminal.target().resultBlobBinding()));

        recordCleanupDisposition(
                fixture,
                terminal,
                terminal.target().resultBlobBinding(),
                "ALREADY_ABSENT");
        assertThat(fixture.mutations().protectedBindings(
                        new FilesScope("org:files-mutation", "space:files-mutation")))
                .doesNotContain(
                        binding(terminal.target().sourceReadBlobBinding()),
                        binding(terminal.target().resultBlobBinding()));
        assertThat(fixture.mutations().protectedBindings(
                new FilesScope("org:files-mutation", "space:other")))
                .isEmpty();
    }

    @Test
    void tx1PlanCreationSerializesCleanupBeforeTheSharedBindingCanBeDeleted() throws Exception {
        MutationFixture fixture = mutationFixture("cleanup_tx1_serialization");
        String sharedBinding = "v1/cleanup/shared-binding";
        PlannedMutation failed = plannedFileWithBinding(
                fixture,
                "cleanup-failed",
                "/cleanup-failed.txt",
                sharedBinding);
        var failedBegin = fixture.mutations().begin(failed.intent(), failed.plan());
        fixture.mutations().recordFailure(
                failedBegin.intent(),
                false,
                DIGEST_C,
                "audit:files:cleanup-failed");

        PlannedMutation protecting = plannedFileWithBinding(
                fixture,
                "cleanup-protecting",
                "/cleanup-protecting.txt",
                sharedBinding);
        installTx1IntentInsertBarrier(fixture.jdbc());
        TrackingBlobStore blobs = new TrackingBlobStore(
                new BlobReference(sharedBinding),
                new BlobReceipt(new BlobReference(sharedBinding), DIGEST_C, 4));
        JpaNativeFilesBlobCleanupRepository cleanupRepository = cleanupRepository(fixture.dataSource());
        NativeFilesBlobCleanupCoordinator cleanup = new NativeFilesBlobCleanupCoordinator(
                cleanupRepository,
                blobs);
        TransactionTemplate cleanupTransaction = new TransactionTemplate(
                JpaTestDatabase.transactionManager(fixture.dataSource()));

        long barrierKey = 1_326_001L;
        var executor = Executors.newFixedThreadPool(2);
        try (Connection barrier = fixture.dataSource().getConnection()) {
            barrier.createStatement().execute("select pg_advisory_lock(" + barrierKey + ")");
            boolean barrierHeld = true;
            try {
                var beginFuture = executor.submit(() -> fixture.mutations().begin(
                        protecting.intent(), protecting.plan()));
                awaitDatabaseCondition(
                        fixture.jdbc(),
                        "Tx1 did not reach the intent-insert barrier while holding the scope head",
                        """
                        select exists (
                            select 1
                              from pg_stat_activity
                             where datname = current_database()
                               and pid <> pg_backend_pid()
                               and wait_event_type = 'Lock'
                               and wait_event = 'advisory'
                               and query ilike '%weave_operation_intents%'
                        )
                        """);

                var cleanupFuture = executor.submit(() -> cleanupTransaction.execute(status ->
                        cleanup.process(failed.intent().operationRef(), 100)));
                awaitDatabaseCondition(
                        fixture.jdbc(),
                        "cleanup did not block on the Tx1 scope-head lock",
                        """
                        select exists (
                            select 1
                              from pg_stat_activity
                             where datname = current_database()
                               and pid <> pg_backend_pid()
                               and cardinality(pg_blocking_pids(pid)) > 0
                               and query ilike '%weave_files_stream_heads%'
                        )
                        """);

                assertThat(cleanupFuture).isNotDone();
                assertThat(fixture.jdbc().queryForObject(
                        "select count(*) from weave_files_blob_cleanup_dispositions",
                        Integer.class)).isZero();
                assertThat(blobs.present()).isTrue();
                assertThat(blobs.deleteCalls()).isZero();

                barrier.createStatement().execute("select pg_advisory_unlock(" + barrierKey + ")");
                barrierHeld = false;
                assertThat(beginFuture.get(1, TimeUnit.MINUTES).created()).isTrue();
                CleanupResult result = cleanupFuture.get(1, TimeUnit.MINUTES);

                assertThat(result.complete()).isTrue();
                assertThat(result.stillProtectedCount()).isEqualTo(1);
                assertThat(result.deletedCount()).isZero();
                assertThat(blobs.present()).isTrue();
                assertThat(blobs.receiptCalls()).isZero();
                assertThat(blobs.deleteCalls()).isZero();
            } finally {
                if (barrierHeld) {
                    barrier.createStatement().execute("select pg_advisory_unlock(" + barrierKey + ")");
                }
            }
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(fixture.jdbc().queryForObject(
                """
                select disposition
                  from weave_files_blob_cleanup_dispositions
                 where operation_ref = ?
                   and private_blob_binding = ?
                """,
                String.class,
                failed.intent().operationRef(),
                sharedBinding)).isEqualTo("STILL_PROTECTED");
        assertThat(fixture.jdbc().queryForObject(
                "select intent_state from weave_operation_intents where operation_ref = ?",
                String.class,
                protecting.intent().operationRef())).isEqualTo(State.CREATED.name());
    }

    private void recordCleanupDisposition(
            MutationFixture fixture,
            PlannedMutation mutation,
            String privateBinding,
            String disposition) {
        fixture.jdbc().update(
                """
                insert into weave_files_blob_cleanup_dispositions (
                    operation_ref, binding_digest, disposition_version,
                    private_blob_binding, disposition, recorded_at_utc)
                values (?, ?, 'weave.files-blob-cleanup-disposition/v1', ?, ?, ?)
                """,
                mutation.intent().operationRef(),
                FilesMutationIntentService.digest(privateBinding),
                privateBinding,
                disposition,
                MUTATION_COMMITTED_AT.atOffset(ZoneOffset.UTC));
    }

    private void installTx1IntentInsertBarrier(JdbcTemplate jdbc) {
        jdbc.execute("""
                create function test_block_tx1_intent_insert()
                returns trigger
                language plpgsql
                as $function$
                begin
                    perform pg_advisory_xact_lock(1326001);
                    return new;
                end;
                $function$
                """);
        jdbc.execute("""
                create trigger test_block_tx1_intent_insert
                before insert on weave_operation_intents
                for each row execute function test_block_tx1_intent_insert()
                """);
    }

    private void awaitDatabaseCondition(
            JdbcTemplate jdbc,
            String failureMessage,
            String query) throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(30);
        while (Instant.now().isBefore(deadline)) {
            if (Boolean.TRUE.equals(jdbc.queryForObject(query, Boolean.class))) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError(failureMessage);
    }

    private JpaNativeFilesBlobCleanupRepository cleanupRepository(
            DriverManagerDataSource dataSource) {
        return new JpaNativeFilesBlobCleanupRepository(
                JpaTestDatabase.repository(dataSource, FilesStreamHeadJpaRepository.class),
                JpaTestDatabase.repository(dataSource, FilesMutationPlanJpaRepository.class),
                JpaTestDatabase.repository(dataSource, FilesMutationTargetJpaRepository.class),
                JpaTestDatabase.repository(dataSource, FilesMutationFenceJpaRepository.class),
                JpaTestDatabase.repository(dataSource, FilesChangeJpaRepository.class),
                JpaTestDatabase.repository(
                        dataSource,
                        FilesBlobCleanupDispositionJpaRepository.class),
                OperationIntentJpaTestFactory.create(dataSource),
                new FilesMutationTargetCodec(
                        JsonMapper.builder().findAndAddModules().build()));
    }

    private MutationFixture mutationFixture(String semanticName) {
        return mutationFixture(semanticName, (intent, spaceRef) -> true);
    }

    private MutationFixture mutationFixture(
            String semanticName,
            com.massimotter.weave.backend.files.application.NativeFilesFinalizationAuthorization authorization) {
        DriverManagerDataSource dataSource = migratedDataSource(semanticName);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        var files = JpaTestDatabase.repository(dataSource, FileObjectJpaRepository.class);
        JpaFilesAuthorityRepository authority = JpaTestDatabase.transactional(
                dataSource,
                new JpaFilesAuthorityRepository(
                        files,
                        JpaTestDatabase.repository(dataSource, FileLockJpaRepository.class)));
        var operations = OperationIntentJpaTestFactory.create(dataSource);
        var intentService = new OperationIntentService(
                operations,
                Clock.fixed(MUTATION_COMMITTED_AT, ZoneOffset.UTC));
        var codec = new FilesMutationTargetCodec(
                JsonMapper.builder().findAndAddModules().build());
        var heads = JpaTestDatabase.repository(dataSource, FilesStreamHeadJpaRepository.class);
        var changes = JpaTestDatabase.repository(dataSource, FilesChangeJpaRepository.class);
        var locks = JpaTestDatabase.repository(dataSource, FileLockJpaRepository.class);
        var mutations = new JpaFilesMutationRepository(
                heads,
                JpaTestDatabase.repository(dataSource, FilesMutationPlanJpaRepository.class),
                JpaTestDatabase.repository(dataSource, FilesMutationTargetJpaRepository.class),
                JpaTestDatabase.repository(dataSource, FilesMutationFenceJpaRepository.class),
                changes,
                files,
                locks,
                authority,
                operations,
                intentService,
                codec,
                authorization,
                JpaTestDatabase.transactionManager(dataSource),
                Clock.fixed(MUTATION_COMMITTED_AT, ZoneOffset.UTC));
        mutations.provisionScope(
                new FilesScope("org:files-mutation", "space:files-mutation"),
                MUTATION_CREATED_AT);
        return new MutationFixture(
                jdbc,
                mutations,
                authority,
                codec,
                new JpaNativeFilesChangeRepository(heads, changes),
                dataSource);
    }

    private PlannedMutation plannedCopy(
            MutationFixture fixture,
            String suffix) {
        String sourceRef = "file:mutation:" + suffix + ":source";
        String targetRef = "file:mutation:" + suffix + ":result";
        String sourcePath = "/" + suffix + "-source.txt";
        String targetPath = "/" + suffix + "-result.txt";
        String sourceVersion = "version:mutation:" + suffix + ":source";
        String resultVersion = "version:mutation:" + suffix + ":result";
        FileObject source = new FileObject(
                new FileId(sourceRef),
                new FilePath(sourcePath),
                Kind.FILE,
                4,
                "text/plain",
                MUTATION_RESULT_AT,
                false);
        FileObject result = new FileObject(
                new FileId(targetRef),
                new FilePath(targetPath),
                Kind.FILE,
                4,
                "text/plain",
                MUTATION_RESULT_AT,
                false);
        Target target = new Target(
                0,
                ChangeKind.COPIED,
                sourceRef,
                targetRef,
                sourcePath,
                targetPath,
                Kind.FILE,
                Lifecycle.ACTIVE,
                "v1/plans/" + suffix + "/source",
                source.size(),
                source.mediaType(),
                DIGEST_A,
                sourceVersion,
                FilesEtags.strong(source, new FileVersion(sourceVersion)),
                source.modifiedAt(),
                source.hidden(),
                MUTATION_RESULT_AT,
                Lifecycle.ACTIVE,
                "v1/plans/" + suffix + "/result",
                result.size(),
                result.mediaType(),
                DIGEST_C,
                resultVersion,
                FilesEtags.strong(result, new FileVersion(resultVersion)),
                result.modifiedAt(),
                result.hidden(),
                MUTATION_RESULT_AT);
        return planned(fixture, suffix, FilesMutationPlan.OperationKind.COPY, List.of(target));
    }

    private PlannedMutation plannedFile(
            MutationFixture fixture,
            String suffix,
            String path) {
        return plannedFileWithBinding(
                fixture,
                suffix,
                path,
                "v1/files/" + suffix);
    }

    private PlannedMutation plannedFileWithBinding(
            MutationFixture fixture,
            String suffix,
            String path,
            String resultBinding) {
        String fileRef = "file:mutation:" + suffix;
        String fileVersion = "version:mutation:" + suffix;
        FileObject result = new FileObject(
                new FileId(fileRef),
                new FilePath(path),
                Kind.FILE,
                4,
                "text/plain",
                MUTATION_RESULT_AT,
                false);
        Target target = new Target(
                0,
                ChangeKind.CREATED,
                null,
                fileRef,
                null,
                path,
                Kind.FILE,
                Lifecycle.ACTIVE,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                resultBinding,
                result.size(),
                result.mediaType(),
                DIGEST_C,
                fileVersion,
                FilesEtags.strong(result, new FileVersion(fileVersion)),
                result.modifiedAt(),
                result.hidden(),
                MUTATION_RESULT_AT);
        return planned(fixture, suffix, FilesMutationPlan.OperationKind.PUT, List.of(target));
    }

    private PlannedMutation plannedPutFromCurrent(
            MutationFixture fixture,
            String suffix,
            FilePath path,
            byte[] content) {
        OperationIntent intent = mutationIntent(
                suffix,
                FilesMutationPlan.OperationKind.PUT,
                List.of(pathRef(path.value()), "lock-token:none"));
        Draft draft = mutationPlanner(fixture).put(
                mutationScope(intent),
                path,
                new ReplayableFileContent(
                        content.length,
                        FilesDigests.sha256(content),
                        "text/plain",
                        () -> new java.io.ByteArrayInputStream(content)));
        return planned(fixture, intent, draft);
    }

    private PlannedMutation plannedMoveFromCurrent(
            MutationFixture fixture,
            String suffix,
            FilePath source,
            FilePath destination) {
        OperationIntent intent = mutationIntent(
                suffix,
                FilesMutationPlan.OperationKind.MOVE,
                List.of(
                        pathRef(source.value()),
                        pathRef(destination.value()),
                        "lock-token:none"));
        Draft draft = mutationPlanner(fixture).move(
                mutationScope(intent),
                source,
                destination,
                false);
        return planned(fixture, intent, draft);
    }

    private PlannedMutation plannedDeleteFromCurrent(
            MutationFixture fixture,
            String suffix,
            FilePath path) {
        OperationIntent intent = mutationIntent(
                suffix,
                FilesMutationPlan.OperationKind.DELETE,
                List.of(pathRef(path.value()), "lock-token:none"));
        Draft draft = mutationPlanner(fixture).delete(
                mutationScope(intent),
                path,
                null);
        return planned(fixture, intent, draft);
    }

    private CanonicalFilesMutationPlanner mutationPlanner(MutationFixture fixture) {
        return new CanonicalFilesMutationPlanner(
                fixture.authority(),
                Clock.fixed(MUTATION_RESULT_AT, ZoneOffset.UTC));
    }

    private CanonicalFilesMutationPlanner.MutationScope mutationScope(OperationIntent intent) {
        return new CanonicalFilesMutationPlanner.MutationScope(
                intent.operationRef(),
                intent.organizationRef(),
                "space:files-mutation",
                intent.canonicalArgumentsDigest(),
                intent.providerBindingRevision());
    }

    private PlannedMutation planned(
            MutationFixture fixture,
            OperationIntent intent,
            Draft draft) {
        Sealed plan = draft.seal(
                fixture.codec().targetsDigest(draft.targets()),
                fixture.codec().fencesDigest(draft.fences()),
                MUTATION_SEALED_AT);
        return new PlannedMutation(intent, plan, draft.targets().getFirst());
    }

    private FinalizationResult finalizePlanned(
            MutationFixture fixture,
            PlannedMutation mutation,
            String auditRef) {
        var begun = fixture.mutations().begin(mutation.intent(), mutation.plan());
        return fixture.mutations().finalizeSuccess(
                begun.intent(), mutation.plan(), DIGEST_C, auditRef, null);
    }

    private PlannedMutation plannedCollections(
            MutationFixture fixture,
            String suffix,
            String root) {
        Target rootTarget = collectionTarget(0, "file:mutation:" + suffix + ":root", root);
        Target childTarget = collectionTarget(
                1,
                "file:mutation:" + suffix + ":child",
                root + "/child");
        return planned(
                fixture,
                suffix,
                FilesMutationPlan.OperationKind.MKCOL,
                List.of(rootTarget, childTarget));
    }

    private Target collectionTarget(int ordinal, String fileRef, String path) {
        return new Target(
                ordinal,
                ChangeKind.CREATED,
                null,
                fileRef,
                null,
                path,
                Kind.COLLECTION,
                Lifecycle.ACTIVE,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                null,
                null,
                null,
                null,
                MUTATION_RESULT_AT,
                false,
                MUTATION_RESULT_AT);
    }

    private PlannedMutation planned(
            MutationFixture fixture,
            String suffix,
            FilesMutationPlan.OperationKind operationKind,
            List<Target> targets) {
        OperationIntent intent = mutationIntent(
                suffix,
                operationKind,
                mutationObjectRefs(operationKind, targets));
        Draft draft = new Draft(
                intent.operationRef(),
                intent.organizationRef(),
                "space:files-mutation",
                intent.canonicalArgumentsDigest(),
                operationKind,
                intent.providerBindingRevision(),
                FilesMutationPlan.EntityTagCondition.notSupplied(),
                FilesMutationPlan.EntityTagCondition.notSupplied(),
                false,
                targets,
                mutationFences(operationKind, targets));
        return planned(fixture, intent, draft);
    }

    private OperationIntent mutationIntent(
            String suffix,
            FilesMutationPlan.OperationKind operationKind,
            List<String> objectRefs) {
        return new OperationIntent(
                "operation:files-mutation:" + suffix,
                "files-mutation-idempotency-" + suffix,
                "org:files-mutation",
                new HumanActor("person:alice", "subject:alice"),
                "files",
                new ProtocolProjection(
                        "webdav",
                        switch (operationKind) {
                            case PUT -> "webdav-put";
                            case MKCOL -> "webdav-mkcol";
                            case COPY -> "webdav-copy";
                            case MOVE -> "webdav-move";
                            case DELETE -> "webdav-delete";
                        },
                        "weave.webdav.files/v1"),
                DIGEST_A,
                DIGEST_B,
                objectRefs,
                "policy:files-mutation",
                "entitlement:files-mutation",
                1,
                State.CREATED,
                "outbox:files-mutation:" + suffix,
                null,
                null,
                null,
                null,
                MUTATION_CREATED_AT,
                MUTATION_CREATED_AT);
    }

    private List<FilesMutationPlan.Fence> mutationFences(
            FilesMutationPlan.OperationKind operationKind,
            List<Target> targets) {
        Target root = targets.stream()
                .filter(target -> switch (operationKind) {
                    case PUT, MKCOL -> target.targetPath() != null;
                    case DELETE -> target.sourcePath() != null;
                    case COPY -> target.changeKind() == ChangeKind.COPIED;
                    case MOVE -> target.changeKind() == ChangeKind.MOVED;
                })
                .min(Comparator.comparingInt(target -> switch (operationKind) {
                    case PUT, MKCOL -> target.targetPath().length();
                    case DELETE, COPY, MOVE -> target.sourcePath().length();
                }))
                .orElseThrow();
        String requestPath = switch (operationKind) {
            case PUT, MKCOL -> root.targetPath();
            case DELETE, COPY, MOVE -> root.sourcePath();
        };
        boolean requestPresent = operationKind != FilesMutationPlan.OperationKind.MKCOL
                && root.sourceFileRef() != null;
        FilesMutationPlan.Fence request = requestPresent
                ? FilesMutationPlan.Fence.present(
                        0,
                        FilesMutationPlan.FenceRole.REQUEST_TARGET,
                        requestPath,
                        root.sourceFileRef(),
                        root.objectKind(),
                        Lifecycle.ACTIVE,
                        0,
                        root.sourceStrongEtag(),
                        operationKind == FilesMutationPlan.OperationKind.PUT
                                ? null
                                : FilesMutationPlan.subtreeMembershipDigest(List.of(
                                        new FilesMutationPlan.Membership(requestPath, root.sourceFileRef()))))
                : FilesMutationPlan.Fence.absent(
                        0, FilesMutationPlan.FenceRole.REQUEST_TARGET, requestPath);
        if (operationKind != FilesMutationPlan.OperationKind.COPY
                && operationKind != FilesMutationPlan.OperationKind.MOVE) {
            return List.of(request);
        }
        return List.of(
                request,
                FilesMutationPlan.Fence.absent(
                        1,
                        FilesMutationPlan.FenceRole.DESTINATION_TARGET,
                        root.targetPath()));
    }

    private List<String> mutationObjectRefs(
            FilesMutationPlan.OperationKind operationKind,
            List<Target> targets) {
        Target root = targets.stream()
                .filter(target -> switch (operationKind) {
                    case PUT, MKCOL -> target.targetPath() != null;
                    case DELETE -> target.sourcePath() != null;
                    case COPY -> target.changeKind() == ChangeKind.COPIED;
                    case MOVE -> target.changeKind() == ChangeKind.MOVED;
                })
                .min(Comparator.comparingInt(target -> switch (operationKind) {
                    case PUT, MKCOL -> target.targetPath().length();
                    case DELETE, COPY, MOVE -> target.sourcePath().length();
                }))
                .orElseThrow();
        return switch (operationKind) {
            case PUT, MKCOL -> List.of(pathRef(root.targetPath()), "lock-token:none");
            case DELETE -> List.of(pathRef(root.sourcePath()), "lock-token:none");
            case COPY, MOVE -> List.of(
                    pathRef(root.sourcePath()), pathRef(root.targetPath()), "lock-token:none");
        };
    }

    private String pathRef(String path) {
        return "file-path:" + FilesMutationIntentService.digest(path);
    }

    private FinalizationResult finalizeAfterGate(
            MutationFixture fixture,
            OperationIntent intent,
            Sealed plan,
            String auditRef,
            CountDownLatch ready,
            CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(30, TimeUnit.SECONDS)) {
            throw new IllegalStateException("native Files finalization start gate timed out");
        }
        return fixture.mutations().finalizeSuccess(intent, plan, DIGEST_C, auditRef, null);
    }

    private DriverManagerDataSource migratedDataSource(String semanticName) {
        String schema = ("files_mutation_" + semanticName + "_" + UUID.randomUUID())
                .toLowerCase()
                .replace("-", "_");
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("create schema \"" + schema + "\"");
        } catch (java.sql.SQLException failure) {
            throw new IllegalStateException("Files mutation test schema could not be created", failure);
        }
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(POSTGRES.getDriverClassName());
        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        dataSource.setUrl(POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema);
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .cleanDisabled(true)
                .load()
                .migrate();
        JpaTestDatabase.validateSchema(dataSource);
        return dataSource;
    }

    private StoredFileRecord activeFile(
            String organization,
            String space,
            FileId id,
            FilePath path,
            String digestSeed,
            Instant observedAt) {
        String repeated = digestSeed.repeat(64);
        return new StoredFileRecord(
                new CanonicalFileRecord(
                        organization,
                        space,
                        new FileObject(
                                id,
                                path,
                                Kind.FILE,
                                1,
                                "text/plain",
                                observedAt,
                                false),
                        new FileVersion("version-" + digestSeed),
                        "sha256:" + repeated,
                        1,
                        Lifecycle.ACTIVE,
                        observedAt),
                new BlobBinding("v1/activation/" + repeated));
    }

    private StoredFileRecord activeCollection(
            String organization,
            String space,
            FileId id,
            FilePath path,
            Instant observedAt) {
        return new StoredFileRecord(new CanonicalFileRecord(
                organization,
                space,
                new FileObject(id, path, Kind.COLLECTION, 0, null, observedAt, false),
                new FileVersion("version-" + id.value()),
                null,
                1,
                Lifecycle.ACTIVE,
                observedAt), null);
    }

    private StoredFileRecord tombstone(StoredFileRecord current, Instant observedAt) {
        CanonicalFileRecord metadata = current.metadata();
        return new StoredFileRecord(
                new CanonicalFileRecord(
                        metadata.organizationRef(),
                        metadata.spaceRef(),
                        metadata.object(),
                        metadata.version(),
                        metadata.contentDigest(),
                        metadata.providerBindingRevision(),
                        Lifecycle.TOMBSTONED,
                        observedAt),
                current.blobBinding());
    }

    private String persistedBinding(JdbcTemplate jdbc, StoredFileRecord record) {
        return jdbc.queryForObject(
                """
                select storage_reference
                  from weave_files_objects
                 where organization_ref = ?
                   and space_ref = ?
                   and file_id = ?
                """,
                String.class,
                record.metadata().organizationRef(),
                record.metadata().spaceRef(),
                record.metadata().object().id().value());
    }

    private String persistedLifecycle(JdbcTemplate jdbc, StoredFileRecord record) {
        return jdbc.queryForObject(
                """
                select lifecycle_state
                  from weave_files_objects
                 where organization_ref = ?
                   and space_ref = ?
                   and file_id = ?
                """,
                String.class,
                record.metadata().organizationRef(),
                record.metadata().spaceRef(),
                record.metadata().object().id().value());
    }

    private DriverManagerDataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(POSTGRES.getDriverClassName());
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }

    private JpaFilesAuthorityRepository repository(
            DriverManagerDataSource dataSource) {
        return JpaTestDatabase.transactional(
                dataSource,
                new JpaFilesAuthorityRepository(
                        JpaTestDatabase.repository(
                                dataSource,
                                FileObjectJpaRepository.class),
                        JpaTestDatabase.repository(
                                dataSource,
                                FileLockJpaRepository.class)));
    }

    private record MutationFixture(
            JdbcTemplate jdbc,
            JpaFilesMutationRepository mutations,
            JpaFilesAuthorityRepository authority,
            FilesMutationTargetCodec codec,
            JpaNativeFilesChangeRepository changeRepository,
            DriverManagerDataSource dataSource) {
    }

    private record PlannedMutation(
            OperationIntent intent,
            Sealed plan,
            Target target) {
    }

    private BlobReference binding(String value) {
        return new BlobReference(value);
    }

    private static final class TrackingBlobStore implements BlobStorePort {
        private final BlobReference reference;
        private final BlobReceipt receipt;
        private final AtomicBoolean present = new AtomicBoolean(true);
        private final AtomicInteger receiptCalls = new AtomicInteger();
        private final AtomicInteger deleteCalls = new AtomicInteger();

        private TrackingBlobStore(BlobReference reference, BlobReceipt receipt) {
            this.reference = reference;
            this.receipt = receipt;
        }

        @Override
        public boolean configured() {
            return true;
        }

        @Override
        public BlobReceipt putStream(
                BlobScope scope,
                BlobReference requested,
                InputStream source,
                long expectedSize,
                String expectedDigest) {
            throw new UnsupportedOperationException("cleanup does not write blobs");
        }

        @Override
        public void readStream(
                BlobScope scope,
                BlobReference requested,
                OutputStream target) {
            throw new UnsupportedOperationException("cleanup does not read blob bytes");
        }

        @Override
        public Optional<BlobReceipt> receipt(
                BlobScope scope,
                BlobReference requested) {
            receiptCalls.incrementAndGet();
            return present.get() && reference.equals(requested)
                    ? Optional.of(receipt)
                    : Optional.empty();
        }

        @Override
        public void delete(BlobScope scope, BlobReference requested) {
            if (reference.equals(requested)) {
                deleteCalls.incrementAndGet();
                present.set(false);
            }
        }

        @Override
        public List<BlobReference> inventory(BlobScope scope, int limit) {
            return present.get() ? List.of(reference) : List.of();
        }

        boolean present() {
            return present.get();
        }

        int receiptCalls() {
            return receiptCalls.get();
        }

        int deleteCalls() {
            return deleteCalls.get();
        }
    }
}

package com.massimotter.weave.backend.files.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.config.IdentityInvitationProperties;
import com.massimotter.weave.backend.files.application.FilesMutationIntentService;
import com.massimotter.weave.backend.files.application.NativeFilesLockRepository.LockResult;
import com.massimotter.weave.backend.files.application.NativeFilesLockRepository.LockAuthorizationDeniedException;
import com.massimotter.weave.backend.files.application.NativeFilesLockTokenCodec;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.port.FilesAuthorityRepository.LockConflictException;
import com.massimotter.weave.backend.identity.IdentityOpaqueReferenceCodec;
import com.massimotter.weave.backend.operation.adapter.OperationIntentJpaTestFactory;
import com.massimotter.weave.backend.operation.application.OperationIntentService;
import com.massimotter.weave.backend.operation.application.OperationIntentService.BeginCommand;
import com.massimotter.weave.backend.operation.domain.OperationIntent;
import com.massimotter.weave.backend.operation.domain.OperationIntent.HumanActor;
import com.massimotter.weave.backend.operation.domain.OperationIntent.ProtocolProjection;
import com.massimotter.weave.backend.testing.JpaTestDatabase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@Tag("postgres")
class JpaNativeFilesLockRepositoryPostgresTest {

    private static final String ORGANIZATION = "org:native-lock";
    private static final String SPACE = "space:native-lock";
    private static final String OWNER = "person:native-lock";
    private static final FilePath PATH = new FilePath("/Team/locked.txt");
    private static final Instant START = Instant.parse("2026-08-20T12:00:00Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @TempDir
    Path temporaryDirectory;

    @Test
    void acquireReplayRefreshAndUnlockArePlanlessAtomicHeadLockedFinalizations() throws Exception {
        Fixture fixture = fixture("lifecycle");
        Prepared acquire = prepared(fixture, "native-lock-acquire-0001", "webdav-lock", PATH.value() + "\nacquire");

        LockResult acquired = fixture.repository().acquire(
                acquire.candidate(),
                acquire.command(),
                SPACE,
                PATH,
                Duration.ofHours(4),
                audit(acquire));
        LockResult replayed = fixture.repository().acquire(
                acquire.candidate(),
                acquire.command(),
                SPACE,
                PATH,
                Duration.ofHours(4),
                audit(acquire));

        assertThat(acquired.replay()).isFalse();
        assertThat(replayed.replay()).isTrue();
        assertThat(replayed.token()).isEqualTo(acquired.token());
        assertThat(replayed.expiresAt()).isEqualTo(acquired.expiresAt());
        assertThat(acquired.expiresAt()).isEqualTo(START.plus(Duration.ofHours(1)));
        assertThat(acquired.token())
                .startsWith("opaquelocktoken:cur_")
                .doesNotContain(acquire.candidate().operationRef());
        assertAtomicState(fixture, 1, 1);
        assertThat(fixture.jdbc().queryForMap("""
                select token_digest, expires_at_utc, released_at_utc
                  from weave_file_locks
                 where organization_ref = ? and space_ref = ? and canonical_path = ?
                """, ORGANIZATION, SPACE, PATH.value()))
                .containsEntry("token_digest", FilesMutationIntentService.digest(acquired.token()))
                .containsEntry("released_at_utc", null);

        fixture.clock().advance(Duration.ofMinutes(10));
        Prepared refresh = prepared(
                fixture,
                "native-lock-refresh-0001",
                "webdav-lock",
                PATH.value() + "\nrefresh\n" + FilesMutationIntentService.digest(acquired.token()));
        LockResult refreshed = fixture.repository().refresh(
                refresh.candidate(),
                refresh.command(),
                SPACE,
                PATH,
                acquired.token(),
                Duration.ofHours(3),
                audit(refresh));
        LockResult refreshReplay = fixture.repository().refresh(
                refresh.candidate(),
                refresh.command(),
                SPACE,
                PATH,
                acquired.token(),
                Duration.ofHours(3),
                audit(refresh));

        assertThat(refreshed.token()).isEqualTo(acquired.token());
        assertThat(refreshed.expiresAt()).isAfter(acquired.expiresAt());
        assertThat(refreshed.expiresAt()).isEqualTo(fixture.clock().instant().plus(Duration.ofHours(1)));
        assertThat(refreshReplay.replay()).isTrue();
        assertThat(refreshReplay.expiresAt()).isEqualTo(refreshed.expiresAt());
        assertAtomicState(fixture, 2, 2);

        fixture.clock().advance(Duration.ofMinutes(5));
        Prepared unlock = prepared(
                fixture,
                "native-lock-unlock-0001",
                "webdav-unlock",
                PATH.value() + "\n" + FilesMutationIntentService.digest(acquired.token()));
        var unlocked = fixture.repository().unlock(
                unlock.candidate(),
                unlock.command(),
                SPACE,
                PATH,
                acquired.token(),
                audit(unlock));
        var unlockReplay = fixture.repository().unlock(
                unlock.candidate(),
                unlock.command(),
                SPACE,
                PATH,
                acquired.token(),
                audit(unlock));

        assertThat(unlocked.replay()).isFalse();
        assertThat(unlockReplay.replay()).isTrue();
        assertAtomicState(fixture, 3, 3);
        assertThat(fixture.jdbc().queryForObject("""
                select released_at_utc is not null
                  from weave_file_locks
                 where organization_ref = ? and space_ref = ? and canonical_path = ?
                """, Boolean.class, ORGANIZATION, SPACE, PATH.value())).isTrue();
        assertThat(fixture.jdbc().queryForList("""
                select outbox_ref
                  from weave_operation_outbox
                 order by operation_ref
                """, String.class))
                .containsExactlyInAnyOrder(
                        acquire.candidate().outboxRef(),
                        refresh.candidate().outboxRef(),
                        unlock.candidate().outboxRef());
    }

    @Test
    void acquireFailsClosedWhenTheProvisionedScopeHeadIsMissing() throws Exception {
        Fixture fixture = fixture("missing_head");
        fixture.jdbc().update("delete from weave_files_stream_heads");
        Prepared acquire = prepared(
                fixture,
                "native-lock-missing-head-0001",
                "webdav-lock",
                PATH.value() + "\nacquire");

        assertThatThrownBy(() -> fixture.repository().acquire(
                acquire.candidate(),
                acquire.command(),
                SPACE,
                PATH,
                Duration.ofHours(1),
                audit(acquire)))
                .isInstanceOf(JpaNativeFilesLockRepository.CorruptLockOperationException.class)
                .hasMessageContaining("scope head is missing");

        assertNoLockEffects(fixture);
    }

    @Test
    void concurrentAcquiresSerializeOnScopeHeadAndSettleTheLoserExactlyOnce() throws Exception {
        Fixture fixture = fixture("concurrency");
        provisionHead(fixture);
        Prepared first = prepared(fixture, "native-lock-race-first", "webdav-lock", PATH.value() + "\nacquire");
        Prepared second = prepared(fixture, "native-lock-race-second", "webdav-lock", PATH.value() + "\nacquire");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        List<LockResult> successes = new ArrayList<>();
        List<Throwable> failures = new ArrayList<>();
        try {
            var firstFuture = executor.submit(() -> acquireAfterGate(fixture, first, ready, start));
            var secondFuture = executor.submit(() -> acquireAfterGate(fixture, second, ready, start));
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (var future : List.of(firstFuture, secondFuture)) {
                try {
                    successes.add(future.get(1, TimeUnit.MINUTES));
                } catch (ExecutionException failure) {
                    failures.add(failure.getCause());
                }
            }
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(successes).hasSize(1);
        assertThat(failures).singleElement().isInstanceOf(LockConflictException.class);
        assertAtomicState(fixture, 1, 1);
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_operation_intents where intent_state = 'FAILED'",
                Integer.class)).isEqualTo(1);
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_operation_outbox where event_type = 'operation.failed'",
                Integer.class)).isEqualTo(1);
        assertThat(fixture.jdbc().queryForObject("select count(*) from weave_file_locks", Integer.class))
                .isEqualTo(1);

        Prepared loser = failed(fixture, first) ? first : second;
        assertThatThrownBy(() -> fixture.repository().acquire(
                loser.candidate(),
                loser.command(),
                SPACE,
                PATH,
                Duration.ofHours(1),
                audit(loser)))
                .isInstanceOf(LockConflictException.class);
        assertSettlementEvidence(
                fixture,
                loser,
                PATH,
                OperationIntent.State.FAILED,
                "operation.failed",
                "lock-precondition-failed");
    }

    @Test
    void revokedAuthorizationRollsBackThenSettlesDeniedWithExactReplay() throws Exception {
        Fixture fixture = fixture("authorization_revoked");
        Prepared acquire = prepared(
                fixture,
                "native-lock-authorization-0001",
                "webdav-lock",
                PATH,
                PATH.value() + "\nacquire");
        fixture.authorizationAllowed().set(false);

        assertThatThrownBy(() -> fixture.repository().acquire(
                acquire.candidate(),
                acquire.command(),
                SPACE,
                PATH,
                Duration.ofHours(1),
                audit(acquire)))
                .isInstanceOf(LockAuthorizationDeniedException.class);
        assertThatThrownBy(() -> fixture.repository().acquire(
                acquire.candidate(),
                acquire.command(),
                SPACE,
                PATH,
                Duration.ofHours(1),
                audit(acquire)))
                .isInstanceOf(LockAuthorizationDeniedException.class);

        assertSettlementEvidence(
                fixture,
                acquire,
                PATH,
                OperationIntent.State.DENIED,
                "operation.denied",
                "authorization-denied");
        assertThat(fixture.jdbc().queryForObject("""
                select latest_revision
                  from weave_files_stream_heads
                 where organization_ref = ? and space_ref = ?
                """, Long.class, ORGANIZATION, SPACE)).isZero();
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_file_locks", Integer.class)).isZero();
    }

    @Test
    void acquiringAnOverlappingNestedPathSettlesOneFailedIntentAndReplaysIt() throws Exception {
        Fixture fixture = fixture("nested_path_conflict");
        FilePath parent = new FilePath("/Team");
        FilePath child = new FilePath("/Team/reports/quarterly.txt");
        Prepared parentAcquire = prepared(
                fixture,
                "native-lock-parent-0001",
                "webdav-lock",
                parent,
                parent.value() + "\nacquire");
        Prepared childAcquire = prepared(
                fixture,
                "native-lock-child-0001",
                "webdav-lock",
                child,
                child.value() + "\nacquire");

        fixture.repository().acquire(
                parentAcquire.candidate(),
                parentAcquire.command(),
                SPACE,
                parent,
                Duration.ofHours(1),
                audit(parentAcquire));

        assertThatThrownBy(() -> fixture.repository().acquire(
                childAcquire.candidate(),
                childAcquire.command(),
                SPACE,
                child,
                Duration.ofHours(1),
                audit(childAcquire)))
                .isInstanceOf(LockConflictException.class);
        assertThatThrownBy(() -> fixture.repository().acquire(
                childAcquire.candidate(),
                childAcquire.command(),
                SPACE,
                child,
                Duration.ofHours(1),
                audit(childAcquire)))
                .isInstanceOf(LockConflictException.class);

        assertAtomicState(fixture, 1, 1);
        assertSettlementEvidence(
                fixture,
                childAcquire,
                child,
                OperationIntent.State.FAILED,
                "operation.failed",
                "lock-precondition-failed");
        assertThat(fixture.jdbc().queryForObject("select count(*) from weave_file_locks", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void invalidRefreshAndUnlockTokensSettleFailedWithoutChangingTheLiveLock() throws Exception {
        Fixture fixture = fixture("invalid_tokens");
        Prepared acquire = prepared(
                fixture,
                "native-lock-invalid-token-acquire",
                "webdav-lock",
                PATH.value() + "\nacquire");
        LockResult acquired = fixture.repository().acquire(
                acquire.candidate(),
                acquire.command(),
                SPACE,
                PATH,
                Duration.ofHours(1),
                audit(acquire));
        String invalidToken = "opaquelocktoken:cur_invalid_token";
        Prepared refresh = prepared(
                fixture,
                "native-lock-invalid-token-refresh",
                "webdav-lock",
                PATH.value() + "\nrefresh\n" + FilesMutationIntentService.digest(invalidToken));
        Prepared unlock = prepared(
                fixture,
                "native-lock-invalid-token-unlock",
                "webdav-unlock",
                PATH.value() + "\n" + FilesMutationIntentService.digest(invalidToken));

        for (int replay = 0; replay < 2; replay++) {
            assertThatThrownBy(() -> fixture.repository().refresh(
                    refresh.candidate(),
                    refresh.command(),
                    SPACE,
                    PATH,
                    invalidToken,
                    Duration.ofHours(1),
                    audit(refresh)))
                    .isInstanceOf(LockConflictException.class);
            assertThatThrownBy(() -> fixture.repository().unlock(
                    unlock.candidate(),
                    unlock.command(),
                    SPACE,
                    PATH,
                    invalidToken,
                    audit(unlock)))
                    .isInstanceOf(LockConflictException.class);
        }

        assertSettlementEvidence(
                fixture,
                refresh,
                PATH,
                OperationIntent.State.FAILED,
                "operation.failed",
                "lock-precondition-failed");
        assertSettlementEvidence(
                fixture,
                unlock,
                PATH,
                OperationIntent.State.FAILED,
                "operation.failed",
                "lock-precondition-failed");
        assertAtomicState(fixture, 1, 1);
        assertThat(fixture.jdbc().queryForObject("""
                select expires_at_utc = ? and released_at_utc is null
                  from weave_file_locks
                 where organization_ref = ? and space_ref = ? and canonical_path = ?
                """, Boolean.class, acquired.expiresAt().atOffset(ZoneOffset.UTC),
                ORGANIZATION, SPACE, PATH.value())).isTrue();
    }

    @Test
    void committedSuccessWinsAgainstAConcurrentFailureSettlement() throws Exception {
        SettlementGate settlementGate = new SettlementGate(
                new CountDownLatch(1),
                new CountDownLatch(1));
        Fixture fixture = fixture("success_wins_settlement", settlementGate);
        Prepared acquire = prepared(
                fixture,
                "native-lock-success-wins-0001",
                "webdav-lock",
                PATH.value() + "\nacquire");
        fixture.authorizationAllowed().set(false);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var failedFinalization = executor.submit(() -> fixture.repository().acquire(
                    acquire.candidate(),
                    acquire.command(),
                    SPACE,
                    PATH,
                    Duration.ofHours(1),
                    audit(acquire)));
            assertThat(settlementGate.ready().await(30, TimeUnit.SECONDS)).isTrue();

            fixture.authorizationAllowed().set(true);
            LockResult winner = fixture.repository().acquire(
                    acquire.candidate(),
                    acquire.command(),
                    SPACE,
                    PATH,
                    Duration.ofHours(1),
                    audit(acquire));
            settlementGate.proceed().countDown();
            LockResult recovered = failedFinalization.get(1, TimeUnit.MINUTES);

            assertThat(winner.replay()).isFalse();
            assertThat(recovered.replay()).isTrue();
            assertThat(recovered.token()).isEqualTo(winner.token());
            assertThat(recovered.expiresAt()).isEqualTo(winner.expiresAt());
            assertAtomicState(fixture, 1, 1);
            assertThat(fixture.jdbc().queryForObject(
                    "select count(*) from weave_operation_intents", Integer.class)).isEqualTo(1);
            assertThat(fixture.jdbc().queryForObject(
                    "select count(*) from weave_operation_outbox", Integer.class)).isEqualTo(1);
            assertThat(fixture.jdbc().queryForObject(
                    "select count(*) from weave_file_locks", Integer.class)).isEqualTo(1);
        } finally {
            settlementGate.proceed().countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void uncertainPersistenceFailureRemainsUnsettled() throws Exception {
        Fixture fixture = fixture("uncertain_persistence");
        fixture.jdbc().execute("""
                create function fail_native_lock_intent_insert()
                returns trigger
                language plpgsql
                as $function$
                begin
                    raise sqlstate '08006' using message = 'simulated uncertain native lock persistence';
                end
                $function$
                """);
        fixture.jdbc().execute("""
                create trigger trg_fail_native_lock_intent_insert
                before insert on weave_operation_intents
                for each row execute function fail_native_lock_intent_insert()
                """);
        Prepared acquire = prepared(
                fixture,
                "native-lock-uncertain-0001",
                "webdav-lock",
                PATH.value() + "\nacquire");

        assertThatThrownBy(() -> fixture.repository().acquire(
                acquire.candidate(),
                acquire.command(),
                SPACE,
                PATH,
                Duration.ofHours(1),
                audit(acquire)))
                .isNotInstanceOf(LockConflictException.class)
                .isNotInstanceOf(LockAuthorizationDeniedException.class);

        assertNoLockEffects(fixture, 1);
    }

    @Test
    void boundedPersistenceFailureThatIsProvenAbsentSettlesFailed() throws Exception {
        Fixture fixture = fixture("proven_persistence_failure");
        fixture.jdbc().execute("create sequence fail_native_lock_attempt start with 1");
        fixture.jdbc().execute("""
                create function fail_first_native_lock_intent_inserts()
                returns trigger
                language plpgsql
                as $function$
                begin
                    if nextval('fail_native_lock_attempt') <= 3 then
                        raise unique_violation using message = 'simulated bounded native lock persistence failure';
                    end if;
                    return new;
                end
                $function$
                """);
        fixture.jdbc().execute("""
                create trigger trg_fail_first_native_lock_intent_inserts
                before insert on weave_operation_intents
                for each row execute function fail_first_native_lock_intent_inserts()
                """);
        Prepared acquire = prepared(
                fixture,
                "native-lock-proven-persistence-0001",
                "webdav-lock",
                PATH.value() + "\nacquire");

        assertThatThrownBy(() -> fixture.repository().acquire(
                acquire.candidate(),
                acquire.command(),
                SPACE,
                PATH,
                Duration.ofHours(1),
                audit(acquire)))
                .isInstanceOf(LockConflictException.class);

        assertSettlementEvidence(
                fixture,
                acquire,
                PATH,
                OperationIntent.State.FAILED,
                "operation.failed",
                "persistence-failed");
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_file_locks", Integer.class)).isZero();
        assertThat(fixture.jdbc().queryForObject("""
                select latest_revision
                  from weave_files_stream_heads
                 where organization_ref = ? and space_ref = ?
                """, Long.class, ORGANIZATION, SPACE)).isZero();
    }

    @Test
    void freshCandidateMustBeFullyEquivalentToItsBeginCommand() throws Exception {
        Fixture fixture = fixture("intent_equivalence");
        Prepared acquire = prepared(
                fixture,
                "native-lock-equivalence-0001",
                "webdav-lock",
                PATH,
                PATH.value() + "\nacquire");
        BeginCommand supplied = acquire.command();
        BeginCommand mismatched = new BeginCommand(
                supplied.idempotencyKey(),
                supplied.organizationRef(),
                new HumanActor("person:different", "subject:different"),
                supplied.domain(),
                supplied.projection(),
                supplied.actionDigest(),
                supplied.canonicalArgumentsDigest(),
                supplied.objectRefs(),
                supplied.policyRevision(),
                supplied.entitlementRevision(),
                supplied.providerBindingRevision());

        assertThatThrownBy(() -> fixture.repository().acquire(
                acquire.candidate(),
                mismatched,
                SPACE,
                PATH,
                Duration.ofHours(1),
                audit(acquire)))
                .isInstanceOf(OperationIntentService.IdempotencyKeyConflictException.class);

        assertNoLockEffects(fixture, 1);
    }

    private LockResult acquireAfterGate(
            Fixture fixture,
            Prepared prepared,
            CountDownLatch ready,
            CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(30, TimeUnit.SECONDS)) {
            throw new IllegalStateException("native Files lock concurrency gate timed out");
        }
        return fixture.repository().acquire(
                prepared.candidate(),
                prepared.command(),
                SPACE,
                PATH,
                Duration.ofHours(1),
                audit(prepared));
    }

    private void assertAtomicState(Fixture fixture, int intentCount, int outboxCount) {
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_operation_intents where intent_state = 'SUCCEEDED'",
                Integer.class)).isEqualTo(intentCount);
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_operation_outbox where event_type = 'operation.succeeded'",
                Integer.class)).isEqualTo(outboxCount);
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_files_mutation_plans",
                Integer.class)).isZero();
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_files_mutation_targets",
                Integer.class)).isZero();
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_files_mutation_fences",
                Integer.class)).isZero();
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_files_changes",
                Integer.class)).isZero();
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_files_blob_cleanup_dispositions",
                Integer.class)).isZero();
        assertThat(fixture.jdbc().queryForObject("""
                select latest_revision
                  from weave_files_stream_heads
                 where organization_ref = ? and space_ref = ?
                """, Long.class, ORGANIZATION, SPACE)).isZero();
    }

    private void assertSettlementEvidence(
            Fixture fixture,
            Prepared prepared,
            FilePath path,
            OperationIntent.State expectedState,
            String expectedEventType,
            String resultCode) {
        ProtocolProjection projection = (ProtocolProjection) prepared.candidate().projection();
        String expectedResultDigest = FilesMutationIntentService.digest(String.join(
                "\n",
                "weave.files-lock-settlement/v1",
                projection.operation(),
                path.value(),
                resultCode));
        assertThat(fixture.jdbc().queryForMap("""
                select intent_state, result_digest, audit_ref, initial_outbox_ref
                  from weave_operation_intents
                 where operation_ref = ?
                """, prepared.candidate().operationRef()))
                .containsEntry("intent_state", expectedState.name())
                .containsEntry("result_digest", expectedResultDigest)
                .containsEntry("audit_ref", audit(prepared))
                .containsEntry("initial_outbox_ref", prepared.candidate().outboxRef());
        assertThat(fixture.jdbc().queryForList("""
                select outbox_ref, event_type
                  from weave_operation_outbox
                 where operation_ref = ?
                """, prepared.candidate().operationRef()))
                .singleElement()
                .satisfies(row -> assertThat(row)
                        .containsEntry("outbox_ref", prepared.candidate().outboxRef())
                        .containsEntry("event_type", expectedEventType));
        assertThat(fixture.jdbc().queryForObject("""
                select count(*)
                  from weave_files_mutation_plans
                 where operation_ref = ?
                """, Integer.class, prepared.candidate().operationRef())).isZero();
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_files_mutation_targets", Integer.class)).isZero();
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_files_mutation_fences", Integer.class)).isZero();
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_files_changes", Integer.class)).isZero();
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_files_blob_cleanup_dispositions", Integer.class)).isZero();
    }

    private boolean failed(Fixture fixture, Prepared prepared) {
        return "FAILED".equals(fixture.jdbc().queryForObject("""
                select intent_state
                  from weave_operation_intents
                 where operation_ref = ?
                """, String.class, prepared.candidate().operationRef()));
    }

    private void assertNoLockEffects(Fixture fixture) {
        assertNoLockEffects(fixture, 0);
    }

    private void assertNoLockEffects(Fixture fixture, int expectedHeadCount) {
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_files_stream_heads", Integer.class))
                .isEqualTo(expectedHeadCount);
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_file_locks", Integer.class)).isZero();
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_operation_intents", Integer.class)).isZero();
        assertThat(fixture.jdbc().queryForObject(
                "select count(*) from weave_operation_outbox", Integer.class)).isZero();
    }

    private void provisionHead(Fixture fixture) {
        fixture.jdbc().update("""
                insert into weave_files_stream_heads (
                    organization_ref, space_ref, latest_revision, reset_required_floor,
                    lock_version, updated_at_utc)
                values (?, ?, 0, 0, 0, ?)
                on conflict (organization_ref, space_ref) do nothing
                """, ORGANIZATION, SPACE, START.atOffset(ZoneOffset.UTC));
    }

    private Prepared prepared(
            Fixture fixture,
            String idempotencyKey,
            String operation,
            String canonicalArguments) {
        return prepared(fixture, idempotencyKey, operation, PATH, canonicalArguments);
    }

    private Prepared prepared(
            Fixture fixture,
            String idempotencyKey,
            String operation,
            FilePath path,
            String canonicalArguments) {
        BeginCommand command = new BeginCommand(
                idempotencyKey,
                ORGANIZATION,
                new HumanActor(OWNER, "subject:native-lock"),
                "files",
                new ProtocolProjection("webdav", operation, "weave.webdav.files/v1"),
                FilesMutationIntentService.digest(operation),
                FilesMutationIntentService.digest(canonicalArguments),
                List.of("file-path:" + FilesMutationIntentService.digest(path.value())),
                "policy:native-lock",
                "entitlement:native-lock",
                7);
        return new Prepared(fixture.intentService().prepare(command), command);
    }

    private String audit(Prepared prepared) {
        return "files-operation-intent:" + prepared.candidate().operationRef();
    }

    private Fixture fixture(String semanticName) throws Exception {
        return fixture(semanticName, null);
    }

    private Fixture fixture(
            String semanticName,
            SettlementGate settlementGate) throws Exception {
        DriverManagerDataSource dataSource = migratedDataSource(semanticName);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        MutableClock clock = new MutableClock(START);
        var operations = OperationIntentJpaTestFactory.create(dataSource);
        OperationIntentService intentService = new OperationIntentService(operations, clock);
        IdentityInvitationProperties properties = new IdentityInvitationProperties();
        Path secret = temporaryDirectory.resolve(semanticName + "-identity-hmac-key");
        Files.writeString(secret, "0123456789abcdef0123456789abcdef");
        NativeFilesLockTokenCodec tokenCodec = new NativeFilesLockTokenCodec(
                new IdentityOpaqueReferenceCodec(propertiesWithSecret(properties, secret)));
        AtomicBoolean authorizationAllowed = new AtomicBoolean(true);
        JpaNativeFilesLockRepository repository = new JpaNativeFilesLockRepository(
                JpaTestDatabase.repository(dataSource, FilesStreamHeadJpaRepository.class),
                JpaTestDatabase.repository(dataSource, FileLockJpaRepository.class),
                operations,
                intentService,
                tokenCodec,
                (intent, spaceRef) -> authorizationAllowed.get(),
                JpaTestDatabase.transactionManager(dataSource),
                clock) {
            @Override
            void beforeDeterministicSettlement(OperationIntent candidate) {
                if (settlementGate != null) {
                    settlementGate.await();
                }
            }
        };
        Fixture fixture = new Fixture(
                jdbc,
                repository,
                intentService,
                clock,
                authorizationAllowed,
                dataSource);
        provisionHead(fixture);
        return fixture;
    }

    private IdentityInvitationProperties propertiesWithSecret(
            IdentityInvitationProperties properties,
            Path secret) {
        properties.keycloak().setReferenceHmacSecretFile(secret.toString());
        return properties;
    }

    private DriverManagerDataSource migratedDataSource(String semanticName) {
        String schema = ("files_lock_" + semanticName + "_" + UUID.randomUUID())
                .toLowerCase()
                .replace("-", "_");
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("create schema \"" + schema + "\"");
        } catch (java.sql.SQLException failure) {
            throw new IllegalStateException("native Files lock test schema could not be created", failure);
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

    private record Fixture(
            JdbcTemplate jdbc,
            JpaNativeFilesLockRepository repository,
            OperationIntentService intentService,
            MutableClock clock,
            AtomicBoolean authorizationAllowed,
            DriverManagerDataSource dataSource) {
    }

    private record Prepared(OperationIntent candidate, BeginCommand command) {
    }

    private record SettlementGate(
            CountDownLatch ready,
            CountDownLatch proceed) {

        void await() {
            ready.countDown();
            try {
                if (!proceed.await(30, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("native Files lock settlement gate timed out");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "native Files lock settlement gate was interrupted",
                        interrupted);
            }
        }
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;

        private MutableClock(Instant initial) {
            this.instant = new AtomicReference<>(initial);
        }

        void advance(Duration duration) {
            instant.updateAndGet(current -> current.plus(duration));
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
            return instant.get();
        }
    }
}

package com.massimotter.weave.backend.runner.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.runner.application.RunnerCapabilityRegistry;
import com.massimotter.weave.backend.runner.application.RunnerCapabilityRegistry.CapabilityContract;
import com.massimotter.weave.backend.runner.application.RunnerCapabilityRegistry.PublicBundlePublication;
import com.massimotter.weave.backend.runner.application.RunnerTaskStore;
import com.massimotter.weave.backend.runner.application.RunnerTaskStore.CancellationDisposition;
import com.massimotter.weave.backend.runner.application.RunnerTaskStore.CancellationRequest;
import com.massimotter.weave.backend.runner.application.RunnerTaskStore.Claim;
import com.massimotter.weave.backend.runner.application.RunnerTaskStore.Completion;
import com.massimotter.weave.backend.runner.application.RunnerTaskStore.CompletionDisposition;
import com.massimotter.weave.backend.runner.application.RunnerTaskStore.Heartbeat;
import com.massimotter.weave.backend.runner.application.RunnerTaskStore.Lease;
import com.massimotter.weave.backend.runner.application.RunnerTaskStore.LeaseDirective;
import com.massimotter.weave.backend.runner.application.RunnerTaskStore.NewTask;
import com.massimotter.weave.backend.runner.domain.RunnerControl;
import com.massimotter.weave.backend.runner.domain.RunnerControl.CapabilityDescriptor;
import com.massimotter.weave.backend.runner.domain.RunnerControl.CapabilityEffect;
import com.massimotter.weave.backend.runner.domain.RunnerControl.CapabilityId;
import com.massimotter.weave.backend.runner.domain.RunnerControl.CapabilityRef;
import com.massimotter.weave.backend.runner.domain.RunnerControl.RunnerId;
import com.massimotter.weave.backend.runner.domain.RunnerControl.RunnerState;
import com.massimotter.weave.backend.runner.domain.RunnerControl.TaskState;
import com.massimotter.weave.backend.testing.JpaTestDatabase;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("postgres")
class JpaRunnerTaskStorePostgresTest {

    private static final String CONTRACT_DIGEST =
            "sha256:1111111111111111111111111111111111111111111111111111111111111111";
    private static final String PUBLIC_BUNDLE_DIGEST =
            "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String INPUT_SCHEMA_DIGEST =
            "sha256:dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd";
    private static final String OUTPUT_SCHEMA_DIGEST =
            "sha256:eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee";
    private static final String OUTCOME_A =
            "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String OUTCOME_B =
            "sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc";
    private static final String TRACEPARENT =
            "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
    private static final CapabilityRef CAPABILITY =
            new CapabilityRef(new CapabilityId("internal.asset.lookup"), "1.0.0");
    private static final RunnerId RUNNER_A = new RunnerId("runner_engine_a01");
    private static final RunnerId RUNNER_B = new RunnerId("runner_engine_b01");

    @Test
    void competingEngineInstancesLeaseOneAttemptOnly() throws Exception {
        var dataSource = JpaTestDatabase.entityFirstDataSource("runner-single-claim");
        Instant now = Instant.parse("2026-08-28T08:00:00Z");
        registerOfferings(dataSource, now.minusSeconds(1));
        RunnerTaskStore first = store(dataSource);
        RunnerTaskStore second = store(dataSource);
        UUID taskId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        first.enqueue(task(taskId, now));

        CyclicBarrier start = new CyclicBarrier(2);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Callable<Optional<Lease>> firstClaim = () -> {
                start.await();
                return first.claim(claim(RUNNER_A, now));
            };
            Callable<Optional<Lease>> secondClaim = () -> {
                start.await();
                return second.claim(claim(RUNNER_B, now));
            };

            Future<Optional<Lease>> left = pool.submit(firstClaim);
            Future<Optional<Lease>> right = pool.submit(secondClaim);
            List<Lease> leases = List.of(left.get(), right.get()).stream()
                    .flatMap(Optional::stream)
                    .toList();

            assertThat(leases).hasSize(1);
            assertThat(leases.getFirst().taskId()).isEqualTo(taskId);
            assertThat(leases.getFirst().attempt()).isEqualTo(1);
            assertThat(leases.getFirst().fencingToken()).isEqualTo(1);
            assertThat(leases.getFirst().capabilityContractDigest()).isEqualTo(CONTRACT_DIGEST);
            assertThat(leases.getFirst().publicBundleDigest()).isEqualTo(PUBLIC_BUNDLE_DIGEST);
        }

        var snapshot = first.find(taskId).orElseThrow();
        assertThat(snapshot.state()).isEqualTo(TaskState.LEASED);
        assertThat(snapshot.attempt()).isEqualTo(1);
        assertThat(snapshot.fencingToken()).isEqualTo(1);
    }

    @Test
    void skipLockedClaimsAnotherTaskWithoutWaitingForTheLockedCandidate() throws Exception {
        var dataSource = JpaTestDatabase.entityFirstDataSource("runner-skip-locked");
        Instant now = Instant.parse("2026-08-28T09:00:00Z");
        registerOfferings(dataSource, now.minusSeconds(1));
        RunnerTaskStore store = store(dataSource);
        UUID firstTask = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID secondTask = UUID.fromString("00000000-0000-0000-0000-000000000020");
        store.enqueue(task(firstTask, now));
        store.enqueue(task(secondTask, now));

        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var jdbc = new JdbcTemplate(dataSource);
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        try (ExecutorService lockerPool = Executors.newSingleThreadExecutor();
                ScheduledExecutorService safetyRelease = Executors.newSingleThreadScheduledExecutor()) {
            Future<?> locker = lockerPool.submit(() -> transaction.executeWithoutResult(status -> {
                jdbc.queryForObject(
                        "select task_id::text from weave_runner_tasks where task_id = ? for update",
                        String.class,
                        firstTask);
                locked.countDown();
                await(release);
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            safetyRelease.schedule(release::countDown, 3, TimeUnit.SECONDS);

            long started = System.nanoTime();
            Lease claimed = store.claim(claim(RUNNER_A, now)).orElseThrow();
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

            assertThat(claimed.taskId()).isEqualTo(secondTask);
            assertThat(elapsedMillis).isLessThan(2000);
            release.countDown();
            locker.get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void expiredLeaseIsFencedAndTheCurrentOutcomeIsIdempotent() {
        var dataSource = JpaTestDatabase.entityFirstDataSource("runner-fencing");
        Instant firstClaimAt = Instant.parse("2026-08-28T10:00:00Z");
        registerOfferings(dataSource, firstClaimAt.minusSeconds(1));
        RunnerTaskStore store = store(dataSource);
        UUID taskId = UUID.fromString("00000000-0000-0000-0000-000000000100");
        store.enqueue(task(taskId, firstClaimAt));

        Lease first = store.claim(claim(RUNNER_A, firstClaimAt)).orElseThrow();
        Lease second = store.claim(claim(RUNNER_B, firstClaimAt.plusSeconds(31))).orElseThrow();

        assertThat(second.taskId()).isEqualTo(taskId);
        assertThat(second.attempt()).isEqualTo(2);
        assertThat(second.fencingToken()).isEqualTo(2);
        assertThat(second.leaseId()).isNotEqualTo(first.leaseId());

        assertThatThrownBy(() -> store.complete(completion(first, OUTCOME_A)))
                .isInstanceOf(RunnerControl.StaleTaskLeaseException.class);

        Completion current = completion(second, OUTCOME_A);
        assertThat(store.complete(current)).isEqualTo(CompletionDisposition.APPLIED);
        assertThat(store.complete(current)).isEqualTo(CompletionDisposition.IDEMPOTENT_REPLAY);
        assertThatThrownBy(() -> store.complete(completion(second, OUTCOME_B)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("different outcome");

        var snapshot = store.find(taskId).orElseThrow();
        assertThat(snapshot.state()).isEqualTo(TaskState.SUCCEEDED);
        assertThat(snapshot.attempt()).isEqualTo(2);
        assertThat(snapshot.fencingToken()).isEqualTo(2);
        assertThat(snapshot.outcomeDigest()).isEqualTo(OUTCOME_A);
    }

    @Test
    void heartbeatExtendsTheLeaseButNeverPastTheHardDeadline() {
        var dataSource = JpaTestDatabase.entityFirstDataSource("runner-heartbeat-deadline");
        Instant now = Instant.parse("2026-08-28T11:00:00Z");
        registerOfferings(dataSource, now.minusSeconds(1));
        RunnerTaskStore store = store(dataSource);
        UUID taskId = UUID.fromString("00000000-0000-0000-0000-000000000200");
        Instant deadline = now.plusSeconds(45);
        store.enqueue(task(taskId, now, deadline));
        Lease lease = store.claim(claim(RUNNER_A, now)).orElseThrow();

        LeaseDirective directive = store.heartbeat(new Heartbeat(
                taskId,
                lease.leaseId(),
                lease.fencingToken(),
                RUNNER_A,
                now.plusSeconds(20),
                Duration.ofSeconds(30)));

        assertThat(directive.leaseId()).isEqualTo(lease.leaseId());
        assertThat(directive.fencingToken()).isEqualTo(lease.fencingToken());
        assertThat(directive.expiresAt()).isEqualTo(deadline);
        assertThat(directive.cancelRequested()).isFalse();
        var snapshot = store.find(taskId).orElseThrow();
        assertThat(snapshot.state()).isEqualTo(TaskState.RUNNING);
        assertThat(snapshot.leaseExpiresAt()).isEqualTo(deadline);
    }

    @Test
    void cancellationSurvivesStoreRestartAndStopsLeaseExtensionOrReclaim() {
        var dataSource = JpaTestDatabase.entityFirstDataSource("runner-cancellation");
        Instant now = Instant.parse("2026-08-28T12:00:00Z");
        registerOfferings(dataSource, now.minusSeconds(1));
        RunnerTaskStore store = store(dataSource);
        UUID taskId = UUID.fromString("00000000-0000-0000-0000-000000000300");
        store.enqueue(task(taskId, now));
        Lease lease = store.claim(claim(RUNNER_A, now)).orElseThrow();
        CancellationRequest request =
                new CancellationRequest(taskId, "org:example", "USER_REQUESTED", now.plusSeconds(5));

        assertThat(store.requestCancellation(request)).isEqualTo(CancellationDisposition.APPLIED);
        assertThat(store.requestCancellation(request))
                .isEqualTo(CancellationDisposition.IDEMPOTENT_REPLAY);

        RunnerTaskStore restarted = store(dataSource);
        LeaseDirective directive = restarted.heartbeat(new Heartbeat(
                taskId,
                lease.leaseId(),
                lease.fencingToken(),
                RUNNER_A,
                now.plusSeconds(6),
                Duration.ofSeconds(30)));

        assertThat(directive.cancelRequested()).isTrue();
        assertThat(directive.expiresAt()).isEqualTo(lease.expiresAt());
        assertThat(restarted.find(taskId).orElseThrow().cancelRequested()).isTrue();
        assertThat(restarted.claim(claim(RUNNER_B, now.plusSeconds(31)))).isEmpty();
    }

    @Test
    void staleHeartbeatCannotExtendAReplacementLease() {
        var dataSource = JpaTestDatabase.entityFirstDataSource("runner-stale-heartbeat");
        Instant now = Instant.parse("2026-08-28T13:00:00Z");
        registerOfferings(dataSource, now.minusSeconds(1));
        RunnerTaskStore store = store(dataSource);
        UUID taskId = UUID.fromString("00000000-0000-0000-0000-000000000400");
        store.enqueue(task(taskId, now));
        Lease first = store.claim(claim(RUNNER_A, now)).orElseThrow();
        Lease second = store.claim(claim(RUNNER_B, now.plusSeconds(31))).orElseThrow();

        assertThatThrownBy(() -> store.heartbeat(new Heartbeat(
                        taskId,
                        first.leaseId(),
                        first.fencingToken(),
                        RUNNER_A,
                        now.plusSeconds(32),
                        Duration.ofSeconds(30))))
                .isInstanceOf(RunnerControl.StaleTaskLeaseException.class);

        var snapshot = store.find(taskId).orElseThrow();
        assertThat(snapshot.leaseId()).isEqualTo(second.leaseId());
        assertThat(snapshot.fencingToken()).isEqualTo(second.fencingToken());
        assertThat(snapshot.leaseExpiresAt()).isEqualTo(second.expiresAt());
    }

    private RunnerTaskStore store(DataSource dataSource) {
        return JpaTestDatabase.transactional(
                dataSource,
                new JpaRunnerTaskStore(JpaTestDatabase.entityManager(dataSource)));
    }

    private RunnerCapabilityRegistry registry(DataSource dataSource) {
        return JpaTestDatabase.transactional(
                dataSource,
                new JpaRunnerCapabilityRegistry(JpaTestDatabase.entityManager(dataSource)));
    }

    private void registerOfferings(DataSource dataSource, Instant observedAt) {
        RunnerCapabilityRegistry registry = registry(dataSource);
        CapabilityContract contract = new CapabilityContract(
                new CapabilityDescriptor(
                        CAPABILITY,
                        "Internal asset lookup",
                        "Returns one bounded internal asset record.",
                        CapabilityEffect.READ_ONLY,
                        "{\"additionalProperties\":false,\"type\":\"object\"}",
                        INPUT_SCHEMA_DIGEST,
                        "{\"additionalProperties\":false,\"type\":\"object\"}",
                        OUTPUT_SCHEMA_DIGEST,
                        Duration.ofSeconds(60),
                        4096,
                        Set.of("asset-report")),
                CONTRACT_DIGEST);
        registry.publish(publication(RUNNER_A, contract, observedAt));
        registry.publish(publication(RUNNER_B, contract, observedAt));
    }

    private PublicBundlePublication publication(
            RunnerId runnerId,
            CapabilityContract contract,
            Instant observedAt) {
        return new PublicBundlePublication(
                runnerId,
                "org:example",
                "internal.assets",
                "1.0.0",
                PUBLIC_BUNDLE_DIGEST,
                List.of(contract),
                RunnerState.ONLINE,
                1,
                1,
                observedAt);
    }

    private NewTask task(UUID taskId, Instant createdAt) {
        return task(taskId, createdAt, createdAt.plusSeconds(300));
    }

    private NewTask task(UUID taskId, Instant createdAt, Instant deadline) {
        return new NewTask(
                taskId,
                "org:example",
                CAPABILITY,
                CONTRACT_DIGEST,
                "runner-task-idempotency-" + taskId,
                "{\"assetId\":\"A-42\"}",
                "[]",
                "[]",
                0,
                createdAt,
                createdAt,
                deadline,
                TRACEPARENT);
    }

    private Claim claim(RunnerId runnerId, Instant now) {
        return new Claim(
                "org:example",
                runnerId,
                PUBLIC_BUNDLE_DIGEST,
                now,
                Duration.ofSeconds(30));
    }

    private Completion completion(Lease lease, String outcomeDigest) {
        return new Completion(
                lease.taskId(),
                lease.leaseId(),
                lease.fencingToken(),
                TaskState.SUCCEEDED,
                outcomeDigest,
                "{\"status\":\"ok\"}",
                null,
                lease.issuedAt().plusSeconds(2));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out waiting for test coordination");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("test coordination was interrupted", interrupted);
        }
    }
}

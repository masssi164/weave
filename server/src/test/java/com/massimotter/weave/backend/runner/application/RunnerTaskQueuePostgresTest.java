package com.massimotter.weave.backend.runner.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.massimotter.weave.backend.runner.adapter.JpaRunnerTaskStore;
import com.massimotter.weave.backend.runner.application.RunnerTaskQueue.LongPollClaim;
import com.massimotter.weave.backend.runner.application.RunnerTaskStore.Lease;
import com.massimotter.weave.backend.runner.application.RunnerTaskStore.NewTask;
import com.massimotter.weave.backend.runner.domain.RunnerControl.CapabilityId;
import com.massimotter.weave.backend.runner.domain.RunnerControl.CapabilityRef;
import com.massimotter.weave.backend.runner.domain.RunnerControl.RunnerId;
import com.massimotter.weave.backend.testing.JpaTestDatabase;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Tag("postgres")
class RunnerTaskQueuePostgresTest {

    private static final String BUNDLE_DIGEST =
            "sha256:dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd";
    private static final String TRACEPARENT =
            "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
    private static final CapabilityRef CAPABILITY =
            new CapabilityRef(new CapabilityId("internal.asset.lookup"), "1.0.0");
    private static final RunnerId RUNNER = new RunnerId("runner_long_poll_01");

    @Test
    void longPollWaitsOutsideTheDatabaseTransactionAndWakesAfterCommittedEnqueue()
            throws Exception {
        DataSource dataSource = JpaTestDatabase.entityFirstDataSource("runner-long-poll-wake");
        ObservingSignal signal = new ObservingSignal();
        RunnerTaskQueue queue = queue(dataSource, signal);
        UUID taskId = UUID.fromString("00000000-0000-0000-0000-000000001000");

        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            Future<Optional<Lease>> pending =
                    pool.submit(() -> queue.claim(claim(Duration.ofSeconds(5))));

            assertThat(signal.firstWait.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(signal.transactionSeen.get()).isFalse();
            assertThat(pending.isDone()).isFalse();

            queue.enqueue(task(taskId));

            Lease lease = pending.get(5, TimeUnit.SECONDS).orElseThrow();
            assertThat(lease.taskId()).isEqualTo(taskId);
            assertThat(lease.runnerId()).isEqualTo(RUNNER);
            assertThat(signal.waitCount.get()).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    void wakeSignalAloneIsOnlyAHintAndNeverInventsALease() throws Exception {
        DataSource dataSource = JpaTestDatabase.entityFirstDataSource("runner-long-poll-hint");
        ObservingSignal signal = new ObservingSignal();
        RunnerTaskQueue queue = queue(dataSource, signal);

        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            Future<Optional<Lease>> pending =
                    pool.submit(() -> queue.claim(claim(Duration.ofSeconds(2))));

            assertThat(signal.firstWait.await(3, TimeUnit.SECONDS)).isTrue();
            signal.signal();
            assertThat(signal.secondWait.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(signal.transactionSeen.get()).isFalse();

            assertThat(pending.get(5, TimeUnit.SECONDS)).isEmpty();
            assertThat(signal.waitCount.get()).isGreaterThanOrEqualTo(2);
        }
    }

    @Test
    void zeroWaitPerformsOneImmediateClaimWithoutEnteringTheWaiter() {
        DataSource dataSource = JpaTestDatabase.entityFirstDataSource("runner-zero-wait");
        ObservingSignal signal = new ObservingSignal();
        RunnerTaskQueue queue = queue(dataSource, signal);

        assertThat(queue.claim(claim(Duration.ZERO))).isEmpty();
        assertThat(signal.waitCount.get()).isZero();
    }

    private RunnerTaskQueue queue(DataSource dataSource, RunnerTaskAvailabilitySignal signal) {
        RunnerTaskStore store = JpaTestDatabase.transactional(
                dataSource,
                new JpaRunnerTaskStore(JpaTestDatabase.entityManager(dataSource)));
        return new RunnerTaskQueue(store, signal, Clock.systemUTC());
    }

    private LongPollClaim claim(Duration maximumWait) {
        return new LongPollClaim(
                "org:example",
                RUNNER,
                BUNDLE_DIGEST,
                Set.of(CAPABILITY),
                Duration.ofSeconds(30),
                maximumWait);
    }

    private NewTask task(UUID taskId) {
        Instant createdAt = Instant.now().minusSeconds(1);
        return new NewTask(
                taskId,
                "org:example",
                CAPABILITY,
                BUNDLE_DIGEST,
                "runner-long-poll-idempotency-" + taskId,
                "{\"assetId\":\"A-42\"}",
                "[]",
                "[]",
                0,
                createdAt,
                createdAt,
                createdAt.plusSeconds(300),
                TRACEPARENT);
    }

    private static final class ObservingSignal implements RunnerTaskAvailabilitySignal {

        private final InMemoryRunnerTaskAvailabilitySignal delegate =
                new InMemoryRunnerTaskAvailabilitySignal();
        private final AtomicInteger waitCount = new AtomicInteger();
        private final AtomicBoolean transactionSeen = new AtomicBoolean();
        private final CountDownLatch firstWait = new CountDownLatch(1);
        private final CountDownLatch secondWait = new CountDownLatch(1);

        @Override
        public long revision() {
            return delegate.revision();
        }

        @Override
        public void awaitChange(long observedRevision, Duration maximumWait)
                throws InterruptedException {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                transactionSeen.set(true);
            }
            int invocation = waitCount.incrementAndGet();
            if (invocation == 1) {
                firstWait.countDown();
            } else if (invocation == 2) {
                secondWait.countDown();
            }
            delegate.awaitChange(observedRevision, maximumWait);
        }

        @Override
        public void signal() {
            delegate.signal();
        }
    }
}

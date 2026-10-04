package com.massimotter.weave.backend.files.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.massimotter.weave.backend.files.application.NativeFilesBlobCleanupCoordinator.CleanupResult;
import com.massimotter.weave.backend.files.application.NativeFilesCleanupOutboxRepository.CleanupLease;
import com.massimotter.weave.backend.files.application.NativeFilesCleanupOutboxRepository.RetryOutcome;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NativeFilesBlobCleanupOutboxDispatcherTest {
    private static final Instant NOW = Instant.parse("2026-08-20T14:00:00Z");

    @Test
    void deliversOnlyCompleteCleanupAndRequeuesIncompleteOrFailedWork() {
        CleanupLease complete = lease(1, "op-complete", 1);
        CleanupLease incomplete = lease(2, "op-incomplete", 2);
        CleanupLease failed = lease(3, "op-failed", 3);
        FakeOutbox outbox = new FakeOutbox(List.of(complete, incomplete, failed));
        List<String> processed = new ArrayList<>();
        NativeFilesBlobCleanupOutboxDispatcher dispatcher = dispatcher(
                outbox,
                (operationRef, limit) -> {
                    assertThat(limit).isEqualTo(100);
                    processed.add(operationRef);
                    if (operationRef.equals("op-failed")) {
                        throw new NativeFilesBlobCleanupException("support-safe failure");
                    }
                    return result(operationRef, operationRef.equals("op-complete"));
                });

        NativeFilesBlobCleanupOutboxDispatcher.DispatchResult result = dispatcher.dispatchBatch();

        assertThat(processed).containsExactly("op-complete", "op-incomplete", "op-failed");
        assertThat(outbox.requestedLimit).isEqualTo(16);
        assertThat(outbox.requestedMaximumAttempts).isEqualTo(10_000);
        assertThat(outbox.requestedLeaseOwner).startsWith("native-files-cleanup-");
        assertThat(outbox.delivered).containsExactly(complete);
        assertThat(outbox.retried).extracting(RetryRequest::lease)
                .containsExactly(incomplete, failed);
        assertThat(outbox.retried).extracting(RetryRequest::settledAt)
                .containsExactly(NOW, NOW);
        assertThat(outbox.retried).extracting(RetryRequest::retryAt)
                .containsExactly(NOW.plusSeconds(1), NOW.plusSeconds(4));
        assertThat(outbox.retried).extracting(RetryRequest::diagnosticCode)
                .containsExactly("cleanup-incomplete", "cleanup-execution-failed");
        assertThat(result).isEqualTo(
                new NativeFilesBlobCleanupOutboxDispatcher.DispatchResult(3, 1, 2, 0, 0));
    }

    @Test
    void reportsFailedAndStaleLeaseOutcomesWithoutExposingPrivateValues() {
        CleanupLease failedClosed = lease(1, "op-max", 10_000);
        CleanupLease stale = lease(2, "op-stale", 4);
        FakeOutbox outbox = new FakeOutbox(List.of(failedClosed, stale));
        outbox.outcomes = List.of(RetryOutcome.FAILED_CLOSED, RetryOutcome.STALE_LEASE);
        NativeFilesBlobCleanupOutboxDispatcher dispatcher = dispatcher(
                outbox,
                (operationRef, limit) -> result(operationRef, false));

        NativeFilesBlobCleanupOutboxDispatcher.DispatchResult result = dispatcher.dispatchBatch();

        assertThat(result).isEqualTo(
                new NativeFilesBlobCleanupOutboxDispatcher.DispatchResult(2, 0, 0, 1, 1));
        assertThat(List.of(result.getClass().getRecordComponents()))
                .extracting(component -> component.getName())
                .noneMatch(name -> name.contains("binding") || name.contains("digest"));
    }

    @Test
    void capsExponentialFailureBackoff() {
        assertThat(NativeFilesBlobCleanupOutboxDispatcher.failureBackoff(1))
                .isEqualTo(Duration.ofSeconds(1));
        assertThat(NativeFilesBlobCleanupOutboxDispatcher.failureBackoff(3))
                .isEqualTo(Duration.ofSeconds(4));
        assertThat(NativeFilesBlobCleanupOutboxDispatcher.failureBackoff(100))
                .isEqualTo(Duration.ofMinutes(5));
    }

    private NativeFilesBlobCleanupOutboxDispatcher dispatcher(
            FakeOutbox outbox,
            NativeFilesBlobCleanupOutboxDispatcher.CleanupProcessor cleanup) {
        return new NativeFilesBlobCleanupOutboxDispatcher(
                outbox,
                cleanup,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private CleanupLease lease(long sequenceId, String operationRef, int attemptCount) {
        return new CleanupLease(
                sequenceId,
                "outbox-" + sequenceId,
                operationRef,
                operationRef.contains("denied") ? "operation.denied" : "operation.failed",
                attemptCount,
                "lease-token-" + sequenceId,
                "worker-1",
                NOW.plusSeconds(30));
    }

    private CleanupResult result(String operationRef, boolean complete) {
        return new CleanupResult(operationRef, 0, 0, 0, 0, 0, 0, 0, complete);
    }

    private static final class FakeOutbox implements NativeFilesCleanupOutboxRepository {
        private final List<CleanupLease> leases;
        private final List<CleanupLease> delivered = new ArrayList<>();
        private final List<RetryRequest> retried = new ArrayList<>();
        private List<RetryOutcome> outcomes = List.of();
        private int retryIndex;
        private int requestedLimit;
        private int requestedMaximumAttempts;
        private String requestedLeaseOwner;

        private FakeOutbox(List<CleanupLease> leases) {
            this.leases = List.copyOf(leases);
        }

        @Override
        public List<CleanupLease> leaseBatch(
                Instant now,
                Instant leaseUntil,
                String leaseOwner,
                int limit,
                int maximumAttempts) {
            requestedLeaseOwner = leaseOwner;
            requestedLimit = limit;
            requestedMaximumAttempts = maximumAttempts;
            return leases;
        }

        @Override public boolean markDelivered(CleanupLease lease, Instant deliveredAt) {
            delivered.add(lease);
            return true;
        }

        @Override
        public RetryOutcome retry(
                CleanupLease lease,
                Instant settledAt,
                Instant retryAt,
                String diagnosticCode,
                int maximumAttempts) {
            retried.add(new RetryRequest(lease, settledAt, retryAt, diagnosticCode));
            return outcomes.isEmpty() ? RetryOutcome.REQUEUED : outcomes.get(retryIndex++);
        }
    }

    private record RetryRequest(
            CleanupLease lease,
            Instant settledAt,
            Instant retryAt,
            String diagnosticCode) {
    }
}

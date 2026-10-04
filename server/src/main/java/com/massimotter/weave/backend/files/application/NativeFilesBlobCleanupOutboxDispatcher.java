package com.massimotter.weave.backend.files.application;

import static java.util.Objects.requireNonNull;

import com.massimotter.weave.backend.files.application.NativeFilesBlobCleanupCoordinator.CleanupResult;
import com.massimotter.weave.backend.files.application.NativeFilesCleanupOutboxRepository.CleanupLease;
import com.massimotter.weave.backend.files.application.NativeFilesCleanupOutboxRepository.RetryOutcome;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Bounded dispatcher for reserved terminal-failure native Files cleanup outbox work. */
@Service
public class NativeFilesBlobCleanupOutboxDispatcher {
    static final int DELIVERY_BATCH_LIMIT = 16;
    static final int CLEANUP_BINDING_LIMIT = 100;
    static final int MAXIMUM_DELIVERY_ATTEMPTS = 10_000;
    static final Duration LEASE_DURATION = Duration.ofSeconds(30);
    static final Duration INCOMPLETE_RETRY_DELAY = Duration.ofSeconds(1);
    static final Duration MAXIMUM_FAILURE_BACKOFF = Duration.ofMinutes(5);
    static final String INCOMPLETE_DIAGNOSTIC = "cleanup-incomplete";
    static final String FAILURE_DIAGNOSTIC = "cleanup-execution-failed";

    private final NativeFilesCleanupOutboxRepository outbox;
    private final CleanupProcessor cleanup;
    private final Clock clock;
    private final String leaseOwner;

    @Autowired
    public NativeFilesBlobCleanupOutboxDispatcher(
            NativeFilesCleanupOutboxRepository outbox,
            NativeFilesBlobCleanupCoordinator cleanup) {
        this(outbox, cleanup::process, Clock.systemUTC());
    }

    NativeFilesBlobCleanupOutboxDispatcher(
            NativeFilesCleanupOutboxRepository outbox,
            CleanupProcessor cleanup,
            Clock clock) {
        this.outbox = requireNonNull(outbox, "outbox");
        this.cleanup = requireNonNull(cleanup, "cleanup");
        this.clock = requireNonNull(clock, "clock");
        this.leaseOwner = "native-files-cleanup-" + UUID.randomUUID();
    }

    @Scheduled(initialDelay = 5_000, fixedDelay = 5_000)
    public void dispatchScheduledBatch() {
        dispatchBatch();
    }

    public DispatchResult dispatchBatch() {
        Instant leasedAt = clock.instant();
        List<CleanupLease> leases = outbox.leaseBatch(
                leasedAt,
                leasedAt.plus(LEASE_DURATION),
                leaseOwner,
                DELIVERY_BATCH_LIMIT,
                MAXIMUM_DELIVERY_ATTEMPTS);
        int delivered = 0;
        int requeued = 0;
        int failedClosed = 0;
        int stale = 0;
        for (CleanupLease lease : leases) {
            try {
                CleanupResult result = cleanup.process(
                        lease.operationRef(),
                        CLEANUP_BINDING_LIMIT);
                if (!lease.operationRef().equals(result.operationRef())) {
                    throw new NativeFilesBlobCleanupException(
                            "Files cleanup result does not match its outbox lease");
                }
                if (result.complete()) {
                    if (outbox.markDelivered(lease, clock.instant())) {
                        delivered++;
                    } else {
                        stale++;
                    }
                } else {
                    Instant settledAt = clock.instant();
                    RetryOutcome outcome = outbox.retry(
                            lease,
                            settledAt,
                            settledAt.plus(INCOMPLETE_RETRY_DELAY),
                            INCOMPLETE_DIAGNOSTIC,
                            MAXIMUM_DELIVERY_ATTEMPTS);
                    requeued += outcome == RetryOutcome.REQUEUED ? 1 : 0;
                    failedClosed += outcome == RetryOutcome.FAILED_CLOSED ? 1 : 0;
                    stale += outcome == RetryOutcome.STALE_LEASE ? 1 : 0;
                }
            } catch (RuntimeException failure) {
                Instant settledAt = clock.instant();
                RetryOutcome outcome = outbox.retry(
                        lease,
                        settledAt,
                        settledAt.plus(failureBackoff(lease.attemptCount())),
                        FAILURE_DIAGNOSTIC,
                        MAXIMUM_DELIVERY_ATTEMPTS);
                requeued += outcome == RetryOutcome.REQUEUED ? 1 : 0;
                failedClosed += outcome == RetryOutcome.FAILED_CLOSED ? 1 : 0;
                stale += outcome == RetryOutcome.STALE_LEASE ? 1 : 0;
            }
        }
        return new DispatchResult(
                leases.size(),
                delivered,
                requeued,
                failedClosed,
                stale);
    }

    static Duration failureBackoff(int attemptCount) {
        int exponent = Math.max(0, Math.min(attemptCount - 1, 16));
        Duration calculated = Duration.ofSeconds(1L << exponent);
        return calculated.compareTo(MAXIMUM_FAILURE_BACKOFF) > 0
                ? MAXIMUM_FAILURE_BACKOFF
                : calculated;
    }

    @FunctionalInterface
    interface CleanupProcessor {
        CleanupResult process(String operationRef, int limit);
    }

    /** Support-safe batch counters; no private binding or digest crosses this boundary. */
    public record DispatchResult(
            int leasedCount,
            int deliveredCount,
            int requeuedCount,
            int failedClosedCount,
            int staleLeaseCount) {
    }
}

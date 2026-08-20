package com.massimotter.weave.backend.files.application;

import java.time.Instant;
import java.util.List;

/** Durable lease boundary for reserved terminal-failure native Files cleanup work. */
public interface NativeFilesCleanupOutboxRepository {

    List<CleanupLease> leaseBatch(
            Instant now,
            Instant leaseUntil,
            String leaseOwner,
            int limit,
            int maximumAttempts);

    boolean markDelivered(CleanupLease lease, Instant deliveredAt);

    RetryOutcome retry(
            CleanupLease lease,
            Instant settledAt,
            Instant retryAt,
            String diagnosticCode,
            int maximumAttempts);

    enum RetryOutcome {
        REQUEUED,
        FAILED_CLOSED,
        STALE_LEASE
    }

    record CleanupLease(
            long sequenceId,
            String outboxRef,
            String operationRef,
            String eventType,
            int attemptCount,
            String leaseToken,
            String leaseOwner,
            Instant leaseUntil) {
        public CleanupLease {
            if (sequenceId < 1) {
                throw new IllegalArgumentException("sequenceId must be positive");
            }
            outboxRef = required(outboxRef, "outboxRef");
            operationRef = required(operationRef, "operationRef");
            if (!"operation.denied".equals(eventType)
                    && !"operation.failed".equals(eventType)) {
                throw new IllegalArgumentException("unsupported Files cleanup outbox event");
            }
            if (attemptCount < 1) {
                throw new IllegalArgumentException("attemptCount must be positive");
            }
            leaseToken = opaque(leaseToken, "leaseToken");
            leaseOwner = opaque(leaseOwner, "leaseOwner");
            leaseUntil = java.util.Objects.requireNonNull(
                    leaseUntil,
                    "leaseUntil must not be null");
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String opaque(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 255) {
            throw new IllegalArgumentException(field + " must be 1-255 opaque characters");
        }
        return value;
    }
}

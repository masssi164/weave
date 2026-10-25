package com.massimotter.weave.backend.transfer.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.massimotter.weave.backend.transfer.domain.TransferPrimitives.LossRecord;
import com.massimotter.weave.backend.transfer.domain.TransferPrimitives.TransferCheckpoint;
import com.massimotter.weave.backend.transfer.domain.TransferPrimitives.TransferFormatVersion;

/** Durable provider-neutral state for one resumable canonical transfer. */
public record TransferRun(
        Id id,
        String organizationRef,
        String canonicalModelVersion,
        TransferFormatVersion transferFormatVersion,
        long stateRevision,
        Status status,
        TransferCheckpoint sourceCheckpoint,
        long batchesApplied,
        long itemsApplied,
        List<LossRecord> losses,
        String lastAggregateDigest,
        String failureReason,
        Instant updatedAt) {

    public TransferRun {
        id = Objects.requireNonNull(id, "id must not be null");
        organizationRef = required(organizationRef, "organization ref");
        canonicalModelVersion = required(canonicalModelVersion, "canonical model version");
        transferFormatVersion = Objects.requireNonNull(
                transferFormatVersion,
                "transferFormatVersion must not be null");
        if (stateRevision < 0) {
            throw new IllegalArgumentException("state revision must not be negative");
        }
        status = Objects.requireNonNull(status, "status must not be null");
        if (batchesApplied < 0 || itemsApplied < 0) {
            throw new IllegalArgumentException("transfer counters must not be negative");
        }
        losses = List.copyOf(losses == null ? List.of() : losses);
        lastAggregateDigest = optionalText(lastAggregateDigest);
        failureReason = optionalText(failureReason);
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (status == Status.FAILED && failureReason == null) {
            throw new IllegalArgumentException("failed transfer requires a failure reason");
        }
        if (status != Status.FAILED && failureReason != null) {
            throw new IllegalArgumentException("only failed transfers may contain a failure reason");
        }
    }

    public static TransferRun initial(
            Id id,
            String organizationRef,
            String canonicalModelVersion,
            TransferFormatVersion transferFormatVersion,
            Instant now) {
        return new TransferRun(
                id,
                organizationRef,
                canonicalModelVersion,
                transferFormatVersion,
                0,
                Status.ACTIVE,
                null,
                0,
                0,
                List.of(),
                null,
                null,
                now);
    }

    public Optional<TransferCheckpoint> checkpoint() {
        return Optional.ofNullable(sourceCheckpoint);
    }

    public Optional<String> failure() {
        return Optional.ofNullable(failureReason);
    }

    public TransferRun advance(
            TransferCheckpoint nextCheckpoint,
            int appliedItems,
            List<LossRecord> accumulatedLosses,
            String aggregateDigest,
            boolean complete,
            Instant now) {
        requireActive();
        if (appliedItems < 0) {
            throw new IllegalArgumentException("appliedItems must not be negative");
        }
        return new TransferRun(
                id,
                organizationRef,
                canonicalModelVersion,
                transferFormatVersion,
                stateRevision + 1,
                complete ? Status.COMPLETED : Status.ACTIVE,
                nextCheckpoint,
                batchesApplied + 1,
                itemsApplied + appliedItems,
                accumulatedLosses,
                required(aggregateDigest, "aggregate digest"),
                null,
                now);
    }

    public TransferRun fail(
            List<LossRecord> accumulatedLosses,
            String aggregateDigest,
            String reason,
            Instant now) {
        requireActive();
        return new TransferRun(
                id,
                organizationRef,
                canonicalModelVersion,
                transferFormatVersion,
                stateRevision + 1,
                Status.FAILED,
                sourceCheckpoint,
                batchesApplied,
                itemsApplied,
                accumulatedLosses,
                required(aggregateDigest, "aggregate digest"),
                required(reason, "failure reason"),
                now);
    }

    private void requireActive() {
        if (status != Status.ACTIVE) {
            throw new IllegalStateException("transfer run is not active: " + status);
        }
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    public enum Status {
        ACTIVE,
        COMPLETED,
        FAILED
    }

    public record Id(String value) {
        public Id {
            value = required(value, "transfer run id");
        }
    }
}

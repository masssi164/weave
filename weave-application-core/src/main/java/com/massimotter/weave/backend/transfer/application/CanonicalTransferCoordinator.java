package com.massimotter.weave.backend.transfer.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.massimotter.weave.backend.transfer.domain.CanonicalObjectId;
import com.massimotter.weave.backend.transfer.domain.CanonicalTransferEnvelope;
import com.massimotter.weave.backend.transfer.domain.CanonicalTransferItem;
import com.massimotter.weave.backend.transfer.domain.TransferPrimitives.LossRecord;
import com.massimotter.weave.backend.transfer.domain.TransferPrimitives.TransferCheckpoint;
import com.massimotter.weave.backend.transfer.domain.TransferPrimitives.TransferFormatVersion;
import com.massimotter.weave.backend.transfer.domain.TransferRun;
import com.massimotter.weave.backend.transfer.port.ProviderSourceConnector;
import com.massimotter.weave.backend.transfer.port.ProviderTargetConnector;
import com.massimotter.weave.backend.transfer.port.TransferRunRepository;

/** Executes bounded, resumable source -> canonical -> target transfer batches. */
public final class CanonicalTransferCoordinator<T extends CanonicalTransferItem> {
    private final ProviderSourceConnector<T> source;
    private final ProviderTargetConnector<T> target;
    private final TransferRunRepository runs;
    private final Clock clock;

    public CanonicalTransferCoordinator(
            ProviderSourceConnector<T> source,
            ProviderTargetConnector<T> target,
            TransferRunRepository runs,
            Clock clock) {
        this.source = Objects.requireNonNull(source, "source must not be null");
        this.target = Objects.requireNonNull(target, "target must not be null");
        this.runs = Objects.requireNonNull(runs, "runs must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public TransferRun runToCompletion(Command command) {
        TransferRun latest = current(command);
        for (int index = 0; index < command.maxBatches(); index++) {
            if (latest.status() != TransferRun.Status.ACTIVE) {
                return latest;
            }
            latest = runNextBatch(command);
        }
        if (latest.status() == TransferRun.Status.ACTIVE) {
            throw new IllegalStateException(
                    "transfer did not complete within maxBatches=" + command.maxBatches());
        }
        return latest;
    }

    public TransferRun runNextBatch(Command command) {
        Objects.requireNonNull(command, "command must not be null");
        TransferRun current = current(command);
        if (current.status() != TransferRun.Status.ACTIVE) {
            return current;
        }

        ProviderSourceConnector.SourceBatch<T> batch = source.read(
                command.organizationRef(),
                current.checkpoint());
        ensureCheckpointProgress(current.sourceCheckpoint(), batch.nextCheckpoint(), batch.complete());

        List<CanonicalObjectId> objectIds = uniqueObjectIds(batch.items());
        LossAccounting.validateObjectReferences(objectIds, batch.losses());
        List<LossRecord> sourceLosses = LossAccounting.mergeAndValidate(List.of(batch.losses()));
        String aggregateDigest = CanonicalTransferDigests.aggregate(
                command.transferFormatVersion(),
                command.canonicalModelVersion(),
                command.organizationRef(),
                batch.items(),
                sourceLosses);
        CanonicalTransferEnvelope<T> envelope = new CanonicalTransferEnvelope<>(
                command.transferFormatVersion(),
                command.canonicalModelVersion(),
                command.organizationRef(),
                batch.items(),
                sourceLosses,
                aggregateDigest,
                batch.nextCheckpoint());

        ProviderTargetConnector.Preflight preflight = Objects.requireNonNull(
                target.preflight(envelope),
                "target preflight must not return null");
        LossAccounting.validateObjectReferences(objectIds, preflight.losses());
        List<LossRecord> batchLosses = LossAccounting.mergeAndValidate(
                List.of(sourceLosses, preflight.losses()));
        List<LossRecord> accumulatedLosses = LossAccounting.mergeAndValidate(
                List.of(current.losses(), batchLosses));
        if (!preflight.accepted()) {
            TransferRun failed = current.fail(
                    accumulatedLosses,
                    aggregateDigest,
                    preflight.rejectionReason(),
                    clock.instant());
            runs.save(failed, current.stateRevision());
            return failed;
        }

        String idempotencyKey = CanonicalTransferDigests.idempotencyKey(
                current.id(),
                current.batchesApplied() + 1,
                aggregateDigest);
        ProviderTargetConnector.ApplyReceipt receipt = Objects.requireNonNull(
                target.apply(envelope, idempotencyKey),
                "target apply must not return null");
        if (!aggregateDigest.equals(receipt.aggregateDigest())) {
            throw new IllegalStateException("target acknowledgement digest does not match the canonical envelope");
        }
        if (receipt.appliedItems() != batch.items().size()) {
            throw new IllegalStateException("target acknowledgement item count does not match the canonical batch");
        }

        ProviderTargetConnector.Verification verification = Objects.requireNonNull(
                target.verify(receipt),
                "target verification must not return null");
        LossAccounting.validateObjectReferences(objectIds, verification.losses());
        batchLosses = LossAccounting.mergeAndValidate(
                List.of(sourceLosses, preflight.losses(), verification.losses()));
        accumulatedLosses = LossAccounting.mergeAndValidate(List.of(current.losses(), batchLosses));
        if (!verification.equivalent() || verification.verifiedItems() != batch.items().size()) {
            String reason = verification.failureReason() == null
                    ? "target verification count does not match the canonical batch"
                    : verification.failureReason();
            TransferRun failed = current.fail(
                    accumulatedLosses,
                    aggregateDigest,
                    reason,
                    clock.instant());
            runs.save(failed, current.stateRevision());
            return failed;
        }

        TransferRun advanced = current.advance(
                batch.nextCheckpoint(),
                receipt.appliedItems(),
                accumulatedLosses,
                aggregateDigest,
                batch.complete(),
                clock.instant());
        runs.save(advanced, current.stateRevision());
        return advanced;
    }

    private TransferRun current(Command command) {
        Objects.requireNonNull(command, "command must not be null");
        TransferRun current = runs.findById(command.runId()).orElseGet(() -> TransferRun.initial(
                command.runId(),
                command.organizationRef(),
                command.canonicalModelVersion(),
                command.transferFormatVersion(),
                clock.instant()));
        if (!current.organizationRef().equals(command.organizationRef())
                || !current.canonicalModelVersion().equals(command.canonicalModelVersion())
                || !current.transferFormatVersion().equals(command.transferFormatVersion())) {
            throw new IllegalStateException("transfer run coordinates do not match the existing durable state");
        }
        return current;
    }

    private static <T extends CanonicalTransferItem> List<CanonicalObjectId> uniqueObjectIds(List<T> items) {
        List<CanonicalObjectId> result = new ArrayList<>(items.size());
        Set<CanonicalObjectId> unique = new HashSet<>();
        for (T item : items) {
            CanonicalObjectId id = Objects.requireNonNull(item, "canonical item must not be null")
                    .metadata()
                    .id();
            if (!unique.add(id)) {
                throw new IllegalStateException("canonical batch contains duplicate object id: " + id.value());
            }
            result.add(id);
        }
        return List.copyOf(result);
    }

    private static void ensureCheckpointProgress(
            TransferCheckpoint previous,
            TransferCheckpoint next,
            boolean complete) {
        if (!complete && Objects.equals(previous, next)) {
            throw new IllegalStateException("incomplete source batch did not advance its checkpoint");
        }
    }

    public record Command(
            TransferRun.Id runId,
            String organizationRef,
            String canonicalModelVersion,
            TransferFormatVersion transferFormatVersion,
            int maxBatches) {
        public Command {
            runId = Objects.requireNonNull(runId, "runId must not be null");
            organizationRef = required(organizationRef, "organization ref");
            canonicalModelVersion = required(canonicalModelVersion, "canonical model version");
            transferFormatVersion = Objects.requireNonNull(
                    transferFormatVersion,
                    "transferFormatVersion must not be null");
            if (maxBatches < 1) {
                throw new IllegalArgumentException("maxBatches must be positive");
            }
        }

        private static String required(String value, String field) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(field + " must not be blank");
            }
            return value.trim();
        }
    }
}

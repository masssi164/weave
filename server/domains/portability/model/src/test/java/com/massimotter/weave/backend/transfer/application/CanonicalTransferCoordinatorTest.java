package com.massimotter.weave.backend.transfer.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.massimotter.weave.backend.transfer.domain.CanonicalObjectId;
import com.massimotter.weave.backend.transfer.domain.CanonicalTransferEnvelope;
import com.massimotter.weave.backend.transfer.domain.CanonicalTransferItem;
import com.massimotter.weave.backend.transfer.domain.TransferPrimitives.Domain;
import com.massimotter.weave.backend.transfer.domain.TransferPrimitives.Lifecycle;
import com.massimotter.weave.backend.transfer.domain.TransferPrimitives.LossClass;
import com.massimotter.weave.backend.transfer.domain.TransferPrimitives.LossRecord;
import com.massimotter.weave.backend.transfer.domain.TransferPrimitives.ObjectMetadata;
import com.massimotter.weave.backend.transfer.domain.TransferPrimitives.Provenance;
import com.massimotter.weave.backend.transfer.domain.TransferPrimitives.TransferCheckpoint;
import com.massimotter.weave.backend.transfer.domain.TransferPrimitives.TransferFormatVersion;
import com.massimotter.weave.backend.transfer.domain.TransferRun;
import com.massimotter.weave.backend.transfer.port.ProviderSourceConnector;
import com.massimotter.weave.backend.transfer.port.ProviderTargetConnector;
import com.massimotter.weave.backend.transfer.port.TransferRunRepository;

class CanonicalTransferCoordinatorTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-18T12:00:00Z"),
            ZoneOffset.UTC);

    @Test
    void transfersTypedBatchesAndAccountsForEveryFidelityClass() {
        TestItem file = item("file-1", Domain.FILES, "file", "sha-file");
        TestItem event = item("event-1", Domain.CALENDAR, "event", "sha-event");
        TestItem message = item("message-1", Domain.CHAT, "message", "sha-message");

        List<LossRecord> sourceLosses = List.of(
                loss(file, "permissions", LossClass.LOSSY),
                loss(file, "share-link", LossClass.UNSUPPORTED),
                loss(file, "vendor-version", LossClass.VENDOR_LOCKED));
        List<LossRecord> targetLosses = List.of(
                loss(event, "summary", LossClass.PORTABLE),
                loss(event, "alarm", LossClass.MANUAL_REVIEW),
                loss(event, "x-provider-property", LossClass.ARCHIVE_ONLY));

        DeterministicSource source = new DeterministicSource(List.of(
                new ProviderSourceConnector.SourceBatch<>(
                        List.of(file, event),
                        sourceLosses,
                        new TransferCheckpoint("after-first", 1),
                        false),
                new ProviderSourceConnector.SourceBatch<>(
                        List.of(message),
                        List.of(),
                        null,
                        true)));
        DeterministicTarget target = new DeterministicTarget(targetLosses, false);
        InMemoryRunRepository repository = new InMemoryRunRepository();
        CanonicalTransferCoordinator<TestItem> coordinator = new CanonicalTransferCoordinator<>(
                source,
                target,
                repository,
                CLOCK);

        TransferRun result = coordinator.runToCompletion(command("run-complete", 3));

        assertEquals(TransferRun.Status.COMPLETED, result.status());
        assertEquals(2L, result.batchesApplied());
        assertEquals(3L, result.itemsApplied());
        assertEquals(2L, result.stateRevision());
        assertEquals(3, target.uniqueItems());
        assertTrue(result.checkpoint().isEmpty());
        Set<LossClass> actualClasses = result.losses().stream()
                .map(LossRecord::classification)
                .collect(Collectors.toSet());
        assertEquals(EnumSet.allOf(LossClass.class), actualClasses);
    }

    @Test
    void retriesTheSameIdempotencyKeyAfterTargetMutationWithoutDuplicates() {
        TestItem file = item("file-retry", Domain.FILES, "file", "sha-retry");
        DeterministicSource source = new DeterministicSource(List.of(
                new ProviderSourceConnector.SourceBatch<>(List.of(file), List.of(), null, true)));
        DeterministicTarget target = new DeterministicTarget(List.of(), true);
        InMemoryRunRepository repository = new InMemoryRunRepository();
        CanonicalTransferCoordinator<TestItem> coordinator = new CanonicalTransferCoordinator<>(
                source,
                target,
                repository,
                CLOCK);
        CanonicalTransferCoordinator.Command command = command("run-retry", 2);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> coordinator.runNextBatch(command));

        assertEquals("injected failure after target mutation", failure.getMessage());
        assertTrue(repository.findById(command.runId()).isEmpty());
        assertEquals(1, target.uniqueItems());
        assertEquals(1, target.mutationBatches());

        TransferRun completed = coordinator.runToCompletion(command);

        assertEquals(TransferRun.Status.COMPLETED, completed.status());
        assertEquals(1L, completed.itemsApplied());
        assertEquals(1, target.uniqueItems());
        assertEquals(1, target.mutationBatches());
        assertEquals(2, target.applyCalls());
    }

    @Test
    void rejectsConflictingLossClassificationsBeforeTargetMutation() {
        TestItem event = item("event-conflict", Domain.CALENDAR, "event", "sha-conflict");
        LossRecord sourceLoss = loss(event, "alarm", LossClass.LOSSY);
        LossRecord targetLoss = loss(event, "alarm", LossClass.UNSUPPORTED);
        DeterministicSource source = new DeterministicSource(List.of(
                new ProviderSourceConnector.SourceBatch<>(
                        List.of(event),
                        List.of(sourceLoss),
                        null,
                        true)));
        DeterministicTarget target = new DeterministicTarget(List.of(targetLoss), false);
        InMemoryRunRepository repository = new InMemoryRunRepository();
        CanonicalTransferCoordinator<TestItem> coordinator = new CanonicalTransferCoordinator<>(
                source,
                target,
                repository,
                CLOCK);
        CanonicalTransferCoordinator.Command command = command("run-conflict", 1);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> coordinator.runNextBatch(command));

        assertTrue(failure.getMessage().contains("conflicting loss classification"));
        assertEquals(0, target.applyCalls());
        assertTrue(repository.findById(command.runId()).isEmpty());
    }

    private static CanonicalTransferCoordinator.Command command(String id, int maxBatches) {
        return new CanonicalTransferCoordinator.Command(
                new TransferRun.Id(id),
                "org-1",
                "core-v1",
                new TransferFormatVersion(1),
                maxBatches);
    }

    private static TestItem item(String id, Domain domain, String kind, String digest) {
        return new TestItem(
                new ObjectMetadata(
                        new CanonicalObjectId(id),
                        domain,
                        kind,
                        1,
                        Lifecycle.ACTIVE,
                        Provenance.IMPORTED,
                        Instant.parse("2026-08-18T10:00:00Z"),
                        digest,
                        List.of()),
                "payload-" + id);
    }

    private static LossRecord loss(TestItem item, String field, LossClass classification) {
        return new LossRecord(
                item.metadata().id(),
                field,
                classification,
                "fixture outcome for " + field);
    }

    private record TestItem(ObjectMetadata metadata, String payload) implements CanonicalTransferItem {
        private TestItem {
            if (metadata == null) {
                throw new IllegalArgumentException("metadata must not be null");
            }
            if (payload == null || payload.isBlank()) {
                throw new IllegalArgumentException("payload must not be blank");
            }
            payload = payload.trim();
        }
    }

    private static final class DeterministicSource implements ProviderSourceConnector<TestItem> {
        private final List<SourceBatch<TestItem>> batches;

        private DeterministicSource(List<SourceBatch<TestItem>> batches) {
            this.batches = List.copyOf(batches);
        }

        @Override
        public SourceBatch<TestItem> read(
                String organizationRef,
                Optional<TransferCheckpoint> after) {
            if (!"org-1".equals(organizationRef)) {
                throw new IllegalArgumentException("unexpected organization");
            }
            int index = after.map(checkpoint -> Math.toIntExact(checkpoint.sequence())).orElse(0);
            if (index >= batches.size()) {
                return new SourceBatch<>(List.of(), List.of(), null, true);
            }
            return batches.get(index);
        }
    }

    private static final class DeterministicTarget implements ProviderTargetConnector<TestItem> {
        private final List<LossRecord> configuredPreflightLosses;
        private final Map<String, TestItem> items = new HashMap<>();
        private final Map<String, ApplyReceipt> receiptsByIdempotencyKey = new HashMap<>();
        private final Map<String, CanonicalTransferEnvelope<TestItem>> envelopesByReceipt = new HashMap<>();
        private boolean failAfterMutationOnce;
        private int mutationBatches;
        private int applyCalls;

        private DeterministicTarget(List<LossRecord> configuredPreflightLosses, boolean failAfterMutationOnce) {
            this.configuredPreflightLosses = List.copyOf(configuredPreflightLosses);
            this.failAfterMutationOnce = failAfterMutationOnce;
        }

        @Override
        public Preflight preflight(CanonicalTransferEnvelope<TestItem> envelope) {
            Set<CanonicalObjectId> ids = envelope.items().stream()
                    .map(item -> item.metadata().id())
                    .collect(Collectors.toSet());
            List<LossRecord> relevant = configuredPreflightLosses.stream()
                    .filter(loss -> ids.contains(loss.objectId()))
                    .toList();
            return Preflight.accepted(relevant);
        }

        @Override
        public ApplyReceipt apply(
                CanonicalTransferEnvelope<TestItem> envelope,
                String idempotencyKey) {
            applyCalls++;
            ApplyReceipt existingReceipt = receiptsByIdempotencyKey.get(idempotencyKey);
            if (existingReceipt != null) {
                return existingReceipt;
            }
            for (TestItem item : envelope.items()) {
                TestItem existing = items.putIfAbsent(item.metadata().id().value(), item);
                if (existing != null && !existing.equals(item)) {
                    throw new IllegalStateException("idempotency conflict for " + item.metadata().id().value());
                }
            }
            ApplyReceipt receipt = new ApplyReceipt(
                    "receipt-" + (receiptsByIdempotencyKey.size() + 1),
                    envelope.aggregateDigest(),
                    envelope.items().size());
            receiptsByIdempotencyKey.put(idempotencyKey, receipt);
            envelopesByReceipt.put(receipt.receiptId(), envelope);
            mutationBatches++;
            if (failAfterMutationOnce) {
                failAfterMutationOnce = false;
                throw new IllegalStateException("injected failure after target mutation");
            }
            return receipt;
        }

        @Override
        public Verification verify(ApplyReceipt receipt) {
            CanonicalTransferEnvelope<TestItem> envelope = envelopesByReceipt.get(receipt.receiptId());
            if (envelope == null) {
                return Verification.failed(0, List.of(), "receipt is unknown");
            }
            List<TestItem> mismatches = new ArrayList<>();
            for (TestItem expected : envelope.items()) {
                TestItem actual = items.get(expected.metadata().id().value());
                if (!expected.equals(actual)) {
                    mismatches.add(expected);
                }
            }
            if (!mismatches.isEmpty()) {
                return Verification.failed(
                        envelope.items().size() - mismatches.size(),
                        List.of(),
                        "target readback differs from canonical items");
            }
            return Verification.equivalent(envelope.items().size(), List.of());
        }

        private int uniqueItems() {
            return items.size();
        }

        private int mutationBatches() {
            return mutationBatches;
        }

        private int applyCalls() {
            return applyCalls;
        }
    }

    private static final class InMemoryRunRepository implements TransferRunRepository {
        private final Map<TransferRun.Id, TransferRun> runs = new HashMap<>();

        @Override
        public Optional<TransferRun> findById(TransferRun.Id id) {
            return Optional.ofNullable(runs.get(id));
        }

        @Override
        public void save(TransferRun run, long expectedPreviousRevision) {
            TransferRun existing = runs.get(run.id());
            long actualRevision = existing == null ? 0 : existing.stateRevision();
            if (actualRevision != expectedPreviousRevision) {
                throw new IllegalStateException("stale transfer run revision");
            }
            if (run.stateRevision() != expectedPreviousRevision + 1) {
                throw new IllegalStateException("new transfer run revision is not monotonic");
            }
            runs.put(run.id(), run);
        }
    }
}

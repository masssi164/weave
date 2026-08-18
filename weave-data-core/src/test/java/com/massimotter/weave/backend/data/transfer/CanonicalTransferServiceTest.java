package com.massimotter.weave.backend.data.transfer;

import static com.massimotter.weave.backend.data.domain.CanonicalData.Checkpoint;
import static com.massimotter.weave.backend.data.domain.CanonicalData.CheckpointKey;
import static com.massimotter.weave.backend.data.domain.CanonicalData.Dependency;
import static com.massimotter.weave.backend.data.domain.CanonicalData.Domain;
import static com.massimotter.weave.backend.data.domain.CanonicalData.IdempotencyKey;
import static com.massimotter.weave.backend.data.domain.CanonicalData.Lifecycle;
import static com.massimotter.weave.backend.data.domain.CanonicalData.LossClass;
import static com.massimotter.weave.backend.data.domain.CanonicalData.LossObservation;
import static com.massimotter.weave.backend.data.domain.CanonicalData.ModelVersion;
import static com.massimotter.weave.backend.data.domain.CanonicalData.ObjectId;
import static com.massimotter.weave.backend.data.domain.CanonicalData.Provenance;
import static com.massimotter.weave.backend.data.domain.CanonicalData.ProvenanceKind;
import static com.massimotter.weave.backend.data.domain.CanonicalData.Revision;
import static com.massimotter.weave.backend.data.domain.CanonicalData.Scope;
import static com.massimotter.weave.backend.data.domain.CanonicalData.TransferFormatVersion;
import static com.massimotter.weave.backend.data.domain.CanonicalData.TransferRunId;
import static com.massimotter.weave.backend.data.domain.CanonicalData.TransferStage;
import static com.massimotter.weave.backend.data.transfer.CanonicalTransfer.ApplyResult;
import static com.massimotter.weave.backend.data.transfer.CanonicalTransfer.CanonicalStore;
import static com.massimotter.weave.backend.data.transfer.CanonicalTransfer.CanonicalTransferService;
import static com.massimotter.weave.backend.data.transfer.CanonicalTransfer.CheckpointRepository;
import static com.massimotter.weave.backend.data.transfer.CanonicalTransfer.ExportResult;
import static com.massimotter.weave.backend.data.transfer.CanonicalTransfer.ImportResult;
import static com.massimotter.weave.backend.data.transfer.CanonicalTransfer.PreflightResult;
import static com.massimotter.weave.backend.data.transfer.CanonicalTransfer.ProviderSourceConnector;
import static com.massimotter.weave.backend.data.transfer.CanonicalTransfer.ProviderTargetConnector;
import static com.massimotter.weave.backend.data.transfer.CanonicalTransfer.SourcePage;
import static com.massimotter.weave.backend.data.transfer.CanonicalTransfer.TransferBatch;
import static com.massimotter.weave.backend.data.transfer.CanonicalTransfer.TransferObject;
import static com.massimotter.weave.backend.data.transfer.CanonicalTransfer.UnaccountedDataLossException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CanonicalTransferServiceTest {

    private static final Scope SCOPE = new Scope("org-1", "space-1");
    private static final ModelVersion MODEL_VERSION = new ModelVersion("1");
    private static final TransferFormatVersion FORMAT_VERSION = new TransferFormatVersion(1);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-18T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void importsThenResumesExportWithoutDuplicateTargetMutation() {
        List<TestObject> sourceObjects = List.of(
                object(Domain.FILES, "file-1", "files:one"),
                object(Domain.CALENDAR, "event-1", "calendar:rich-reminder"),
                object(Domain.CHAT, "event-1", "chat:one"));
        InMemoryCheckpoints checkpoints = new InMemoryCheckpoints();
        InMemoryStore store = new InMemoryStore();
        CanonicalTransferService service = new CanonicalTransferService(checkpoints, CLOCK);

        ImportResult imported = service.importFromProvider(
                new TransferRunId("import-provider-a"),
                SCOPE,
                new ListSource(sourceObjects),
                store,
                2);

        assertEquals(3, imported.importedObjects());
        assertTrue(imported.checkpoint().complete());
        assertEquals(3, store.size());

        TransferRunId exportRun = new TransferRunId("export-provider-b");
        NarrowTarget target = new NarrowTarget(true);

        assertThrows(
                SimulatedCrash.class,
                () -> service.exportToProvider(
                        exportRun,
                        SCOPE,
                        MODEL_VERSION,
                        FORMAT_VERSION,
                        store,
                        target,
                        2));
        assertTrue(checkpoints.find(new CheckpointKey(exportRun, TransferStage.EXPORT)).isEmpty());

        ExportResult exported = service.exportToProvider(
                exportRun,
                SCOPE,
                MODEL_VERSION,
                FORMAT_VERSION,
                store,
                target,
                2);

        assertEquals(3, exported.appliedObjects());
        assertTrue(exported.checkpoint().complete());
        assertEquals(imported.aggregateDigest(), exported.aggregateDigest());
        assertEquals(2, target.uniqueApplyMutations());
        assertEquals(3, target.size());
        assertEquals(1, exported.losses().size());
        assertEquals(LossClass.ARCHIVE_ONLY, exported.losses().getFirst().classification());
    }

    @Test
    void rejectsReadbackDriftWithoutExplicitLossClassification() {
        InMemoryCheckpoints checkpoints = new InMemoryCheckpoints();
        InMemoryStore store = new InMemoryStore();
        CanonicalTransferService service = new CanonicalTransferService(checkpoints, CLOCK);
        service.importFromProvider(
                new TransferRunId("import-silent-drift"),
                SCOPE,
                new ListSource(List.of(object(Domain.FILES, "file-1", "original"))),
                store,
                10);

        TransferRunId exportRun = new TransferRunId("export-silent-drift");
        assertThrows(
                UnaccountedDataLossException.class,
                () -> service.exportToProvider(
                        exportRun,
                        SCOPE,
                        MODEL_VERSION,
                        FORMAT_VERSION,
                        store,
                        new SilentDriftTarget(),
                        10));
        assertTrue(checkpoints.find(new CheckpointKey(exportRun, TransferStage.EXPORT)).isEmpty());
    }

    @Test
    void carriesAllSixFidelityClassesThroughExecutableTargetPreflight() {
        List<TestObject> objects = new ArrayList<>();
        LossClass[] classes = LossClass.values();
        for (int index = 0; index < classes.length; index++) {
            objects.add(object(Domain.FILES, "file-" + index, "digest-" + index));
        }

        InMemoryCheckpoints checkpoints = new InMemoryCheckpoints();
        InMemoryStore store = new InMemoryStore();
        CanonicalTransferService service = new CanonicalTransferService(checkpoints, CLOCK);
        service.importFromProvider(
                new TransferRunId("import-all-loss-classes"),
                SCOPE,
                new ListSource(objects),
                store,
                10);

        ExportResult result = service.exportToProvider(
                new TransferRunId("export-all-loss-classes"),
                SCOPE,
                MODEL_VERSION,
                FORMAT_VERSION,
                store,
                new AllClassTarget(),
                10);

        EnumSet<LossClass> observed = EnumSet.noneOf(LossClass.class);
        for (LossObservation loss : result.losses()) {
            observed.add(loss.classification());
        }
        assertEquals(EnumSet.allOf(LossClass.class), observed);
    }

    private static TestObject object(Domain domain, String value, String digest) {
        return new TestObject(
                new ObjectId(domain, value),
                MODEL_VERSION,
                new Revision(1),
                Lifecycle.ACTIVE,
                new Provenance(ProvenanceKind.IMPORTED, "provider-a", Instant.EPOCH),
                digest,
                List.of());
    }

    private record TestObject(
            ObjectId objectId,
            ModelVersion modelVersion,
            Revision revision,
            Lifecycle lifecycle,
            Provenance provenance,
            String canonicalDigest,
            List<Dependency> dependencies) implements TransferObject {
        private TestObject {
            dependencies = List.copyOf(dependencies);
        }

        private TestObject withDigest(String digest) {
            return new TestObject(
                    objectId,
                    modelVersion,
                    revision,
                    lifecycle,
                    provenance,
                    digest,
                    dependencies);
        }
    }

    private static final class InMemoryCheckpoints implements CheckpointRepository {
        private final Map<CheckpointKey, Checkpoint> values = new HashMap<>();

        @Override
        public Optional<Checkpoint> find(CheckpointKey key) {
            return Optional.ofNullable(values.get(key));
        }

        @Override
        public void save(CheckpointKey key, Checkpoint checkpoint) {
            values.put(key, checkpoint);
        }
    }

    private static final class ListSource implements ProviderSourceConnector<TestObject> {
        private final List<TestObject> objects;

        private ListSource(List<TestObject> objects) {
            this.objects = List.copyOf(objects);
        }

        @Override
        public SourcePage<TestObject> readCanonicalPage(
                Scope scope, Checkpoint checkpoint, int limit) {
            return page(objects, checkpoint, limit);
        }
    }

    private static final class InMemoryStore implements CanonicalStore<TestObject> {
        private final Map<ObjectId, TestObject> objects = new LinkedHashMap<>();
        private final Set<IdempotencyKey> imports = new HashSet<>();

        @Override
        public void importBatch(
                Scope scope, List<TestObject> imported, IdempotencyKey idempotencyKey) {
            if (!imports.add(idempotencyKey)) {
                return;
            }
            for (TestObject object : imported) {
                objects.put(object.objectId(), object);
            }
        }

        @Override
        public SourcePage<TestObject> readCanonicalPage(
                Scope scope, Checkpoint checkpoint, int limit) {
            return page(List.copyOf(objects.values()), checkpoint, limit);
        }

        private int size() {
            return objects.size();
        }
    }

    private static final class NarrowTarget implements ProviderTargetConnector<TestObject> {
        private final Map<ObjectId, TestObject> objects = new LinkedHashMap<>();
        private final Map<IdempotencyKey, ApplyResult> results = new HashMap<>();
        private final boolean failAfterFirstApply;
        private boolean crashed;
        private int uniqueApplyMutations;

        private NarrowTarget(boolean failAfterFirstApply) {
            this.failAfterFirstApply = failAfterFirstApply;
        }

        @Override
        public PreflightResult preflight(TransferBatch<TestObject> batch) {
            List<LossObservation> losses = batch.objects().stream()
                    .filter(object -> object.objectId().domain() == Domain.CALENDAR)
                    .map(object -> new LossObservation(
                            object.objectId(),
                            "extensions.vendorReminder",
                            LossClass.ARCHIVE_ONLY,
                            "target provider stores the unsupported reminder only in the archive envelope"))
                    .toList();
            return PreflightResult.accepted(losses);
        }

        @Override
        public ApplyResult apply(
                TransferBatch<TestObject> batch, IdempotencyKey idempotencyKey) {
            ApplyResult existing = results.get(idempotencyKey);
            if (existing != null) {
                return existing;
            }

            List<ObjectId> applied = new ArrayList<>();
            for (TestObject object : batch.objects()) {
                TestObject stored = object.objectId().domain() == Domain.CALENDAR
                        ? object.withDigest("calendar:narrow")
                        : object;
                objects.put(stored.objectId(), stored);
                applied.add(stored.objectId());
            }
            ApplyResult result = new ApplyResult(applied, List.of());
            results.put(idempotencyKey, result);
            uniqueApplyMutations++;
            if (failAfterFirstApply && !crashed) {
                crashed = true;
                throw new SimulatedCrash();
            }
            return result;
        }

        @Override
        public List<TestObject> readBack(Scope scope, List<ObjectId> objectIds) {
            return objectIds.stream().map(objects::get).toList();
        }

        private int uniqueApplyMutations() {
            return uniqueApplyMutations;
        }

        private int size() {
            return objects.size();
        }
    }

    private static final class SilentDriftTarget implements ProviderTargetConnector<TestObject> {
        private final Map<ObjectId, TestObject> objects = new HashMap<>();

        @Override
        public PreflightResult preflight(TransferBatch<TestObject> batch) {
            return PreflightResult.accepted(List.of());
        }

        @Override
        public ApplyResult apply(
                TransferBatch<TestObject> batch, IdempotencyKey idempotencyKey) {
            List<ObjectId> applied = new ArrayList<>();
            for (TestObject object : batch.objects()) {
                TestObject changed = object.withDigest("silently-changed");
                objects.put(changed.objectId(), changed);
                applied.add(changed.objectId());
            }
            return new ApplyResult(applied, List.of());
        }

        @Override
        public List<TestObject> readBack(Scope scope, List<ObjectId> objectIds) {
            return objectIds.stream().map(objects::get).toList();
        }
    }

    private static final class AllClassTarget implements ProviderTargetConnector<TestObject> {
        private final Map<ObjectId, TestObject> objects = new LinkedHashMap<>();

        @Override
        public PreflightResult preflight(TransferBatch<TestObject> batch) {
            LossClass[] classes = LossClass.values();
            List<LossObservation> losses = new ArrayList<>();
            for (int index = 0; index < batch.objects().size(); index++) {
                TestObject object = batch.objects().get(index);
                losses.add(new LossObservation(
                        object.objectId(),
                        "fixture.field" + index,
                        classes[index],
                        "deterministic connector fixture for " + classes[index]));
            }
            return PreflightResult.accepted(losses);
        }

        @Override
        public ApplyResult apply(
                TransferBatch<TestObject> batch, IdempotencyKey idempotencyKey) {
            List<ObjectId> applied = new ArrayList<>();
            for (TestObject object : batch.objects()) {
                objects.put(object.objectId(), object);
                applied.add(object.objectId());
            }
            return new ApplyResult(applied, List.of());
        }

        @Override
        public List<TestObject> readBack(Scope scope, List<ObjectId> objectIds) {
            return objectIds.stream().map(objects::get).toList();
        }
    }

    private static SourcePage<TestObject> page(
            List<TestObject> objects, Checkpoint checkpoint, int limit) {
        int start = Math.toIntExact(checkpoint.sequence());
        if (start > objects.size()) {
            throw new IllegalStateException("checkpoint exceeds source size");
        }
        int end = Math.min(start + limit, objects.size());
        boolean complete = end == objects.size();
        Checkpoint next = new Checkpoint(
                end,
                complete ? null : Integer.toString(end),
                complete);
        return new SourcePage<>(objects.subList(start, end), next);
    }

    private static final class SimulatedCrash extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}

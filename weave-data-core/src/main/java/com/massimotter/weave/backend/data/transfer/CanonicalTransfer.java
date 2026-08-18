package com.massimotter.weave.backend.data.transfer;

import static com.massimotter.weave.backend.data.domain.CanonicalData.Checkpoint;
import static com.massimotter.weave.backend.data.domain.CanonicalData.CheckpointKey;
import static com.massimotter.weave.backend.data.domain.CanonicalData.Dependency;
import static com.massimotter.weave.backend.data.domain.CanonicalData.IdempotencyKey;
import static com.massimotter.weave.backend.data.domain.CanonicalData.Lifecycle;
import static com.massimotter.weave.backend.data.domain.CanonicalData.LossClass;
import static com.massimotter.weave.backend.data.domain.CanonicalData.LossObservation;
import static com.massimotter.weave.backend.data.domain.CanonicalData.ModelVersion;
import static com.massimotter.weave.backend.data.domain.CanonicalData.ObjectId;
import static com.massimotter.weave.backend.data.domain.CanonicalData.Provenance;
import static com.massimotter.weave.backend.data.domain.CanonicalData.Revision;
import static com.massimotter.weave.backend.data.domain.CanonicalData.Scope;
import static com.massimotter.weave.backend.data.domain.CanonicalData.TransferFormatVersion;
import static com.massimotter.weave.backend.data.domain.CanonicalData.TransferRunId;
import static com.massimotter.weave.backend.data.domain.CanonicalData.TransferStage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Executable provider-independent import/export contracts. */
public final class CanonicalTransfer {

    private CanonicalTransfer() {
    }

    /**
     * A typed Files, Calendar or Chat object that can cross the canonical transfer
     * boundary. Implementations remain in their owning domain modules.
     */
    public interface TransferObject {
        ObjectId objectId();

        ModelVersion modelVersion();

        Revision revision();

        Lifecycle lifecycle();

        Provenance provenance();

        /** Digest of canonical domain semantics, excluding provider-private metadata. */
        String canonicalDigest();

        List<Dependency> dependencies();
    }

    /** Source connector for an external provider. Provider DTOs remain behind this port. */
    public interface ProviderSourceConnector<T extends TransferObject> {
        SourcePage<T> readCanonicalPage(Scope scope, Checkpoint checkpoint, int limit);
    }

    /** Target connector for an external provider. Provider DTOs remain behind this port. */
    public interface ProviderTargetConnector<T extends TransferObject> {
        PreflightResult preflight(TransferBatch<T> batch);

        ApplyResult apply(TransferBatch<T> batch, IdempotencyKey idempotencyKey);

        List<T> readBack(Scope scope, List<ObjectId> objectIds);
    }

    /** Canonical typed object store; JPA implements this port later under #1320. */
    public interface CanonicalStore<T extends TransferObject> {
        void importBatch(Scope scope, List<T> objects, IdempotencyKey idempotencyKey);

        SourcePage<T> readCanonicalPage(Scope scope, Checkpoint checkpoint, int limit);
    }

    /** Durable checkpoint port; PostgreSQL implements this under #1320. */
    public interface CheckpointRepository {
        Optional<Checkpoint> find(CheckpointKey key);

        void save(CheckpointKey key, Checkpoint checkpoint);
    }

    public record SourcePage<T extends TransferObject>(List<T> objects, Checkpoint nextCheckpoint) {
        public SourcePage {
            objects = List.copyOf(Objects.requireNonNull(objects, "objects must not be null"));
            nextCheckpoint = Objects.requireNonNull(nextCheckpoint, "nextCheckpoint must not be null");
            if (objects.isEmpty() && !nextCheckpoint.complete()) {
                throw new IllegalArgumentException("an incomplete source page must contain progress data");
            }
        }
    }

    public record TransferHeader(
            TransferRunId runId,
            Scope scope,
            ModelVersion canonicalModelVersion,
            TransferFormatVersion transferFormatVersion,
            long sequence,
            Instant createdAt) {
        public TransferHeader {
            runId = Objects.requireNonNull(runId, "runId must not be null");
            scope = Objects.requireNonNull(scope, "scope must not be null");
            canonicalModelVersion = Objects.requireNonNull(
                    canonicalModelVersion, "canonicalModelVersion must not be null");
            transferFormatVersion = Objects.requireNonNull(
                    transferFormatVersion, "transferFormatVersion must not be null");
            createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
            if (sequence < 1) {
                throw new IllegalArgumentException("batch sequence must be positive");
            }
        }
    }

    public record TransferBatch<T extends TransferObject>(
            TransferHeader header,
            List<T> objects,
            List<Dependency> dependencies,
            String aggregateDigest) {
        public TransferBatch {
            header = Objects.requireNonNull(header, "header must not be null");
            objects = List.copyOf(Objects.requireNonNull(objects, "objects must not be null"));
            dependencies = List.copyOf(Objects.requireNonNull(dependencies, "dependencies must not be null"));
            aggregateDigest = requireText(aggregateDigest, "aggregateDigest");
            if (objects.isEmpty()) {
                throw new IllegalArgumentException("transfer batch must not be empty");
            }
            Set<ObjectId> ids = new HashSet<>();
            for (T object : objects) {
                Objects.requireNonNull(object, "transfer object must not be null");
                if (!ids.add(object.objectId())) {
                    throw new IllegalArgumentException("duplicate canonical object id in batch: " + object.objectId());
                }
            }
        }
    }

    public record PreflightResult(boolean accepted, List<LossObservation> losses) {
        public PreflightResult {
            losses = List.copyOf(Objects.requireNonNull(losses, "losses must not be null"));
        }

        public static PreflightResult accepted(List<LossObservation> losses) {
            return new PreflightResult(true, losses);
        }

        public static PreflightResult rejected(List<LossObservation> losses) {
            return new PreflightResult(false, losses);
        }
    }

    public record ApplyResult(List<ObjectId> appliedObjectIds, List<LossObservation> losses) {
        public ApplyResult {
            appliedObjectIds = List.copyOf(Objects.requireNonNull(
                    appliedObjectIds, "appliedObjectIds must not be null"));
            losses = List.copyOf(Objects.requireNonNull(losses, "losses must not be null"));
            if (new HashSet<>(appliedObjectIds).size() != appliedObjectIds.size()) {
                throw new IllegalArgumentException("applied object ids must be unique");
            }
        }
    }

    public record ImportResult(int importedObjects, Checkpoint checkpoint, String aggregateDigest) {
        public ImportResult {
            checkpoint = Objects.requireNonNull(checkpoint, "checkpoint must not be null");
            aggregateDigest = requireText(aggregateDigest, "aggregateDigest");
            if (importedObjects < 0) {
                throw new IllegalArgumentException("importedObjects must not be negative");
            }
        }
    }

    public record ExportResult(
            int appliedObjects,
            List<LossObservation> losses,
            Checkpoint checkpoint,
            String aggregateDigest) {
        public ExportResult {
            losses = List.copyOf(Objects.requireNonNull(losses, "losses must not be null"));
            checkpoint = Objects.requireNonNull(checkpoint, "checkpoint must not be null");
            aggregateDigest = requireText(aggregateDigest, "aggregateDigest");
            if (appliedObjects < 0) {
                throw new IllegalArgumentException("appliedObjects must not be negative");
            }
        }
    }

    public static final class TransferRejectedException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public TransferRejectedException(String message) {
            super(message);
        }
    }

    public static final class UnaccountedDataLossException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public UnaccountedDataLossException(String message) {
            super(message);
        }
    }

    public static final class CanonicalTransferService {
        private final CheckpointRepository checkpoints;
        private final Clock clock;

        public CanonicalTransferService(CheckpointRepository checkpoints, Clock clock) {
            this.checkpoints = Objects.requireNonNull(checkpoints, "checkpoints must not be null");
            this.clock = Objects.requireNonNull(clock, "clock must not be null");
        }

        public <T extends TransferObject> ImportResult importFromProvider(
                TransferRunId runId,
                Scope scope,
                ProviderSourceConnector<T> source,
                CanonicalStore<T> store,
                int pageSize) {
            requirePositivePageSize(pageSize);
            Objects.requireNonNull(runId, "runId must not be null");
            Objects.requireNonNull(scope, "scope must not be null");
            Objects.requireNonNull(source, "source must not be null");
            Objects.requireNonNull(store, "store must not be null");

            CheckpointKey key = new CheckpointKey(runId, TransferStage.IMPORT);
            Checkpoint checkpoint = checkpoints.find(key).orElseGet(Checkpoint::initial);
            int imported = 0;
            List<String> pageDigests = new ArrayList<>();

            while (!checkpoint.complete()) {
                SourcePage<T> page = source.readCanonicalPage(scope, checkpoint, pageSize);
                validateProgress(checkpoint, page.nextCheckpoint());
                if (!page.objects().isEmpty()) {
                    String digest = digestObjects(page.objects());
                    IdempotencyKey idempotencyKey = idempotencyKey(
                            runId, TransferStage.IMPORT, page.nextCheckpoint(), digest);
                    store.importBatch(scope, page.objects(), idempotencyKey);
                    imported += page.objects().size();
                    pageDigests.add(digest);
                }
                checkpoints.save(key, page.nextCheckpoint());
                checkpoint = page.nextCheckpoint();
            }

            return new ImportResult(imported, checkpoint, digestStrings(pageDigests));
        }

        public <T extends TransferObject> ExportResult exportToProvider(
                TransferRunId runId,
                Scope scope,
                ModelVersion canonicalModelVersion,
                TransferFormatVersion transferFormatVersion,
                CanonicalStore<T> store,
                ProviderTargetConnector<T> target,
                int pageSize) {
            requirePositivePageSize(pageSize);
            Objects.requireNonNull(runId, "runId must not be null");
            Objects.requireNonNull(scope, "scope must not be null");
            Objects.requireNonNull(canonicalModelVersion, "canonicalModelVersion must not be null");
            Objects.requireNonNull(transferFormatVersion, "transferFormatVersion must not be null");
            Objects.requireNonNull(store, "store must not be null");
            Objects.requireNonNull(target, "target must not be null");

            CheckpointKey key = new CheckpointKey(runId, TransferStage.EXPORT);
            Checkpoint checkpoint = checkpoints.find(key).orElseGet(Checkpoint::initial);
            int applied = 0;
            List<LossObservation> losses = new ArrayList<>();
            List<String> pageDigests = new ArrayList<>();

            while (!checkpoint.complete()) {
                SourcePage<T> page = store.readCanonicalPage(scope, checkpoint, pageSize);
                validateProgress(checkpoint, page.nextCheckpoint());
                if (page.objects().isEmpty()) {
                    checkpoints.save(key, page.nextCheckpoint());
                    checkpoint = page.nextCheckpoint();
                    continue;
                }

                String digest = digestObjects(page.objects());
                TransferHeader header = new TransferHeader(
                        runId,
                        scope,
                        canonicalModelVersion,
                        transferFormatVersion,
                        page.nextCheckpoint().sequence(),
                        clock.instant());
                TransferBatch<T> batch = new TransferBatch<>(
                        header,
                        page.objects(),
                        collectDependencies(page.objects()),
                        digest);

                PreflightResult preflight = target.preflight(batch);
                if (!preflight.accepted()) {
                    throw new TransferRejectedException(
                            "provider target rejected canonical batch " + header.sequence());
                }

                IdempotencyKey idempotencyKey = idempotencyKey(
                        runId, TransferStage.EXPORT, page.nextCheckpoint(), digest);
                ApplyResult result = target.apply(batch, idempotencyKey);
                List<LossObservation> batchLosses = new ArrayList<>(preflight.losses());
                batchLosses.addAll(result.losses());
                verifyAccounting(batch, result.appliedObjectIds(), batchLosses);
                List<T> readBack = target.readBack(scope, result.appliedObjectIds());
                verifyReadBack(batch, result.appliedObjectIds(), readBack, batchLosses);

                // Advance only after target readback. A crash after apply retries the same
                // deterministic key and therefore cannot create a duplicate target object.
                checkpoints.save(key, page.nextCheckpoint());
                checkpoint = page.nextCheckpoint();
                applied += result.appliedObjectIds().size();
                losses.addAll(batchLosses);
                pageDigests.add(digest);
            }

            return new ExportResult(applied, losses, checkpoint, digestStrings(pageDigests));
        }

        private static <T extends TransferObject> void verifyAccounting(
                TransferBatch<T> batch,
                List<ObjectId> appliedIds,
                List<LossObservation> losses) {
            Set<ObjectId> batchIds = new HashSet<>();
            for (T object : batch.objects()) {
                batchIds.add(object.objectId());
            }
            for (ObjectId appliedId : appliedIds) {
                if (!batchIds.contains(appliedId)) {
                    throw new UnaccountedDataLossException(
                            "target acknowledged unknown canonical object " + appliedId);
                }
            }
            Set<ObjectId> applied = new HashSet<>(appliedIds);
            for (ObjectId objectId : batchIds) {
                if (!applied.contains(objectId) && !hasNonPortableLoss(objectId, losses)) {
                    throw new UnaccountedDataLossException(
                            "canonical object was neither applied nor explicitly classified: " + objectId);
                }
            }
        }

        private static <T extends TransferObject> void verifyReadBack(
                TransferBatch<T> batch,
                List<ObjectId> appliedIds,
                List<T> readBack,
                List<LossObservation> losses) {
            Map<ObjectId, T> expected = new HashMap<>();
            for (T object : batch.objects()) {
                expected.put(object.objectId(), object);
            }
            Map<ObjectId, T> actual = new HashMap<>();
            for (T object : readBack) {
                if (actual.put(object.objectId(), object) != null) {
                    throw new UnaccountedDataLossException(
                            "target readback contains duplicate object " + object.objectId());
                }
            }
            for (ObjectId objectId : appliedIds) {
                T expectedObject = expected.get(objectId);
                T actualObject = actual.get(objectId);
                if (actualObject == null) {
                    throw new UnaccountedDataLossException(
                            "target readback omitted applied object " + objectId);
                }
                boolean equivalent = expectedObject.revision().equals(actualObject.revision())
                        && expectedObject.lifecycle() == actualObject.lifecycle()
                        && expectedObject.canonicalDigest().equals(actualObject.canonicalDigest());
                if (!equivalent && !hasNonPortableLoss(objectId, losses)) {
                    throw new UnaccountedDataLossException(
                            "target readback drift is not explicitly classified for " + objectId);
                }
            }
        }

        private static boolean hasNonPortableLoss(ObjectId objectId, List<LossObservation> losses) {
            return losses.stream().anyMatch(loss -> loss.objectId().equals(objectId)
                    && loss.classification() != LossClass.PORTABLE);
        }

        private static <T extends TransferObject> List<Dependency> collectDependencies(List<T> objects) {
            List<Dependency> dependencies = new ArrayList<>();
            for (T object : objects) {
                dependencies.addAll(List.copyOf(Objects.requireNonNull(
                        object.dependencies(), "dependencies must not be null")));
            }
            return List.copyOf(dependencies);
        }

        private static void validateProgress(Checkpoint current, Checkpoint next) {
            if (next.sequence() < current.sequence()) {
                throw new IllegalStateException("source checkpoint moved backwards");
            }
            if (!next.complete() && next.sequence() <= current.sequence()) {
                throw new IllegalStateException("source checkpoint did not advance");
            }
            if (current.complete() && !next.equals(current)) {
                throw new IllegalStateException("a completed checkpoint must not advance");
            }
        }

        private static void requirePositivePageSize(int pageSize) {
            if (pageSize < 1) {
                throw new IllegalArgumentException("pageSize must be positive");
            }
        }
    }

    public static String digestObjects(List<? extends TransferObject> objects) {
        List<String> components = objects.stream()
                .map(object -> object.objectId().domain().name()
                        + ":" + object.objectId().value()
                        + ":" + object.revision().value()
                        + ":" + object.lifecycle().name()
                        + ":" + requireText(object.canonicalDigest(), "canonicalDigest"))
                .sorted(Comparator.naturalOrder())
                .toList();
        return digestStrings(components);
    }

    public static String digestStrings(List<String> values) {
        MessageDigest digest = sha256();
        for (String value : values) {
            byte[] bytes = requireText(value, "digest component").getBytes(StandardCharsets.UTF_8);
            digest.update((byte) 0);
            digest.update(bytes);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static IdempotencyKey idempotencyKey(
            TransferRunId runId,
            TransferStage stage,
            Checkpoint checkpoint,
            String batchDigest) {
        return new IdempotencyKey(digestStrings(List.of(
                runId.value(),
                stage.name(),
                Long.toString(checkpoint.sequence()),
                batchDigest)));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not provide SHA-256", exception);
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}

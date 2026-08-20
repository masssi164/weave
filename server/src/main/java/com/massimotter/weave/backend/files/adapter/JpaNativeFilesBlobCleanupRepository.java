package com.massimotter.weave.backend.files.adapter;

import static java.util.Objects.requireNonNull;

import com.massimotter.weave.backend.files.application.FilesBlobCleanupDispositionRepository;
import com.massimotter.weave.backend.files.application.FilesMutationTargetCodec;
import com.massimotter.weave.backend.files.application.NativeFilesBlobCleanupException;
import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobReference;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobScope;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.Sealed;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.Fence;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.Target;
import com.massimotter.weave.backend.operation.adapter.JpaOperationIntentRepository;
import com.massimotter.weave.backend.operation.domain.OperationIntent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Repository;

/** JPA adapter for exact, append-only native Files blob-cleanup dispositions. */
@Repository
public class JpaNativeFilesBlobCleanupRepository
        implements FilesBlobCleanupDispositionRepository {

    private static final List<String> NONTERMINAL_INTENT_STATES = Arrays.stream(
                    OperationIntent.State.values())
            .filter(state -> !state.terminal())
            .map(Enum::name)
            .toList();

    private final FilesStreamHeadJpaRepository heads;
    private final FilesMutationPlanJpaRepository plans;
    private final FilesMutationTargetJpaRepository targets;
    private final FilesMutationFenceJpaRepository fences;
    private final FilesChangeJpaRepository changes;
    private final FilesBlobCleanupDispositionJpaRepository cleanup;
    private final JpaOperationIntentRepository operations;
    private final FilesMutationTargetCodec codec;

    public JpaNativeFilesBlobCleanupRepository(
            FilesStreamHeadJpaRepository heads,
            FilesMutationPlanJpaRepository plans,
            FilesMutationTargetJpaRepository targets,
            FilesMutationFenceJpaRepository fences,
            FilesChangeJpaRepository changes,
            FilesBlobCleanupDispositionJpaRepository cleanup,
            JpaOperationIntentRepository operations,
            FilesMutationTargetCodec codec) {
        this.heads = requireNonNull(heads, "heads");
        this.plans = requireNonNull(plans, "plans");
        this.targets = requireNonNull(targets, "targets");
        this.fences = requireNonNull(fences, "fences");
        this.changes = requireNonNull(changes, "changes");
        this.cleanup = requireNonNull(cleanup, "cleanup");
        this.operations = requireNonNull(operations, "operations");
        this.codec = requireNonNull(codec, "codec");
    }

    @Override
    public CleanupWork lockWork(String operationRef) {
        String requiredRef = required(operationRef);
        FilesMutationPlanJpaEntity storedPlan = plans.lockForCleanupByOperationRef(requiredRef)
                .orElseThrow(() -> corrupt("Files cleanup plan is missing"));
        List<FilesMutationTargetJpaEntity> targetEntities =
                targets.findByIdOperationRefOrderByIdTargetOrdinal(requiredRef);
        List<Target> storedTargets = targetEntities.stream()
                .map(FilesMutationTargetJpaEntity::toTarget)
                .toList();
        List<Fence> storedFences = fences.findByIdOperationRefOrderByIdFenceOrdinal(requiredRef)
                .stream()
                .map(FilesMutationFenceJpaEntity::toFence)
                .toList();
        Sealed plan;
        try {
            plan = storedPlan.toSealed(storedTargets, storedFences);
        } catch (RuntimeException malformed) {
            throw new NativeFilesBlobCleanupException(
                    "Files cleanup plan is incomplete or malformed");
        }
        if (!constantEquals(codec.targetsDigest(storedTargets), plan.targetsDigest())) {
            throw corrupt("Files cleanup plan target digest does not match");
        }
        if (!constantEquals(codec.fencesDigest(storedFences), plan.fencesDigest())) {
            throw corrupt("Files cleanup plan fence digest does not match");
        }

        OperationIntent intent = operations.findByOperationRef(requiredRef)
                .orElseThrow(() -> corrupt("Files cleanup intent is missing"));
        requireTerminalFailureLink(intent, plan, operations.outboxLinks(requiredRef));
        if (!changes.findByOperationRefOrderByIdRevision(requiredRef).isEmpty()) {
            throw corrupt("Files cleanup found canonical change evidence for a failed operation");
        }
        heads.lockById(new FilesScopeId(plan.organizationRef(), plan.spaceRef()))
                .orElseThrow(() -> corrupt("Files cleanup stream head is missing"));

        LinkedHashSet<BlobReference> distinct = new LinkedHashSet<>();
        for (FilesMutationTargetJpaEntity target : targetEntities) {
            if (target.objectKind() != Kind.FILE) {
                continue;
            }
            add(distinct, target.sourceReadBlobBinding());
            add(distinct, target.resultBlobBinding());
        }
        List<BlobReference> ordered = new ArrayList<>(distinct);
        ordered.sort(java.util.Comparator.comparing(BlobReference::value));
        return new CleanupWork(
                requiredRef,
                new BlobScope(plan.organizationRef(), plan.spaceRef()),
                ordered);
    }

    @Override
    public List<RecordedDisposition> recorded(String operationRef) {
        return cleanup.findByIdOperationRefOrderByIdBindingDigest(required(operationRef)).stream()
                .map(entity -> new RecordedDisposition(
                        entity.operationRef(),
                        entity.dispositionVersion(),
                        entity.bindingDigest(),
                        new BlobReference(entity.privateBlobBinding()),
                        Disposition.valueOf(entity.disposition()),
                        entity.recordedAt()))
                .toList();
    }

    @Override
    public ReferenceStatus recheck(CleanupWork work, BlobReference binding) {
        CleanupWork requiredWork = requireNonNull(work, "work");
        BlobReference requiredBinding = requireNonNull(binding, "binding");
        long canonicalReferences = cleanup.countCanonicalReferences(
                requiredWork.scope().organizationRef(),
                requiredWork.scope().spaceRef(),
                requiredBinding.value());
        if (canonicalReferences > 0) {
            return ReferenceStatus.STILL_REFERENCED;
        }
        long otherPlanReferences = cleanup.countOtherNonterminalPlanReferences(
                requiredWork.scope().organizationRef(),
                requiredWork.scope().spaceRef(),
                requiredWork.operationRef(),
                requiredBinding.value(),
                NONTERMINAL_INTENT_STATES);
        return otherPlanReferences > 0
                ? ReferenceStatus.STILL_PROTECTED
                : ReferenceStatus.DELETE_ALLOWED;
    }

    @Override
    public void record(
            CleanupWork work,
            BlobReference binding,
            String bindingDigest,
            Disposition disposition,
            Instant recordedAt) {
        CleanupWork requiredWork = requireNonNull(work, "work");
        BlobReference requiredBinding = requireNonNull(binding, "binding");
        Disposition requiredDisposition = requireNonNull(disposition, "disposition");
        Instant requiredAt = requireNonNull(recordedAt, "recordedAt");
        FilesBlobCleanupDispositionId id = new FilesBlobCleanupDispositionId(
                requiredWork.operationRef(),
                bindingDigest);
        FilesBlobCleanupDispositionJpaEntity byDigest = cleanup.findById(id).orElse(null);
        FilesBlobCleanupDispositionJpaEntity byBinding = cleanup
                .findByOperationRefAndPrivateBlobBinding(
                        requiredWork.operationRef(),
                        requiredBinding.value())
                .orElse(null);
        if (byDigest != null || byBinding != null) {
            FilesBlobCleanupDispositionJpaEntity existing = byDigest == null ? byBinding : byDigest;
            if (byDigest != null
                    && byBinding != null
                    && byDigest != byBinding
                    && !byDigest.bindingDigest().equals(byBinding.bindingDigest())) {
                throw corrupt("Files cleanup disposition keys collide");
            }
            if (!existing.bindingDigest().equals(bindingDigest)
                    || !existing.privateBlobBinding().equals(requiredBinding.value())
                    || !existing.dispositionVersion().equals(VERSION)
                    || !existing.disposition().equals(requiredDisposition.name())) {
                throw corrupt("Files cleanup disposition retry is contradictory");
            }
            return;
        }
        cleanup.saveAndFlush(FilesBlobCleanupDispositionJpaEntity.create(
                requiredWork.operationRef(),
                bindingDigest,
                requiredBinding.value(),
                requiredDisposition.name(),
                requiredAt));
    }

    private void requireTerminalFailureLink(
            OperationIntent intent,
            Sealed plan,
            List<JpaOperationIntentRepository.OutboxLink> outbox) {
        boolean terminalFailure = intent.state() == OperationIntent.State.DENIED
                || intent.state() == OperationIntent.State.FAILED;
        boolean planLink = "files".equals(intent.domain())
                && intent.organizationRef().equals(plan.organizationRef())
                && intent.canonicalArgumentsDigest().equals(plan.canonicalArgumentsDigest())
                && intent.providerBindingRevision() == plan.providerBindingRevision();
        String expectedEvent = intent.state() == OperationIntent.State.DENIED
                ? "operation.denied"
                : "operation.failed";
        boolean outboxLink = outbox.size() == 1
                && outbox.getFirst().outboxRef().equals(intent.outboxRef())
                && outbox.getFirst().eventType().equals(expectedEvent);
        if (!terminalFailure || !planLink || !outboxLink) {
            throw corrupt("Files cleanup intent, plan, and reserved outbox evidence do not match");
        }
    }

    private void add(LinkedHashSet<BlobReference> bindings, String value) {
        if (value != null) {
            bindings.add(new BlobReference(value));
        }
    }

    private boolean constantEquals(String first, String second) {
        return MessageDigest.isEqual(
                first.getBytes(StandardCharsets.US_ASCII),
                second.getBytes(StandardCharsets.US_ASCII));
    }

    private String required(String operationRef) {
        if (operationRef == null || operationRef.isBlank()) {
            throw new IllegalArgumentException("operationRef must not be blank");
        }
        return operationRef.trim();
    }

    private NativeFilesBlobCleanupException corrupt(String message) {
        return new NativeFilesBlobCleanupException(message);
    }
}

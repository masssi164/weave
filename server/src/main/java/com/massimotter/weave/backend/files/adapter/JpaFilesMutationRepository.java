package com.massimotter.weave.backend.files.adapter;

import static com.massimotter.weave.backend.files.domain.FilesAuthority.Lifecycle.ACTIVE;
import static com.massimotter.weave.backend.files.domain.FilesAuthority.Lifecycle.TOMBSTONED;
import static java.util.Objects.requireNonNull;

import com.massimotter.weave.backend.files.application.FilesDigests;
import com.massimotter.weave.backend.files.application.FilesEtags;
import com.massimotter.weave.backend.files.application.FilesMutationRecords;
import com.massimotter.weave.backend.files.application.FilesMutationIntentService;
import com.massimotter.weave.backend.files.application.FilesMutationTargetCodec;
import com.massimotter.weave.backend.files.application.FilesScope;
import com.massimotter.weave.backend.files.application.NativeFilesMutationRepository;
import com.massimotter.weave.backend.files.application.NativeFilesScopeProvisioner;
import com.massimotter.weave.backend.files.application.NativeFilesFinalizationAuthorization;
import com.massimotter.weave.backend.files.application.NativeFilesMutationRepository.CommitOutcome;
import com.massimotter.weave.backend.files.application.NativeFilesMutationRepository.CommitProbe;
import com.massimotter.weave.backend.files.application.NativeFilesMutationRepository.RecoveryPage;
import com.massimotter.weave.backend.files.domain.FilesAuthority.CanonicalFileRecord;
import com.massimotter.weave.backend.files.domain.FilesChangeStream.ChangeKind;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileId;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileObject;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileVersion;
import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobReference;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.Sealed;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.OperationKind;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.ExpectedPresence;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.Fence;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.FenceRole;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.Membership;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.Target;
import com.massimotter.weave.backend.files.port.StoredFileRecord;
import com.massimotter.weave.backend.files.port.StoredFileRecord.BlobBinding;
import com.massimotter.weave.backend.operation.adapter.JpaOperationIntentRepository;
import com.massimotter.weave.backend.operation.application.OperationIntentService;
import com.massimotter.weave.backend.operation.domain.OperationIntent;
import com.massimotter.weave.backend.operation.domain.OperationIntent.ProtocolProjection;
import jakarta.persistence.PersistenceException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** JPA implementation of the two native Files relational mutation boundaries. */
@Repository
public class JpaFilesMutationRepository
        implements NativeFilesMutationRepository, NativeFilesScopeProvisioner {

    private static final int BEGIN_ATTEMPTS = 3;
    private static final List<String> NONTERMINAL_INTENT_STATES = Arrays.stream(
                    OperationIntent.State.values())
            .filter(state -> !state.terminal())
            .map(Enum::name)
            .toList();
    private static final List<String> TERMINAL_FAILURE_INTENT_STATES = List.of(
            OperationIntent.State.DENIED.name(),
            OperationIntent.State.FAILED.name());

    private final FilesStreamHeadJpaRepository heads;
    private final FilesMutationPlanJpaRepository plans;
    private final FilesMutationTargetJpaRepository targets;
    private final FilesMutationFenceJpaRepository fences;
    private final FilesChangeJpaRepository changes;
    private final FileObjectJpaRepository files;
    private final FileLockJpaRepository locks;
    private final JpaFilesAuthorityRepository authority;
    private final JpaOperationIntentRepository operations;
    private final OperationIntentService intentService;
    private final FilesMutationTargetCodec codec;
    private final NativeFilesFinalizationAuthorization authorization;
    private final TransactionTemplate transactions;
    private final Clock clock;

    @SuppressWarnings("checkstyle:ParameterNumber")
    @Autowired
    public JpaFilesMutationRepository(
            FilesStreamHeadJpaRepository heads,
            FilesMutationPlanJpaRepository plans,
            FilesMutationTargetJpaRepository targets,
            FilesMutationFenceJpaRepository fences,
            FilesChangeJpaRepository changes,
            FileObjectJpaRepository files,
            FileLockJpaRepository locks,
            JpaFilesAuthorityRepository authority,
            JpaOperationIntentRepository operations,
            OperationIntentService intentService,
            FilesMutationTargetCodec codec,
            NativeFilesFinalizationAuthorization authorization,
            PlatformTransactionManager transactionManager) {
        this(
                heads,
                plans,
                targets,
                fences,
                changes,
                files,
                locks,
                authority,
                operations,
                intentService,
                codec,
                authorization,
                transactionManager,
                Clock.systemUTC());
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    JpaFilesMutationRepository(
            FilesStreamHeadJpaRepository heads,
            FilesMutationPlanJpaRepository plans,
            FilesMutationTargetJpaRepository targets,
            FilesMutationFenceJpaRepository fences,
            FilesChangeJpaRepository changes,
            FileObjectJpaRepository files,
            FileLockJpaRepository locks,
            JpaFilesAuthorityRepository authority,
            JpaOperationIntentRepository operations,
            OperationIntentService intentService,
            FilesMutationTargetCodec codec,
            NativeFilesFinalizationAuthorization authorization,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.heads = requireNonNull(heads, "heads");
        this.plans = requireNonNull(plans, "plans");
        this.targets = requireNonNull(targets, "targets");
        this.fences = requireNonNull(fences, "fences");
        this.changes = requireNonNull(changes, "changes");
        this.files = requireNonNull(files, "files");
        this.locks = requireNonNull(locks, "locks");
        this.authority = requireNonNull(authority, "authority");
        this.operations = requireNonNull(operations, "operations");
        this.intentService = requireNonNull(intentService, "intentService");
        this.codec = requireNonNull(codec, "codec");
        this.authorization = requireNonNull(authorization, "authorization");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public void provisionScope(FilesScope scope, Instant provisionedAt) {
        FilesScope requiredScope = requireNonNull(scope, "scope");
        Instant requiredTime = requireNonNull(provisionedAt, "provisionedAt");
        FilesScopeId id = new FilesScopeId(
                requiredScope.organizationRef(), requiredScope.spaceRef());
        try {
            transactions.executeWithoutResult(status -> {
                if (heads.existsById(id)) {
                    return;
                }
                requirePristineProvisioningScope(requiredScope);
                heads.saveAndFlush(FilesStreamHeadJpaEntity.provision(
                        requiredScope.organizationRef(),
                        requiredScope.spaceRef(),
                        requiredTime));
            });
        } catch (DataIntegrityViolationException | PersistenceException concurrentProvision) {
            Boolean present = transactions.execute(status -> heads.existsById(id));
            if (!Boolean.TRUE.equals(present)) {
                throw concurrentProvision;
            }
        }
    }

    @Override
    public BeginResult begin(
            OperationIntent candidate,
            FilesScope scope,
            Supplier<Sealed> planFactory) {
        OperationIntent requested = requireNonNull(candidate, "candidate");
        FilesScope requiredScope = requireNonNull(scope, "scope");
        Supplier<Sealed> requestedPlanFactory = requireNonNull(planFactory, "planFactory");
        RuntimeException lastFailure = null;
        for (int attempt = 0; attempt < BEGIN_ATTEMPTS; attempt++) {
            try {
                return transactions.execute(status -> {
                    lockProvisionedScope(requiredScope);
                    Sealed requestedPlan = requireNonNull(requestedPlanFactory.get(), "plan");
                    requireIntentPlanLink(requested, requestedPlan);
                    if (!requestedPlan.organizationRef().equals(requiredScope.organizationRef())
                            || !requestedPlan.spaceRef().equals(requiredScope.spaceRef())) {
                        throw new IllegalArgumentException(
                                "the native Files plan does not match its provisioned scope");
                    }
                    return createBoundary(requested, requestedPlan);
                });
            } catch (DataIntegrityViolationException | PersistenceException failure) {
                lastFailure = failure;
                BeginResult existing = transactions.execute(status -> existing(requested));
                if (existing != null) {
                    return existing;
                }
            }
        }
        throw requireNonNull(lastFailure, "begin failure");
    }

    @Override
    public Sealed requireSealed(String operationRef) {
        return transactions.execute(status -> {
            Sealed plan = verifiedStoredPlan(operationRef);
            OperationIntent intent = operations.findByOperationRef(operationRef)
                    .orElseThrow(() -> new CorruptFilesMutationException(
                            "the Files intent is missing"));
            requireIntentPlanLink(intent, plan);
            return plan;
        });
    }

    @Override
    public FinalizationResult finalizeSuccess(
            OperationIntent expected,
            Sealed suppliedPlan,
            String resultDigest,
            String auditRef,
            LockMove lockMove) {
        return transactions.execute(status -> {
            Sealed plan = verifiedStoredPlan(suppliedPlan.operationRef());
            if (!plan.equals(suppliedPlan)) {
                throw new CorruptFilesMutationException("the supplied Files plan differs from its sealed record");
            }
            OperationIntent current = operations.findByOperationRef(plan.operationRef())
                    .orElseThrow(() -> new CorruptFilesMutationException("the Files intent is missing"));
            if (current.state() == OperationIntent.State.SUCCEEDED) {
                List<FilesChangeJpaEntity> committed = changes.findByOperationRefOrderByIdRevision(plan.operationRef());
                if (!completeCommittedEffect(plan, committed, operations.outboxLinks(plan.operationRef()))) {
                    throw new CorruptFilesMutationException(
                            "the committed Files finalization evidence is incomplete or inconsistent");
                }
                return new FinalizationResult(
                        current,
                        committed.getFirst().rangeStart(),
                        committed.getLast().rangeEnd());
            }
            if (!current.updatedAt().equals(requireNonNull(expected, "expected").updatedAt())) {
                throw new ConcurrentFilesMutationException(plan.operationRef());
            }
            requireIntentPlanLink(current, plan);

            FilesScopeId scopeId = new FilesScopeId(plan.organizationRef(), plan.spaceRef());
            FilesStreamHeadJpaEntity head = heads.lockById(scopeId)
                    .orElseThrow(() -> new CorruptFilesMutationException("the Files stream head is missing"));
            if (!authorization.allowed(current, plan.spaceRef())) {
                throw new AuthorizationDeniedException(plan.operationRef());
            }
            validateApplicableLocks(current, plan);
            List<StoredFileRecord> currentSnapshot = files
                    .findByIdOrganizationRefAndIdSpaceRefAndLifecycleOrderByCanonicalPath(
                            plan.organizationRef(), plan.spaceRef(), ACTIVE)
                    .stream()
                    .map(FileObjectJpaEntity::toStoredRecord)
                    .toList();
            validateFences(plan, currentSnapshot);
            validateCurrentState(plan);

            List<StoredFileRecord> tombstones = new ArrayList<>();
            List<StoredFileRecord> activations = new ArrayList<>();
            for (Target target : plan.targets()) {
                StoredFileRecord result = FilesMutationRecords.resultRecord(plan, target);
                if (target.resultLifecycleState() == TOMBSTONED) {
                    tombstones.add(result);
                } else {
                    activations.add(result);
                }
            }
            FilePath operationRoot = operationRoot(plan);
            authority.replaceTree(operationRoot, tombstones, activations);
            applyLockMove(current, plan);

            Instant committedAt = now();
            FilesStreamHeadJpaEntity.RevisionRange range = head.reserve(plan.targetCount(), committedAt);
            List<FilesChangeJpaEntity> entries = new ArrayList<>(plan.targetCount());
            long revision = range.start();
            for (Target target : plan.targets()) {
                entries.add(FilesChangeJpaEntity.create(plan, target, revision++, range, committedAt));
            }
            changes.saveAll(entries);

            OperationIntentService.PreparedTransition transition = intentService.prepareNativeSuccess(
                    current,
                    resultDigest,
                    auditRef);
            OperationIntent succeeded = operations.transitionWithinTransaction(
                    current,
                    transition.intent(),
                    transition.outboxEvent());
            changes.flush();
            heads.flush();
            return new FinalizationResult(succeeded, range.start(), range.end());
        });
    }

    @Override
    public OperationIntent recordFailure(
            OperationIntent expected,
            boolean denied,
            String resultDigest,
            String auditRef) {
        return transactions.execute(status -> {
            OperationIntent current = operations.findByOperationRef(expected.operationRef())
                    .orElseThrow(() -> new CorruptFilesMutationException("the Files intent is missing"));
            if (current.state().terminal()) {
                return current;
            }
            if (!current.updatedAt().equals(expected.updatedAt())) {
                throw new ConcurrentFilesMutationException(expected.operationRef());
            }
            verifiedStoredPlan(expected.operationRef());
            OperationIntentService.PreparedTransition transition = intentService.prepareNativeFailure(
                    current,
                    denied,
                    resultDigest,
                    auditRef);
            return operations.transitionWithinTransaction(
                    current,
                    transition.intent(),
                    transition.outboxEvent());
        });
    }

    @Override
    public OperationIntent markAmbiguous(
            OperationIntent expected,
            String correlationDigest) {
        return transactions.execute(status -> {
            OperationIntent current = operations.findByOperationRef(expected.operationRef())
                    .orElseThrow(() -> new CorruptFilesMutationException(
                            "the Files intent is missing"));
            if (current.state() == OperationIntent.State.AMBIGUOUS
                    || current.state() == OperationIntent.State.RECONCILING
                    || current.state().terminal()) {
                return current;
            }
            if (!current.updatedAt().equals(expected.updatedAt())) {
                throw new ConcurrentFilesMutationException(expected.operationRef());
            }
            verifiedStoredPlan(expected.operationRef());
            OperationIntent ambiguous = intentService.prepareNativeAmbiguous(
                    current,
                    correlationDigest);
            return operations.transitionWithoutOutbox(current, ambiguous);
        });
    }

    @Override
    public OperationIntent beginReconciliation(OperationIntent expected) {
        return transactions.execute(status -> {
            OperationIntent current = operations.findByOperationRef(expected.operationRef())
                    .orElseThrow(() -> new CorruptFilesMutationException(
                            "the Files intent is missing"));
            if (current.state() == OperationIntent.State.RECONCILING
                    || current.state().terminal()) {
                return current;
            }
            if (!current.updatedAt().equals(expected.updatedAt())) {
                throw new ConcurrentFilesMutationException(expected.operationRef());
            }
            verifiedStoredPlan(expected.operationRef());
            OperationIntent reconciling = intentService.prepareNativeReconciliation(current);
            return operations.transitionWithoutOutbox(current, reconciling);
        });
    }

    @Override
    public CommitProbe probe(String operationRef) {
        return transactions.execute(status -> {
            Sealed plan = verifiedStoredPlan(operationRef);
            OperationIntent current = operations.findByOperationRef(operationRef)
                    .orElseThrow(() -> new CorruptFilesMutationException("the Files intent is missing"));
            List<FilesChangeJpaEntity> committed = changes.findByOperationRefOrderByIdRevision(operationRef);
            var outbox = operations.outboxLinks(operationRef);
            if (current.state() == OperationIntent.State.SUCCEEDED) {
                if (!completeCommittedEffect(plan, committed, outbox)) {
                    return new CommitProbe(CommitOutcome.CORRUPT, current, null, null);
                }
                return new CommitProbe(
                        CommitOutcome.SUCCEEDED,
                        current,
                        committed.getFirst().rangeStart(),
                        committed.getLast().rangeEnd());
            }
            if (current.state() == OperationIntent.State.DENIED
                    || current.state() == OperationIntent.State.FAILED) {
                boolean terminalOutbox = outbox.size() == 1
                        && outbox.getFirst().outboxRef().equals(current.outboxRef())
                        && outbox.getFirst().eventType().equals(
                                current.state() == OperationIntent.State.DENIED
                                        ? "operation.denied"
                                        : "operation.failed");
                return new CommitProbe(
                        committed.isEmpty() && terminalOutbox
                                ? CommitOutcome.TERMINAL_FAILURE
                                : CommitOutcome.CORRUPT,
                        current,
                        null,
                        null);
            }
            boolean cleanNonCommit = committed.isEmpty()
                    && outbox.isEmpty()
                    && (current.state() == OperationIntent.State.CREATED
                            || current.state() == OperationIntent.State.AMBIGUOUS
                            || current.state() == OperationIntent.State.RECONCILING);
            if (cleanNonCommit) {
                lockProvisionedScope(new FilesScope(
                        plan.organizationRef(),
                        plan.spaceRef()));
            }
            return new CommitProbe(
                    cleanNonCommit ? CommitOutcome.NOT_COMMITTED : CommitOutcome.CORRUPT,
                    current,
                    null,
                    null);
        });
    }

    @Override
    public IngressProtection ingressProtection(String operationRef) {
        String requiredOperationRef = requireText(operationRef, "operationRef");
        try {
            return transactions.execute(status -> {
                boolean planExists = plans.existsById(requiredOperationRef);
                Optional<OperationIntent> intent = operations.findByOperationRef(requiredOperationRef);
                if (!planExists && intent.isEmpty()) {
                    return IngressProtection.UNPROTECTED;
                }
                if (!planExists || intent.isEmpty()) {
                    return IngressProtection.UNAVAILABLE;
                }
                return intent.orElseThrow().state().terminal()
                        ? IngressProtection.UNPROTECTED
                        : IngressProtection.PROTECTED;
            });
        } catch (RuntimeException unavailable) {
            return IngressProtection.UNAVAILABLE;
        }
    }

    @Override
    public RecoveryPage recoverablePutMutations(String afterOperationRef, int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("recovery limit must be between 1 and 100");
        }
        return transactions.execute(status -> {
            List<String> operationRefs = plans.findRecoverableOperationRefs(
                        OperationKind.PUT.name(),
                        NONTERMINAL_INTENT_STATES,
                        afterOperationRef,
                        PageRequest.of(0, limit));
            List<RecoveryCandidate> candidates = new ArrayList<>();
            for (String operationRef : operationRefs) {
                try {
                    OperationIntent intent = operations.findByOperationRef(operationRef)
                            .orElseThrow(() -> new CorruptFilesMutationException(
                                    "the Files recovery intent is missing"));
                    Sealed plan = verifiedStoredPlan(operationRef);
                    if (intent.state().terminal() || plan.operationKind() != OperationKind.PUT) {
                        throw new CorruptFilesMutationException(
                                "the Files recovery candidate changed");
                    }
                    candidates.add(new RecoveryCandidate(intent, plan));
                } catch (NativeFilesMutationRepository.CorruptMutationStateException
                        | IllegalArgumentException
                        | IllegalStateException corruptCandidate) {
                    // Cursor progress comes from the raw page, not only successfully decoded rows.
                }
            }
            return new RecoveryPage(
                    candidates,
                    operationRefs.isEmpty() ? null : operationRefs.getLast(),
                    operationRefs.size());
        });
    }

    @Override
    public Set<BlobReference> protectedBindings(FilesScope scope) {
        FilesScope requiredScope = requireNonNull(scope, "scope");
        return transactions.execute(status -> {
            Set<BlobReference> protectedBindings = new LinkedHashSet<>();
            for (FilesMutationTargetJpaEntity target : targets.findProtectedTargets(
                    requiredScope.organizationRef(),
                    requiredScope.spaceRef(),
                    NONTERMINAL_INTENT_STATES)) {
                addBinding(protectedBindings, target.sourceReadBlobBinding());
                addBinding(protectedBindings, target.resultBlobBinding());
            }
            for (FilesMutationTargetJpaEntity target
                    : targets.findTerminalFailureTargetsWithIncompleteCleanup(
                            requiredScope.organizationRef(),
                            requiredScope.spaceRef(),
                            TERMINAL_FAILURE_INTENT_STATES)) {
                addBinding(protectedBindings, target.sourceReadBlobBinding());
                addBinding(protectedBindings, target.resultBlobBinding());
            }
            return Set.copyOf(protectedBindings);
        });
    }

    private boolean completeCommittedEffect(
            Sealed plan,
            List<FilesChangeJpaEntity> committed,
            List<JpaOperationIntentRepository.OutboxLink> outbox) {
        if (committed.size() != plan.targetCount()
                || outbox.size() != 1
                || !outbox.getFirst().outboxRef().equals(
                        operations.findByOperationRef(plan.operationRef()).orElseThrow().outboxRef())
                || !"operation.succeeded".equals(outbox.getFirst().eventType())) {
            return false;
        }
        long rangeStart = committed.getFirst().rangeStart();
        long rangeEnd = committed.getLast().rangeEnd();
        if (rangeEnd - rangeStart + 1 != plan.targetCount()
                || committed.stream().anyMatch(change ->
                        change.rangeStart() != rangeStart || change.rangeEnd() != rangeEnd)) {
            return false;
        }
        for (int index = 0; index < committed.size(); index++) {
            if (!matchesChange(
                    plan,
                    plan.targets().get(index),
                    committed.get(index).toFileChange(),
                    rangeStart + index,
                    rangeStart,
                    rangeEnd)) {
                return false;
            }
        }
        FilesStreamHeadJpaEntity head = heads.findById(
                        new FilesScopeId(plan.organizationRef(), plan.spaceRef()))
                .orElse(null);
        if (head == null || head.latestRevision() < rangeEnd) {
            return false;
        }
        return true;
    }

    private boolean matchesChange(
            Sealed plan,
            Target target,
            com.massimotter.weave.backend.files.domain.FilesChangeStream.FileChange change,
            long revision,
            long rangeStart,
            long rangeEnd) {
        return change.organizationRef().equals(plan.organizationRef())
                && change.spaceRef().equals(plan.spaceRef())
                && change.revision() == revision
                && change.operationRef().equals(plan.operationRef())
                && change.changeKind() == target.changeKind()
                && change.fileId().value().equals(target.targetFileRef())
                && Objects.equals(
                        change.sourceFileId() == null ? null : change.sourceFileId().value(),
                        target.sourceFileRef())
                && Objects.equals(
                        change.sourcePath() == null ? null : change.sourcePath().value(),
                        target.sourcePath())
                && Objects.equals(
                        change.targetPath() == null ? null : change.targetPath().value(),
                        target.targetPath())
                && change.objectKind() == target.objectKind()
                && change.lifecycle() == target.resultLifecycleState()
                && change.providerBindingRevision() == plan.providerBindingRevision()
                && change.resultingSize() == target.resultSize()
                && Objects.equals(change.resultingMediaType(), target.resultMediaType())
                && Objects.equals(change.resultingContentDigest(), target.resultContentDigest())
                && Objects.equals(
                        change.resultingFileVersion().known()
                                ? change.resultingFileVersion().value()
                                : null,
                        target.resultFileVersion())
                && Objects.equals(change.resultingEtag(), target.resultStrongEtag())
                && change.resultingModifiedAt().equals(target.resultModifiedAt())
                && change.resultingHidden() == target.resultHidden()
                && change.resultingObservedAt().equals(target.resultObservedAt())
                && change.rangeStart() == rangeStart
                && change.rangeEnd() == rangeEnd;
    }

    private void addBinding(Set<BlobReference> protectedBindings, String binding) {
        if (binding != null) {
            protectedBindings.add(new BlobReference(binding));
        }
    }

    private BeginResult createBoundary(OperationIntent intent, Sealed plan) {
        OperationIntent stored = operations.insertCreatedWithoutOutbox(intent);
        FilesMutationPlanJpaEntity storedPlan = plans.saveAndFlush(
                FilesMutationPlanJpaEntity.open(plan));
        targets.saveAllAndFlush(plan.targets().stream()
                .map(target -> FilesMutationTargetJpaEntity.create(plan.operationRef(), target))
                .toList());
        fences.saveAllAndFlush(plan.fences().stream()
                .map(fence -> FilesMutationFenceJpaEntity.create(plan.operationRef(), fence))
                .toList());
        storedPlan.seal(plan.sealedAt());
        plans.flush();
        return new BeginResult(stored, verifiedStoredPlan(plan.operationRef()), true);
    }

    private FilesStreamHeadJpaEntity lockProvisionedScope(FilesScope scope) {
        return heads.lockById(new FilesScopeId(scope.organizationRef(), scope.spaceRef()))
                .orElseThrow(() -> new CorruptFilesMutationException(
                        "the Files stream head is missing before plan creation"));
    }

    private void requirePristineProvisioningScope(FilesScope scope) {
        boolean hasHistory = operations.existsForOrganizationDomain(
                        scope.organizationRef(),
                        "files")
                || plans.existsByOrganizationRefAndSpaceRef(
                        scope.organizationRef(),
                        scope.spaceRef())
                || changes.existsByIdOrganizationRefAndIdSpaceRef(
                        scope.organizationRef(),
                        scope.spaceRef())
                || locks.existsByIdOrganizationRefAndIdSpaceRef(
                        scope.organizationRef(),
                        scope.spaceRef())
                || files.existsByIdOrganizationRefAndIdSpaceRef(
                        scope.organizationRef(),
                        scope.spaceRef());
        if (hasHistory) {
            throw new CorruptFilesMutationException(
                    "the Files stream head is missing after scope activity");
        }
    }

    private BeginResult existing(OperationIntent requested) {
        return operations.findByIdempotencyKey(requested.organizationRef(), requested.idempotencyKey())
                .map(intent -> new BeginResult(intent, verifiedStoredPlan(intent.operationRef()), false))
                .orElse(null);
    }

    private Sealed verifiedStoredPlan(String operationRef) {
        FilesMutationPlanJpaEntity plan = plans.lockByOperationRef(requireText(operationRef, "operationRef"))
                .orElseThrow(() -> new CorruptFilesMutationException("the Files mutation plan is missing"));
        List<Target> storedTargets = targets.findByIdOperationRefOrderByIdTargetOrdinal(operationRef)
                .stream()
                .map(FilesMutationTargetJpaEntity::toTarget)
                .toList();
        List<Fence> storedFences = fences.findByIdOperationRefOrderByIdFenceOrdinal(operationRef)
                .stream()
                .map(FilesMutationFenceJpaEntity::toFence)
                .toList();
        Sealed sealed = plan.toSealed(storedTargets, storedFences);
        String actualDigest = codec.targetsDigest(storedTargets);
        if (!constantEquals(actualDigest, sealed.targetsDigest())) {
            throw new CorruptFilesMutationException("the Files mutation target digest does not match");
        }
        String actualFenceDigest = codec.fencesDigest(storedFences);
        if (!constantEquals(actualFenceDigest, sealed.fencesDigest())) {
            throw new CorruptFilesMutationException("the Files mutation fence digest does not match");
        }
        return sealed;
    }

    private void validateCurrentState(Sealed plan) {
        for (Target target : plan.targets()) {
            if (target.sourceFileRef() != null) {
                StoredFileRecord current = files.findById(new CanonicalFileId(
                                plan.organizationRef(),
                                plan.spaceRef(),
                                target.sourceFileRef()))
                        .map(FileObjectJpaEntity::toStoredRecord)
                        .orElseThrow(() -> new ConcurrentFilesMutationException(plan.operationRef()));
                requireSourceSnapshot(plan, target, current);
            } else if (target.resultLifecycleState() == ACTIVE) {
                StoredFileRecord pathOwner = files
                        .findByIdOrganizationRefAndIdSpaceRefAndCanonicalPath(
                                plan.organizationRef(),
                                plan.spaceRef(),
                                requireText(target.targetPath(), "targetPath"))
                        .map(FileObjectJpaEntity::toStoredRecord)
                        .filter(record -> record.metadata().lifecycle() == ACTIVE)
                        .orElse(null);
                if (pathOwner != null && !plannedVictim(plan, target.targetPath(), pathOwner)) {
                    throw new ConcurrentFilesMutationException(plan.operationRef());
                }
            }
        }
        validateParents(plan);
    }

    private void validateFences(Sealed plan, List<StoredFileRecord> currentSnapshot) {
        Fence requestFence = plan.fences().stream()
                .filter(fence -> fence.fenceRole() == FenceRole.REQUEST_TARGET)
                .findFirst()
                .orElseThrow(() -> new CorruptFilesMutationException(
                        "the Files mutation request-target fence is missing"));
        StoredFileRecord currentRequest = byPath(currentSnapshot, requestFence.canonicalPath());
        String currentRequestEtag = strongEtag(currentRequest);
        if (plan.ifMatchCondition().supplied()
                && !plan.ifMatchCondition().matches(currentRequestEtag, true)) {
            throw new RequestPreconditionException(plan.operationRef());
        }
        if (plan.ifNoneMatchCondition().supplied()
                && plan.ifNoneMatchCondition().matches(currentRequestEtag, false)) {
            throw new RequestPreconditionException(plan.operationRef());
        }

        if (plan.destinationMustRemainAbsent()) {
            Fence destination = plan.fences().stream()
                    .filter(fence -> fence.fenceRole() == FenceRole.DESTINATION_TARGET)
                    .findFirst()
                    .orElseThrow(() -> new CorruptFilesMutationException(
                            "the Files mutation destination fence is missing"));
            if (byPath(currentSnapshot, destination.canonicalPath()) != null) {
                throw new RequestPreconditionException(plan.operationRef());
            }
        }

        for (Fence fence : plan.fences()) {
            StoredFileRecord current = byPath(currentSnapshot, fence.canonicalPath());
            if (fence.expectedPresence() == ExpectedPresence.ABSENT) {
                if (current != null) {
                    throw new ConcurrentFilesMutationException(plan.operationRef());
                }
                continue;
            }
            if (!matchesFence(fence, current, currentSnapshot)) {
                throw new ConcurrentFilesMutationException(plan.operationRef());
            }
        }
    }

    private boolean matchesFence(
            Fence fence,
            StoredFileRecord current,
            List<StoredFileRecord> currentSnapshot) {
        if (current == null) {
            return false;
        }
        CanonicalFileRecord metadata = current.metadata();
        FileObject object = metadata.object();
        if (!object.id().value().equals(fence.expectedFileRef())
                || !object.path().value().equals(fence.canonicalPath())
                || object.kind() != fence.expectedObjectKind()
                || metadata.lifecycle() != fence.expectedLifecycleState()
                || current.adapterRowVersion() != fence.expectedRowVersion()
                || !Objects.equals(strongEtag(current), fence.expectedStrongEtag())) {
            return false;
        }
        if (fence.expectedSubtreeDigest() == null) {
            return true;
        }
        String prefix = fence.canonicalPath() + "/";
        String actualMembership = com.massimotter.weave.backend.files.port.FilesMutationPlan
                .subtreeMembershipDigest(currentSnapshot.stream()
                        .filter(record -> record.metadata().object().path().value()
                                .equals(fence.canonicalPath())
                                || record.metadata().object().path().value().startsWith(prefix))
                        .map(record -> new Membership(
                                record.metadata().object().path().value(),
                                record.metadata().object().id().value()))
                        .toList());
        return constantEquals(actualMembership, fence.expectedSubtreeDigest());
    }

    private StoredFileRecord byPath(List<StoredFileRecord> records, String path) {
        return records.stream()
                .filter(record -> record.metadata().object().path().value().equals(path))
                .findFirst()
                .orElse(null);
    }

    private String strongEtag(StoredFileRecord record) {
        return record == null
                ? null
                : FilesEtags.strong(record.metadata().object(), record.metadata().version());
    }

    private void requireSourceSnapshot(Sealed plan, Target target, StoredFileRecord current) {
        CanonicalFileRecord metadata = current.metadata();
        FileObject object = metadata.object();
        if (metadata.lifecycle() != ACTIVE
                || !object.id().value().equals(target.sourceFileRef())
                || !object.path().value().equals(target.sourcePath())
                || object.kind() != target.objectKind()) {
            throw new ConcurrentFilesMutationException(plan.operationRef());
        }
        if (target.sourceReadBlobBinding() == null) {
            return;
        }
        if (current.blobBinding() == null
                || !current.blobBinding().opaqueReference().equals(target.sourceReadBlobBinding())
                || object.size() != target.sourceSize()
                || !Objects.equals(object.mediaType(), target.sourceMediaType())
                || !Objects.equals(metadata.contentDigest(), target.sourceContentDigest())
                || !Objects.equals(metadata.version().value(), target.sourceFileVersion())
                || !FilesEtags.strong(object, metadata.version()).equals(target.sourceStrongEtag())
                || !canonical(object.modifiedAt()).equals(target.sourceModifiedAt())
                || object.hidden() != target.sourceHidden()
                || !canonical(metadata.observedAt()).equals(target.sourceObservedAt())) {
            throw new ConcurrentFilesMutationException(plan.operationRef());
        }
    }

    private boolean plannedVictim(Sealed plan, String path, StoredFileRecord current) {
        return plan.targets().stream().anyMatch(target -> target.changeKind() == ChangeKind.TOMBSTONED
                && Objects.equals(path, target.sourcePath())
                && current.metadata().object().id().value().equals(target.targetFileRef()));
    }

    private void validateParents(Sealed plan) {
        for (Target target : plan.targets()) {
            if (target.resultLifecycleState() != ACTIVE || target.targetPath() == null) {
                continue;
            }
            FilePath parent = parent(new FilePath(target.targetPath()));
            if (parent.root()) {
                continue;
            }
            boolean plannedParent = plan.targets().stream().anyMatch(candidate ->
                    candidate.resultLifecycleState() == ACTIVE
                            && candidate.objectKind() == Kind.COLLECTION
                            && Objects.equals(candidate.targetPath(), parent.value()));
            boolean currentParent = files.findByIdOrganizationRefAndIdSpaceRefAndCanonicalPath(
                            plan.organizationRef(),
                            plan.spaceRef(),
                            parent.value())
                    .map(FileObjectJpaEntity::toStoredRecord)
                    .filter(record -> record.metadata().lifecycle() == ACTIVE
                            && record.metadata().object().kind() == Kind.COLLECTION)
                    .isPresent();
            if (!plannedParent && !currentParent) {
                throw new ConcurrentFilesMutationException(plan.operationRef());
            }
        }
    }

    private void validateApplicableLocks(OperationIntent intent, Sealed plan) {
        String presented = lockTokenDigest(intent);
        List<FilePath> writeRoots = writeRoots(plan);
        authority.activeLocks(plan.organizationRef(), plan.spaceRef(), now()).stream()
                .filter(lock -> writeRoots.stream().anyMatch(root -> applies(lock.path(), root)))
                .forEach(lock -> {
                    if (presented == null
                            || !constantEquals(lock.tokenDigest(), presented)
                            || !lock.ownerRef().equals(intent.actor().personRef())) {
                        throw new LockPreconditionException(plan.operationRef());
                    }
                });
    }

    private void applyLockMove(OperationIntent intent, Sealed plan) {
        if (plan.operationKind() != com.massimotter.weave.backend.files.port.FilesMutationPlan.OperationKind.MOVE) {
            return;
        }
        String tokenDigest = lockTokenDigest(intent);
        if (tokenDigest == null) {
            return;
        }
        Target root = mutationRoot(plan);
        FilePath source = new FilePath(requireText(root.sourcePath(), "sourcePath"));
        FilePath destination = new FilePath(requireText(root.targetPath(), "targetPath"));
        if (authority.activeLock(
                        plan.organizationRef(),
                        plan.spaceRef(),
                        source,
                        now())
                .isPresent()) {
            authority.moveLock(
                    plan.organizationRef(),
                    plan.spaceRef(),
                    source,
                    destination,
                    tokenDigest,
                    intent.actor().personRef(),
                    now());
        }
    }

    private FilePath operationRoot(Sealed plan) {
        Target root = mutationRoot(plan);
        return new FilePath(switch (plan.operationKind()) {
            case PUT, MKCOL -> requireText(root.targetPath(), "targetPath");
            case DELETE, COPY, MOVE -> requireText(root.sourcePath(), "sourcePath");
        });
    }

    private Target mutationRoot(Sealed plan) {
        return plan.targets().stream()
                .filter(target -> switch (plan.operationKind()) {
                    case PUT, MKCOL -> target.targetPath() != null;
                    case DELETE -> target.sourcePath() != null;
                    case COPY -> target.changeKind() == ChangeKind.COPIED;
                    case MOVE -> target.changeKind() == ChangeKind.MOVED;
                })
                .min((left, right) -> Integer.compare(
                        rootPath(left, plan).length(),
                        rootPath(right, plan).length()))
                .orElseThrow(() -> new CorruptFilesMutationException(
                        "the Files mutation operation root is missing"));
    }

    private List<FilePath> writeRoots(Sealed plan) {
        Target root = mutationRoot(plan);
        return switch (plan.operationKind()) {
            case PUT, MKCOL -> List.of(new FilePath(root.targetPath()));
            case DELETE -> List.of(new FilePath(root.sourcePath()));
            case COPY -> List.of(new FilePath(root.targetPath()));
            case MOVE -> List.of(new FilePath(root.sourcePath()), new FilePath(root.targetPath()));
        };
    }

    private boolean applies(FilePath lockedPath, FilePath requestPath) {
        String locked = lockedPath.value();
        String request = requestPath.value();
        return request.equals(locked)
                || request.startsWith(locked.endsWith("/") ? locked : locked + "/")
                || locked.startsWith(request.endsWith("/") ? request : request + "/");
    }

    private String lockTokenDigest(OperationIntent intent) {
        String reference = intent.objectRefs().getLast();
        if ("lock-token:none".equals(reference)) {
            return null;
        }
        return requireText(reference.substring("lock-token:".length()), "lockTokenDigest");
    }

    private FilePath parent(FilePath path) {
        if (path.root() || path.value().lastIndexOf('/') == 0) {
            return new FilePath("/");
        }
        return new FilePath(path.value().substring(0, path.value().lastIndexOf('/')));
    }

    private void requireIntentPlanLink(OperationIntent intent, Sealed plan) {
        if (!"files".equals(intent.domain())
                || (intent.state() != OperationIntent.State.CREATED
                        && intent.state() != OperationIntent.State.AMBIGUOUS
                        && intent.state() != OperationIntent.State.RECONCILING)
                || !intent.operationRef().equals(plan.operationRef())
                || !intent.organizationRef().equals(plan.organizationRef())
                || !intent.canonicalArgumentsDigest().equals(plan.canonicalArgumentsDigest())
                || intent.providerBindingRevision() != plan.providerBindingRevision()
                || !(intent.projection() instanceof ProtocolProjection projection)
                || !"webdav".equals(projection.protocol())
                || !operationName(plan).equals(projection.operation())
                || !intent.objectRefs().equals(expectedObjectRefs(plan, intent.objectRefs()))) {
            throw new IllegalArgumentException("Files intent and mutation plan do not match");
        }
    }

    private String operationName(Sealed plan) {
        return switch (plan.operationKind()) {
            case PUT -> "webdav-put";
            case MKCOL -> "webdav-mkcol";
            case COPY -> "webdav-copy";
            case MOVE -> "webdav-move";
            case DELETE -> "webdav-delete";
        };
    }

    private List<String> expectedObjectRefs(Sealed plan, List<String> actualObjectRefs) {
        Target root = plan.targets().stream()
                .filter(target -> switch (plan.operationKind()) {
                    case PUT, MKCOL -> target.targetPath() != null;
                    case DELETE -> target.sourcePath() != null;
                    case COPY -> target.changeKind() == ChangeKind.COPIED;
                    case MOVE -> target.changeKind() == ChangeKind.MOVED;
                })
                .min((left, right) -> Integer.compare(
                        rootPath(left, plan).length(),
                        rootPath(right, plan).length()))
                .orElseThrow(() -> new CorruptFilesMutationException(
                        "the Files mutation operation root is missing"));
        List<String> expected = switch (plan.operationKind()) {
            case PUT, MKCOL -> List.of(pathRef(root.targetPath()));
            case DELETE -> List.of(pathRef(root.sourcePath()));
            case COPY, MOVE -> List.of(pathRef(root.sourcePath()), pathRef(root.targetPath()));
        };
        if (actualObjectRefs.size() == expected.size() + 1
                && actualObjectRefs.getLast().matches(
                        "lock-token:(none|sha256:[a-f0-9]{64})")) {
            List<String> withLock = new ArrayList<>(expected);
            withLock.add(actualObjectRefs.getLast());
            return List.copyOf(withLock);
        }
        return expected;
    }

    private String rootPath(Target target, Sealed plan) {
        return switch (plan.operationKind()) {
            case PUT, MKCOL -> target.targetPath();
            case DELETE, COPY, MOVE -> target.sourcePath();
        };
    }

    private String pathRef(String path) {
        return "file-path:" + FilesMutationIntentService.digest(requireText(path, "path"));
    }

    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private Instant canonical(Instant value) {
        return value == null ? null : value.truncatedTo(ChronoUnit.MICROS);
    }

    private boolean constantEquals(String left, String right) {
        return left != null && right != null && java.security.MessageDigest.isEqual(
                left.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                right.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    public static final class ConcurrentFilesMutationException extends RuntimeException {
        public ConcurrentFilesMutationException(String operationRef) {
            super("native Files mutation changed concurrently: " + operationRef);
        }
    }

    public static final class RequestPreconditionException extends RuntimeException {
        public RequestPreconditionException(String operationRef) {
            super("native Files request precondition changed before finalization: " + operationRef);
        }
    }

    public static final class CorruptFilesMutationException
            extends NativeFilesMutationRepository.CorruptMutationStateException {
        public CorruptFilesMutationException(String message) {
            super(message);
        }
    }

    public static final class AuthorizationDeniedException extends RuntimeException {
        public AuthorizationDeniedException(String operationRef) {
            super("native Files authorization changed before finalization: " + operationRef);
        }
    }

    public static final class LockPreconditionException extends RuntimeException {
        public LockPreconditionException(String operationRef) {
            super("native Files lock precondition changed before finalization: " + operationRef);
        }
    }
}

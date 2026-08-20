package com.massimotter.weave.backend.service.files;

import com.massimotter.weave.backend.config.FilesRuntimeProperties;
import com.massimotter.weave.backend.config.WeaveNativeFilesProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.files.application.CanonicalFilesCommands;
import com.massimotter.weave.backend.files.application.CanonicalFilesBlobEffects;
import com.massimotter.weave.backend.files.application.CanonicalFilesMutationPlanner;
import com.massimotter.weave.backend.files.application.CanonicalFilesQueries;
import com.massimotter.weave.backend.files.application.CanonicalFilesTreeCommands;
import com.massimotter.weave.backend.files.application.FilesDigests;
import com.massimotter.weave.backend.files.application.FilesEtags;
import com.massimotter.weave.backend.files.application.FilesMutationRecords;
import com.massimotter.weave.backend.files.application.FilesMutationPlanningException;
import com.massimotter.weave.backend.files.application.FilesMutationTargetCodec;
import com.massimotter.weave.backend.files.application.NativeFilesMutationRepository;
import com.massimotter.weave.backend.files.application.NativeFilesMutationRepository.CommitProbe;
import com.massimotter.weave.backend.files.application.NativeFilesMutationRepository.CommitOutcome;
import com.massimotter.weave.backend.files.adapter.JpaFilesMutationRepository.AuthorizationDeniedException;
import com.massimotter.weave.backend.files.adapter.JpaFilesMutationRepository.ConcurrentFilesMutationException;
import com.massimotter.weave.backend.files.adapter.JpaFilesMutationRepository.LockPreconditionException;
import com.massimotter.weave.backend.files.adapter.JpaFilesMutationRepository.RequestPreconditionException;
import com.massimotter.weave.backend.files.application.CanonicalFilesBlobEffects.BlobEffectException;
import com.massimotter.weave.backend.files.application.FilesApplicationException;
import com.massimotter.weave.backend.files.application.FilesCommandException;
import com.massimotter.weave.backend.files.application.FilesCommandScope;
import com.massimotter.weave.backend.files.application.FilesScope;
import com.massimotter.weave.backend.files.application.FilesTreeCommandException;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileObject;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileVersion;
import com.massimotter.weave.backend.files.domain.FilesDomain.VersionedFile;
import com.massimotter.weave.backend.files.domain.FilesDomain.VersionedListing;
import com.massimotter.weave.backend.files.domain.FilesSearch.CandidatePage;
import com.massimotter.weave.backend.files.domain.FilesSearch.ScopeDepth;
import com.massimotter.weave.backend.files.port.BlobStorePort;
import com.massimotter.weave.backend.files.port.FilesAuthorityRepository;
import com.massimotter.weave.backend.files.port.FilesBlobProtectionPort;
import com.massimotter.weave.backend.files.port.FilesStreamingContentPort;
import com.massimotter.weave.backend.files.port.FilesMutationPlan;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.Sealed;
import com.massimotter.weave.backend.files.port.NativeFilesContentStore;
import com.massimotter.weave.backend.files.port.FilesProviderPort;
import com.massimotter.weave.backend.files.port.FilesWebDavSearchQualification;
import com.massimotter.weave.backend.files.port.NativeFilesDurableMutationPort;
import com.massimotter.weave.backend.files.port.ReplayableFileContent.StreamFactory;
import com.massimotter.weave.backend.files.port.StoredFileRecord;
import com.massimotter.weave.backend.files.port.VerifiedFileRead;
import com.massimotter.weave.backend.operation.domain.OperationIntent;
import com.massimotter.weave.backend.portability.ProviderConformanceProfile;
import com.massimotter.weave.backend.portability.ProviderConformanceProfile.MappingClass;
import com.massimotter.weave.backend.portability.ProviderReadiness;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.persistence.PersistenceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Native Files boot composition over provider-independent canonical use cases.
 *
 * <p>This class owns no Files domain behavior. It binds the canonical query, create/write, and
 * tree-command services to the configured metadata and BlobStore adapters and translates only
 * application failures into the established Server boundary.</p>
 */
@Component
@Primary
@ConditionalOnProperty(
        name = "weave.files.provider",
        havingValue = FilesRuntimeProperties.WEAVE_NATIVE,
        matchIfMissing = true)
public final class WeaveNativeFilesAdapter
        implements FilesProviderPort, NativeFilesDurableMutationPort, FilesStreamingContentPort {

    public static final String ADAPTER_KEY = "weave-native";

    private final BlobStorePort blobs;
    private final CanonicalFilesQueries queries;
    private final CanonicalFilesCommands commands;
    private final CanonicalFilesTreeCommands treeCommands;
    private final CanonicalFilesMutationPlanner mutationPlanner;
    private final CanonicalFilesBlobEffects blobEffects;
    private final NativeFilesMutationRepository mutationRepository;
    private final FilesMutationTargetCodec targetCodec;
    private final NativeFilesContentStore contentStore;
    private final Clock clock;
    private final AtomicBoolean contentIntegrityHealthy = new AtomicBoolean(true);

    @Autowired
    public WeaveNativeFilesAdapter(
            FilesAuthorityRepository authority,
            BlobStorePort blobs,
            WeaveNativeFilesProperties properties,
            NativeFilesMutationRepository mutationRepository,
            FilesMutationTargetCodec targetCodec,
            @Lazy NativeFilesContentStore contentStore) {
        this(
                authority,
                blobs,
                Clock.systemUTC(),
                properties.reconciliationLimit(),
                mutationRepository,
                targetCodec,
                contentStore);
    }

    WeaveNativeFilesAdapter(
            FilesAuthorityRepository authority,
            BlobStorePort blobs,
            WeaveNativeFilesProperties properties) {
        this(
                authority,
                blobs,
                Clock.systemUTC(),
                properties.reconciliationLimit(),
                null,
                null,
                null);
    }

    WeaveNativeFilesAdapter(
            FilesAuthorityRepository authority,
            BlobStorePort blobs,
            Clock clock,
            int reconciliationLimit) {
        this(authority, blobs, clock, reconciliationLimit, null, null, null);
    }

    WeaveNativeFilesAdapter(
            FilesAuthorityRepository authority,
            BlobStorePort blobs,
            Clock clock,
            int reconciliationLimit,
            NativeFilesMutationRepository mutationRepository,
            FilesMutationTargetCodec targetCodec) {
        this(
                authority,
                blobs,
                clock,
                reconciliationLimit,
                mutationRepository,
                targetCodec,
                null);
    }

    WeaveNativeFilesAdapter(
            FilesAuthorityRepository authority,
            BlobStorePort blobs,
            Clock clock,
            int reconciliationLimit,
            NativeFilesMutationRepository mutationRepository,
            FilesMutationTargetCodec targetCodec,
            NativeFilesContentStore contentStore) {
        FilesAuthorityRepository requiredAuthority = Objects.requireNonNull(
                authority,
                "authority must not be null");
        this.blobs = Objects.requireNonNull(blobs, "blobs must not be null");
        Clock requiredClock = clock == null ? Clock.systemUTC() : clock;
        this.clock = requiredClock;
        this.queries = new CanonicalFilesQueries(
                requiredAuthority,
                this.blobs,
                mutationRepository == null ? FilesBlobProtectionPort.none() : mutationRepository,
                reconciliationLimit);
        this.commands = new CanonicalFilesCommands(
                requiredAuthority,
                requiredClock);
        this.treeCommands = new CanonicalFilesTreeCommands(
                requiredAuthority,
                this.blobs,
                requiredClock);
        this.mutationPlanner = new CanonicalFilesMutationPlanner(requiredAuthority, requiredClock);
        this.blobEffects = new CanonicalFilesBlobEffects(this.blobs);
        this.mutationRepository = mutationRepository;
        this.targetCodec = targetCodec;
        this.contentStore = contentStore;
    }

    @Override
    public Sealed plan(
            OperationIntent intent,
            FilesRequestScope scope,
            Mutation mutation) {
        requireDurableComposition();
        OperationIntent requiredIntent = Objects.requireNonNull(intent, "intent must not be null");
        FilesRequestScope requiredScope = Objects.requireNonNull(scope, "scope must not be null");
        CanonicalFilesMutationPlanner.MutationScope mutationScope =
                new CanonicalFilesMutationPlanner.MutationScope(
                        requiredIntent.operationRef(),
                        requiredScope.organizationRef(),
                        requiredScope.spaceRef(),
                        requiredIntent.canonicalArgumentsDigest(),
                        requiredScope.providerBindingRevision());
        FilesMutationPlan.Draft draft;
        try {
            draft = switch (Objects.requireNonNull(mutation, "mutation must not be null")) {
                case Put put -> mutationPlanner.put(
                        mutationScope,
                        put.path(),
                        put.content(),
                        put.ifMatchCondition(),
                        put.ifNoneMatchCondition());
                case MakeCollection makeCollection -> mutationPlanner.createCollection(
                        mutationScope,
                        makeCollection.path(),
                        makeCollection.ifMatchCondition(),
                        makeCollection.ifNoneMatchCondition());
                case Copy copy -> mutationPlanner.copy(
                        mutationScope,
                        copy.source(),
                        copy.destination(),
                        copy.overwrite(),
                        copy.ifMatchCondition(),
                        copy.ifNoneMatchCondition());
                case Move move -> mutationPlanner.move(
                        mutationScope,
                        move.source(),
                        move.destination(),
                        move.overwrite(),
                        move.ifMatchCondition(),
                        move.ifNoneMatchCondition());
                case Delete delete -> mutationPlanner.delete(
                        mutationScope,
                        delete.path(),
                        delete.expectedVersion(),
                        delete.ifMatchCondition(),
                        delete.ifNoneMatchCondition());
            };
        } catch (FilesMutationPlanningException exception) {
            throw planningFailure(exception);
        }
        Instant sealedAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        return draft.seal(
                targetCodec.targetsDigest(draft.targets()),
                targetCodec.fencesDigest(draft.fences()),
                sealedAt);
    }

    @Override
    public NativeResult execute(
            OperationIntent intent,
            FilesRequestScope scope,
            Sealed suppliedPlan,
            Mutation mutation,
            String auditRef,
            NativeLockMove lockMove) {
        requireDurableComposition();
        Sealed plan = mutationRepository.requireSealed(suppliedPlan.operationRef());
        if (!plan.equals(suppliedPlan)) {
            throw new IllegalStateException("the native Files mutation plan changed after sealing");
        }

        StoredFileRecord resultRecord = null;
        FilesMutationPlan.Target rootTarget = null;
        if (!(mutation instanceof Delete)) {
            rootTarget = plan.targets().stream()
                    .filter(target -> target.resultLifecycleState()
                            == com.massimotter.weave.backend.files.domain.FilesAuthority.Lifecycle.ACTIVE)
                    .filter(target -> Objects.equals(target.targetPath(), mutation.resultPath().value()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("the native Files result target is missing"));
            resultRecord = FilesMutationRecords.resultRecord(plan, rootTarget);
        }
        FileObject item = resultRecord == null ? null : resultRecord.metadata().object();
        FileVersion version = resultRecord == null
                ? FileVersion.unknown()
                : resultRecord.metadata().version();
        String etag = item == null ? null : FilesEtags.strong(item, version);
        boolean created = rootTarget != null
                && rootTarget.changeKind()
                        == com.massimotter.weave.backend.files.domain.FilesChangeStream.ChangeKind.CREATED;
        String canonicalResult = item == null
                ? "deleted:" + mutation.resultPath().value()
                : item.id().value() + "\n" + item.path().value() + "\n" + etag;
        NativeFilesMutationRepository.LockMove repositoryLockMove = lockMove == null
                ? null
                : new NativeFilesMutationRepository.LockMove(
                        lockMove.source(),
                        lockMove.destination(),
                        lockMove.tokenDigest(),
                        lockMove.ownerRef());
        var initialProbe = mutationRepository.probe(plan.operationRef());
        if (initialProbe.outcome() == CommitOutcome.TERMINAL_FAILURE) {
            throw conflict(
                    "files-native-operation-terminal",
                    "The native Files operation already has a terminal failure.");
        }
        if (initialProbe.outcome() == CommitOutcome.CORRUPT) {
            throw unavailable(
                    "files-native-finalization-corrupt",
                    "The native Files finalization evidence is inconsistent.");
        }
        if (initialProbe.outcome() == CommitOutcome.SUCCEEDED) {
            return new NativeResult(item, version, etag, created);
        }

        OperationIntent executionIntent = initialProbe.intent();
        if (executionIntent.state() == OperationIntent.State.AMBIGUOUS) {
            executionIntent = mutationRepository.beginReconciliation(executionIntent);
            if (executionIntent.state().terminal()) {
                return replayConcurrentFinalization(
                        plan.operationRef(), item, version, etag, created);
            }
            if (executionIntent.state() != OperationIntent.State.RECONCILING) {
                throw unavailable(
                        "files-native-finalization-outcome-unknown",
                        "The native Files reconciliation state could not be established.");
            }
        }
        try {
            var putContent = mutation instanceof Put put ? put.content() : null;
            blobEffects.execute(plan, putContent);
            mutationRepository.finalizeSuccess(
                    executionIntent,
                    plan,
                    FilesDigests.sha256(canonicalResult),
                    auditRef,
                    repositoryLockMove);
        } catch (RuntimeException failure) {
            return recoverFinalizationOutcome(
                    plan, item, version, etag, created, auditRef, failure);
        }
        return new NativeResult(item, version, etag, created);
    }

    private NativeResult replayConcurrentFinalization(
            String operationRef,
            FileObject item,
            FileVersion version,
            String etag,
            boolean created) {
        CommitProbe probe;
        try {
            probe = mutationRepository.probe(operationRef);
        } catch (RuntimeException unavailableProbe) {
            throw unavailable(
                    "files-native-finalization-outcome-unknown",
                    "The concurrent native Files finalization outcome could not be proven.");
        }
        return switch (probe.outcome()) {
            case SUCCEEDED -> new NativeResult(item, version, etag, created);
            case TERMINAL_FAILURE -> throw conflict(
                    "files-native-operation-terminal",
                    "The native Files operation already has a terminal failure.");
            case NOT_COMMITTED, CORRUPT -> throw unavailable(
                    "files-native-finalization-corrupt",
                    "The concurrent native Files finalization evidence is inconsistent.");
        };
    }

    private NativeResult recoverFinalizationOutcome(
            Sealed plan,
            FileObject item,
            FileVersion version,
            String etag,
            boolean created,
            String auditRef,
            RuntimeException failure) {
        NativeFilesMutationRepository.CommitProbe probe;
        try {
            probe = mutationRepository.probe(plan.operationRef());
        } catch (RuntimeException unavailableProbe) {
            throw unavailable(
                    "files-native-finalization-outcome-unknown",
                    "The native Files finalization outcome could not be proven.");
        }
        return switch (probe.outcome()) {
            case SUCCEEDED -> new NativeResult(item, version, etag, created);
            case NOT_COMMITTED -> {
                if (deterministicFailure(failure) || persistenceFailure(failure)) {
                    boolean denied = failure instanceof AuthorizationDeniedException
                            || (failure instanceof ApiErrorException apiFailure
                                    && apiFailure.status() == HttpStatus.FORBIDDEN);
                    CommitOutcome settlement = settleProvenNonCommit(
                            probe.intent(),
                            denied,
                            FilesDigests.sha256(failureCode(failure)),
                            auditRef,
                            plan,
                            failure);
                    if (settlement == CommitOutcome.SUCCEEDED) {
                        yield new NativeResult(item, version, etag, created);
                    }
                    throw translatedFinalizationFailure(failure);
                }
                try {
                    mutationRepository.markAmbiguous(
                            probe.intent(),
                            FilesDigests.sha256(
                                    "native-files-uncertain:" + failure.getClass().getName()));
                } catch (RuntimeException transitionFailure) {
                    throw unavailable(
                            "files-native-finalization-outcome-unknown",
                            "The native Files finalization outcome could not be recorded.");
                }
                throw unavailable(
                        "files-native-finalization-outcome-unknown",
                        "The native Files finalization outcome requires reconciliation.");
            }
            case TERMINAL_FAILURE -> throw conflict(
                    "files-native-operation-terminal",
                    "The native Files operation already has a terminal failure.");
            case CORRUPT -> throw unavailable(
                    "files-native-finalization-corrupt",
                    "The native Files finalization evidence is inconsistent.");
        };
    }

    private CommitOutcome settleProvenNonCommit(
            OperationIntent intent,
            boolean denied,
            String resultDigest,
            String auditRef,
            Sealed plan,
            RuntimeException originalFailure) {
        RuntimeException lastSettlementFailure = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            OperationIntent settled;
            try {
                settled = mutationRepository.recordFailure(
                        intent, denied, resultDigest, auditRef);
            } catch (RuntimeException settlementFailure) {
                lastSettlementFailure = settlementFailure;
                CommitProbe settlementProbe;
                try {
                    settlementProbe = mutationRepository.probe(plan.operationRef());
                } catch (RuntimeException unavailableProbe) {
                    settlementFailure.addSuppressed(unavailableProbe);
                    continue;
                }
                switch (settlementProbe.outcome()) {
                    case SUCCEEDED -> {
                        return CommitOutcome.SUCCEEDED;
                    }
                    case TERMINAL_FAILURE -> {
                        return CommitOutcome.TERMINAL_FAILURE;
                    }
                    case CORRUPT -> throw unavailable(
                            "files-native-finalization-corrupt",
                            "The native Files failure settlement evidence is inconsistent.");
                    case NOT_COMMITTED -> {
                        // The settlement is proven absent, so a bounded retry is safe.
                    }
                }
                continue;
            }
            if (settled.state() == OperationIntent.State.SUCCEEDED) {
                return CommitOutcome.SUCCEEDED;
            }
            if (settled.state() == OperationIntent.State.DENIED
                    || settled.state() == OperationIntent.State.FAILED) {
                return CommitOutcome.TERMINAL_FAILURE;
            }
            throw unavailable(
                    "files-native-finalization-corrupt",
                    "The native Files failure settlement returned a nonterminal state.");
        }
        if (lastSettlementFailure != null) {
            originalFailure.addSuppressed(lastSettlementFailure);
        }
        throw unavailable(
                "files-native-finalization-outcome-unknown",
                "The native Files failure settlement could not be committed.");
    }

    private boolean deterministicFailure(RuntimeException failure) {
        return failure instanceof AuthorizationDeniedException
                || failure instanceof LockPreconditionException
                || failure instanceof ConcurrentFilesMutationException
                || failure instanceof RequestPreconditionException
                || failure instanceof BlobEffectException
                || (failure instanceof ApiErrorException apiFailure
                        && apiFailure.status().is4xxClientError());
    }

    private boolean persistenceFailure(RuntimeException failure) {
        return failure instanceof PersistenceException || failure instanceof DataAccessException;
    }

    private String failureCode(RuntimeException failure) {
        if (failure instanceof ApiErrorException apiFailure) {
            return apiFailure.code();
        }
        if (failure instanceof AuthorizationDeniedException) {
            return "files-native-authorization-denied";
        }
        if (failure instanceof LockPreconditionException) {
            return "files-native-lock-precondition-failed";
        }
        if (failure instanceof ConcurrentFilesMutationException) {
            return "files-native-concurrent-mutation";
        }
        if (failure instanceof RequestPreconditionException) {
            return "files-webdav-precondition-failed";
        }
        if (persistenceFailure(failure)) {
            return "files-native-persistence-failed";
        }
        return "files-native-blob-effect-failed";
    }

    private ApiErrorException translatedFinalizationFailure(RuntimeException failure) {
        if (failure instanceof ApiErrorException apiFailure) {
            return apiFailure;
        }
        if (failure instanceof AuthorizationDeniedException) {
            return new ApiErrorException(
                    HttpStatus.FORBIDDEN,
                    "files-forbidden",
                    "Files access was revoked before the mutation committed.",
                    Map.of("module", "files", "adapter", ADAPTER_KEY, "diagnosticsRedacted", true));
        }
        if (failure instanceof LockPreconditionException) {
            return new ApiErrorException(
                    HttpStatus.LOCKED,
                    "file-locked",
                    "The Files lock precondition changed before the mutation committed.",
                    Map.of("module", "files", "adapter", ADAPTER_KEY, "diagnosticsRedacted", true));
        }
        if (failure instanceof ConcurrentFilesMutationException) {
            return conflict(
                    "files-native-concurrent-mutation",
                    "The native Files mutation lost a concurrent precondition race.");
        }
        if (failure instanceof RequestPreconditionException) {
            return precondition("A persisted WebDAV precondition changed before commit.");
        }
        if (persistenceFailure(failure)) {
            return unavailable(
                    "files-native-persistence-failed",
                    "The native Files mutation did not commit after bounded persistence retries.");
        }
        return conflict(
                "files-native-blob-effect-failed",
                "The planned native Files blob effect could not be proven.");
    }

    private ApiErrorException planningFailure(FilesMutationPlanningException exception) {
        return switch (exception.code()) {
            case NOT_FOUND -> notFound("mutation-plan", exception.getMessage());
            case PRECONDITION_FAILED -> precondition(exception.getMessage());
            case PATH_CONFLICT -> conflict("files-native-path-conflict", exception.getMessage());
            case PARENT_MISSING -> conflict("files-native-parent-missing", exception.getMessage());
            case PARENT_NOT_COLLECTION -> conflict("files-native-parent-not-collection", exception.getMessage());
            case INVALID_BLOB_BINDING -> conflict("files-native-metadata-blob-mismatch", exception.getMessage());
        };
    }

    private void requireDurableComposition() {
        if (mutationRepository == null || targetCodec == null) {
            throw new IllegalStateException("native Files durable mutation composition is unavailable");
        }
    }

    @Override
    public FilesProviderPort scoped(FilesRequestScope scope) {
        return new Scoped(Objects.requireNonNull(scope, "scope must not be null"));
    }

    @Override
    public boolean configured() {
        return blobs.configured();
    }

    @Override
    public ProviderReadiness readiness() {
        if (!configured()) {
            return ProviderReadiness.degraded("files-native-blob-store-not-configured");
        }
        if (contentStore == null || !contentIntegrityHealthy.get()) {
            return ProviderReadiness.degraded("files-native-streaming-not-ready");
        }
        try {
            contentStore.requireStreamingReady();
            contentStore.contentProfile();
            return ProviderReadiness.ready("files-native-ready");
        } catch (RuntimeException unavailable) {
            return ProviderReadiness.degraded("files-native-streaming-not-ready");
        }
    }

    @Override
    public ProviderConformanceProfile conformanceProfile() {
        return new ProviderConformanceProfile(
                "files",
                ADAPTER_KEY,
                Set.of(
                        "list",
                        "read",
                        "write",
                        "create_collection",
                        "delete",
                        "copy",
                        "move",
                        "versions",
                        "locks",
                        "files.webdav_basicsearch",
                        "files.content_streaming_read",
                        "files.content_streaming_write"),
                Map.of(
                        "canonicalId", MappingClass.PORTABLE,
                        "path", MappingClass.PORTABLE,
                        "content", MappingClass.PORTABLE,
                        "mediaType", MappingClass.PORTABLE,
                        "version", MappingClass.PORTABLE,
                        "lock", MappingClass.PORTABLE,
                        "share", MappingClass.UNSUPPORTED),
                true,
                true,
                true);
    }

    @Override
    public FilesWebDavSearchQualification webDavBasicSearchQualification() {
        return FilesWebDavSearchQualification.nativeVerified(Instant.now());
    }

    @Override public VersionedListing list(FilePath path) { throw unscoped(); }
    @Override public Optional<VersionedFile> find(FilePath path) { throw unscoped(); }
    @Override public CandidatePage searchCandidates(
            FilePath scopePath, ScopeDepth scopeDepth, int maxCandidates) { throw unscoped(); }
    @Override public FileObject createCollection(FilePath path) { throw unscoped(); }
    @Override public FileObject copy(FilePath source, FilePath destination, boolean overwrite) { throw unscoped(); }
    @Override public FileObject move(FilePath source, FilePath destination, boolean overwrite) { throw unscoped(); }
    @Override public void delete(FilePath path, FileVersion expectedVersion) { throw unscoped(); }
    @Override public ContentProfile contentProfile() { return requireContentStore().contentProfile(); }
    @Override public void requireStreamingReady() {
        if (!contentIntegrityHealthy.get()) {
            throw new IllegalStateException("native Files content integrity is not ready");
        }
        requireContentStore().requireStreamingReady();
    }
    @Override public Ingress receive(Long declaredLength, String mediaType, StreamFactory requestBody) { throw unscoped(); }
    @Override public VerifiedFileRead inspect(FilePath path) { throw unscoped(); }
    @Override public Egress verify(VerifiedFileRead read) { throw unscoped(); }

    public ReconciliationReport reconcile(FilesRequestScope scope) {
        try {
            CanonicalFilesQueries.ReconciliationReport report = queries.reconcile(queryScope(scope));
            contentIntegrityHealthy.set(report.inconsistentMetadataRecords() == 0);
            return new ReconciliationReport(
                    report.activeMetadataRecords(),
                    report.inventoriedBlobs(),
                    report.orphanBlobsDeleted(),
                    report.inconsistentMetadataRecords());
        } catch (FilesApplicationException exception) {
            throw queryFailure(exception, "reconcile");
        }
    }

    public record ReconciliationReport(
            int activeMetadataRecords,
            int inventoriedBlobs,
            int orphanBlobsDeleted,
            int inconsistentMetadataRecords) {
    }

    private VersionedListing list(FilesRequestScope scope, FilePath path) {
        try {
            return queries.list(queryScope(scope), path);
        } catch (FilesApplicationException exception) {
            throw queryFailure(exception, "list");
        }
    }

    private Optional<VersionedFile> find(FilesRequestScope scope, FilePath path) {
        try {
            return queries.find(queryScope(scope), path);
        } catch (FilesApplicationException exception) {
            throw queryFailure(exception, "find");
        }
    }

    private CandidatePage searchCandidates(
            FilesRequestScope scope,
            FilePath scopePath,
            ScopeDepth scopeDepth,
            int maxCandidates) {
        try {
            return queries.searchCandidates(
                    queryScope(scope),
                    scopePath,
                    scopeDepth,
                    maxCandidates);
        } catch (FilesApplicationException exception) {
            throw queryFailure(exception, "search-candidates");
        }
    }

    private VerifiedFileRead inspect(FilesRequestScope scope, FilePath path) {
        requireContentStore();
        try {
            return queries.openRead(queryScope(scope), path);
        } catch (FilesApplicationException exception) {
            if (exception.code() == FilesApplicationException.Code.CONTENT_INTEGRITY_FAILED
                    || exception.code() == FilesApplicationException.Code.INVALID_BLOB_REFERENCE) {
                contentIntegrityHealthy.set(false);
                throw unavailable(
                        "file-content-integrity-unavailable",
                        "The native Files content snapshot could not be verified.");
            }
            throw queryFailure(exception, "read-snapshot");
        }
    }

    private NativeFilesContentStore requireContentStore() {
        if (contentStore == null) {
            throw unavailable(
                    "files-streaming-not-supported",
                    "The native Files bounded-content store is unavailable.");
        }
        return contentStore;
    }

    private FileObject createCollection(FilesRequestScope scope, FilePath path) {
        try {
            return commands.createCollection(commandScope(scope), path);
        } catch (FilesCommandException exception) {
            throw commandFailure(exception);
        }
    }

    private FileObject copy(
            FilesRequestScope scope,
            FilePath source,
            FilePath destination,
            boolean overwrite) {
        try {
            return treeCommands.copy(commandScope(scope), source, destination, overwrite);
        } catch (FilesTreeCommandException exception) {
            throw treeFailure(exception, "copy");
        }
    }

    private FileObject move(
            FilesRequestScope scope,
            FilePath source,
            FilePath destination,
            boolean overwrite) {
        try {
            return treeCommands.move(commandScope(scope), source, destination, overwrite);
        } catch (FilesTreeCommandException exception) {
            throw treeFailure(exception, "move");
        }
    }

    private void delete(
            FilesRequestScope scope,
            FilePath path,
            FileVersion expectedVersion) {
        try {
            treeCommands.delete(commandScope(scope), path, expectedVersion);
        } catch (FilesTreeCommandException exception) {
            throw treeFailure(exception, "delete");
        }
    }

    private FilesScope queryScope(FilesRequestScope scope) {
        FilesRequestScope required = Objects.requireNonNull(scope, "scope must not be null");
        return new FilesScope(required.organizationRef(), required.spaceRef());
    }

    private FilesCommandScope commandScope(FilesRequestScope scope) {
        FilesRequestScope required = Objects.requireNonNull(scope, "scope must not be null");
        return new FilesCommandScope(
                required.organizationRef(),
                required.spaceRef(),
                required.providerBindingRevision());
    }

    private ApiErrorException queryFailure(
            FilesApplicationException exception,
            String operation) {
        return switch (exception.code()) {
            case NOT_FOUND -> notFound(operation, exception.getMessage());
            case NOT_A_COLLECTION -> conflict(
                    "files-native-not-a-collection", exception.getMessage());
            case NOT_A_FILE -> conflict(
                    "files-native-not-a-file", exception.getMessage());
            case INVALID_BLOB_REFERENCE -> conflict(
                    "files-native-metadata-blob-mismatch", exception.getMessage());
            case CONTENT_INTEGRITY_FAILED -> conflict(
                    "read-stream".equals(operation)
                            ? "files-native-content-digest-mismatch"
                            : "files-native-metadata-blob-mismatch",
                    exception.getMessage());
        };
    }

    private ApiErrorException commandFailure(FilesCommandException exception) {
        String code = switch (exception.code()) {
            case PATH_CONFLICT -> "files-native-path-conflict";
            case PARENT_MISSING -> "files-native-parent-missing";
            case PARENT_NOT_COLLECTION -> "files-native-parent-not-collection";
            case METADATA_CONFLICT -> "files-native-metadata-conflict";
        };
        return conflict(code, exception.getMessage());
    }

    private ApiErrorException treeFailure(
            FilesTreeCommandException exception,
            String operation) {
        String code = exception.code().name();
        if ("NOT_FOUND".equals(code)) {
            return notFound(operation, exception.getMessage());
        }
        if ("PRECONDITION_FAILED".equals(code)
                || "OVERWRITE_PRECONDITION_FAILED".equals(code)
                || "VERSION_PRECONDITION_FAILED".equals(code)) {
            return precondition(exception.getMessage());
        }
        if ("PARENT_MISSING".equals(code)) {
            return conflict("files-native-parent-missing", exception.getMessage());
        }
        if ("PARENT_NOT_COLLECTION".equals(code)) {
            return conflict("files-native-parent-not-collection", exception.getMessage());
        }
        if ("TREE_CONFLICT".equals(code)
                || "INVALID_TREE_OPERATION".equals(code)) {
            return conflict("files-native-tree-conflict", exception.getMessage());
        }
        if ("INVALID_BLOB_REFERENCE".equals(code)
                || "CONTENT_INTEGRITY_FAILED".equals(code)) {
            return conflict("files-native-metadata-blob-mismatch", exception.getMessage());
        }
        return conflict("files-native-metadata-conflict", exception.getMessage());
    }

    private ApiErrorException unscoped() {
        return conflict(
                "files-native-scope-required",
                "Native Files operations require an explicit organization/space scope.");
    }

    private ApiErrorException notFound(String operation, String message) {
        return new ApiErrorException(
                HttpStatus.NOT_FOUND,
                "file-not-found",
                message,
                Map.of(
                        "module", "files",
                        "operation", operation,
                        "diagnosticsRedacted", true));
    }

    private ApiErrorException precondition(String message) {
        return new ApiErrorException(
                HttpStatus.PRECONDITION_FAILED,
                "files-precondition-failed",
                message,
                Map.of(
                        "module", "files",
                        "adapter", ADAPTER_KEY,
                        "diagnosticsRedacted", true));
    }

    private ApiErrorException conflict(String code, String message) {
        return new ApiErrorException(
                HttpStatus.CONFLICT,
                code,
                message,
                Map.of(
                        "module", "files",
                        "adapter", ADAPTER_KEY,
                        "diagnosticsRedacted", true));
    }

    private ApiErrorException unavailable(String code, String message) {
        return new ApiErrorException(
                HttpStatus.SERVICE_UNAVAILABLE,
                code,
                message,
                Map.of(
                        "module", "files",
                        "adapter", ADAPTER_KEY,
                        "diagnosticsRedacted", true));
    }

    private final class Scoped implements FilesProviderPort, FilesStreamingContentPort {
        private final FilesRequestScope scope;

        private Scoped(FilesRequestScope scope) {
            this.scope = scope;
        }

        @Override public FilesProviderPort scoped(FilesRequestScope next) { return WeaveNativeFilesAdapter.this.scoped(next); }
        @Override public boolean configured() { return WeaveNativeFilesAdapter.this.configured(); }
        @Override public ProviderReadiness readiness() { return WeaveNativeFilesAdapter.this.readiness(); }
        @Override public ProviderConformanceProfile conformanceProfile() { return WeaveNativeFilesAdapter.this.conformanceProfile(); }
        @Override public FilesWebDavSearchQualification webDavBasicSearchQualification() {
            return WeaveNativeFilesAdapter.this.webDavBasicSearchQualification();
        }
        @Override public VersionedListing list(FilePath path) { return WeaveNativeFilesAdapter.this.list(scope, path); }
        @Override public Optional<VersionedFile> find(FilePath path) { return WeaveNativeFilesAdapter.this.find(scope, path); }
        @Override public CandidatePage searchCandidates(
                FilePath scopePath, ScopeDepth scopeDepth, int maxCandidates) {
            return WeaveNativeFilesAdapter.this.searchCandidates(
                    scope, scopePath, scopeDepth, maxCandidates);
        }
        @Override public FileObject createCollection(FilePath path) { return WeaveNativeFilesAdapter.this.createCollection(scope, path); }
        @Override public FileObject copy(FilePath source, FilePath destination, boolean overwrite) { return WeaveNativeFilesAdapter.this.copy(scope, source, destination, overwrite); }
        @Override public FileObject move(FilePath source, FilePath destination, boolean overwrite) { return WeaveNativeFilesAdapter.this.move(scope, source, destination, overwrite); }
        @Override public void delete(FilePath path, FileVersion expectedVersion) { WeaveNativeFilesAdapter.this.delete(scope, path, expectedVersion); }
        @Override public ContentProfile contentProfile() { return requireContentStore().contentProfile(); }
        @Override public void requireStreamingReady() {
            WeaveNativeFilesAdapter.this.requireStreamingReady();
        }
        @Override public Ingress receive(Long declaredLength, String mediaType, StreamFactory requestBody) {
            return requireContentStore().receive(declaredLength, mediaType, requestBody);
        }
        @Override public VerifiedFileRead inspect(FilePath path) {
            return WeaveNativeFilesAdapter.this.inspect(scope, path);
        }
        @Override public Egress verify(VerifiedFileRead read) {
            try {
                return requireContentStore().verify(read);
            } catch (ApiErrorException failure) {
                if ("file-content-integrity-unavailable".equals(failure.code())) {
                    contentIntegrityHealthy.set(false);
                }
                throw failure;
            }
        }
    }
}

package com.massimotter.weave.backend.files.application;

import static com.massimotter.weave.backend.files.application.FilesMutationPlanningException.Code.INVALID_BLOB_BINDING;
import static com.massimotter.weave.backend.files.application.FilesMutationPlanningException.Code.NOT_FOUND;
import static com.massimotter.weave.backend.files.application.FilesMutationPlanningException.Code.PARENT_MISSING;
import static com.massimotter.weave.backend.files.application.FilesMutationPlanningException.Code.PARENT_NOT_COLLECTION;
import static com.massimotter.weave.backend.files.application.FilesMutationPlanningException.Code.PATH_CONFLICT;
import static com.massimotter.weave.backend.files.application.FilesMutationPlanningException.Code.PRECONDITION_FAILED;
import static com.massimotter.weave.backend.files.domain.FilesAuthority.Lifecycle.ACTIVE;
import static com.massimotter.weave.backend.files.domain.FilesAuthority.Lifecycle.TOMBSTONED;

import com.massimotter.weave.backend.files.domain.FilesAuthority.CanonicalFileRecord;
import com.massimotter.weave.backend.files.domain.FilesChangeStream.ChangeKind;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileId;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileObject;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileVersion;
import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import com.massimotter.weave.backend.files.port.FilesAuthorityRepository;
import com.massimotter.weave.backend.files.port.FilesMutationPlan;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.Draft;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.EntityTagCondition;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.Fence;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.FenceRole;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.Membership;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.OperationKind;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.Target;
import com.massimotter.weave.backend.files.port.ReplayableFileContent;
import com.massimotter.weave.backend.files.port.StoredFileRecord;
import com.massimotter.weave.backend.files.port.StoredFileRecord.BlobBinding;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Builds the complete deterministic native Files mutation target set without blob access. */
public final class CanonicalFilesMutationPlanner {

    private static final Comparator<String> UTF8_UNSIGNED = CanonicalFilesMutationPlanner::compareUtf8;

    private final FilesAuthorityRepository authority;
    private final Clock clock;

    public CanonicalFilesMutationPlanner(FilesAuthorityRepository authority, Clock clock) {
        this.authority = Objects.requireNonNull(authority, "authority must not be null");
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public Draft put(
            MutationScope scope,
            FilePath path,
            ReplayableFileContent content) {
        return put(
                scope,
                path,
                content,
                EntityTagCondition.notSupplied(),
                EntityTagCondition.notSupplied());
    }

    public Draft put(
            MutationScope scope,
            FilePath path,
            ReplayableFileContent content,
            EntityTagCondition ifMatch,
            EntityTagCondition ifNoneMatch) {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(content, "content must not be null");
        List<StoredFileRecord> records = active(scope);
        ensureParent(records, path);
        StoredFileRecord existing = byPath(records, path).orElse(null);
        requireRequestConditions(existing, ifMatch, ifNoneMatch);
        if (existing != null && existing.metadata().object().kind() != Kind.FILE) {
            throw failure(PATH_CONFLICT, "A collection already exists at the requested Files path.");
        }

        String digest = content.sha256Digest();
        FileId id = existing == null
                ? canonicalId(scope)
                : existing.metadata().object().id();
        Instant now = now();
        FileVersion version = new FileVersion(digest);
        FileObject resultObject = new FileObject(
                id,
                path,
                Kind.FILE,
                content.sizeBytes(),
                content.mediaType(),
                now,
                false);
        StoredFileRecord result = active(
                scope,
                resultObject,
                version,
                digest,
                new BlobBinding(blobReference(id, digest)),
                now);
        ChangeKind kind = existing == null ? ChangeKind.CREATED : ChangeKind.CONTENT_UPDATED;
        return draft(scope, OperationKind.PUT, ifMatch, ifNoneMatch, false, List.of(new PlannedTarget(
                kind,
                existing,
                existing == null ? null : existing.metadata().object().id().value(),
                existing == null ? null : existing.metadata().object().path().value(),
                result.metadata().object().path().value(),
                result)), List.of(observation(FenceRole.REQUEST_TARGET, path, existing, null)));
    }

    public Draft createCollection(MutationScope scope, FilePath path) {
        return createCollection(
                scope, path, EntityTagCondition.notSupplied(), EntityTagCondition.notSupplied());
    }

    public Draft createCollection(
            MutationScope scope,
            FilePath path,
            EntityTagCondition ifMatch,
            EntityTagCondition ifNoneMatch) {
        Objects.requireNonNull(path, "path must not be null");
        List<StoredFileRecord> records = active(scope);
        ensureParent(records, path);
        StoredFileRecord existing = byPath(records, path).orElse(null);
        requireRequestConditions(existing, ifMatch, ifNoneMatch);
        if (existing != null) {
            throw failure(PATH_CONFLICT, "A Files object already exists at the requested path.");
        }
        Instant now = now();
        FileObject object = new FileObject(
                canonicalId(scope),
                path,
                Kind.COLLECTION,
                0,
                null,
                now,
                false);
        StoredFileRecord result = active(
                scope,
                object,
                collectionVersion(object.id(), path),
                null,
                null,
                now);
        return draft(scope, OperationKind.MKCOL, ifMatch, ifNoneMatch, false, List.of(new PlannedTarget(
                ChangeKind.CREATED,
                null,
                null,
                null,
                path.value(),
                result)), List.of(observation(FenceRole.REQUEST_TARGET, path, null, null)));
    }

    public Draft copy(
            MutationScope scope,
            FilePath source,
            FilePath destination,
            boolean overwrite) {
        return copy(
                scope,
                source,
                destination,
                overwrite,
                EntityTagCondition.notSupplied(),
                EntityTagCondition.notSupplied());
    }

    public Draft copy(
            MutationScope scope,
            FilePath source,
            FilePath destination,
            boolean overwrite,
            EntityTagCondition ifMatch,
            EntityTagCondition ifNoneMatch) {
        requireTreeArguments(scope, source, destination);
        List<StoredFileRecord> records = active(scope);
        List<StoredFileRecord> sourceTree = tree(records, source);
        if (sourceTree.isEmpty()) {
            throw failure(NOT_FOUND, "The requested file or folder was not found.");
        }
        StoredFileRecord sourceRoot = byPath(sourceTree, source).orElseThrow();
        requireRequestConditions(sourceRoot, ifMatch, ifNoneMatch);
        ensureParent(records, destination);
        List<StoredFileRecord> destinationTree = tree(records, destination);
        if (!destinationTree.isEmpty() && !overwrite) {
            throw failure(PRECONDITION_FAILED, "Overwrite is false and the destination already exists.");
        }

        Instant now = now();
        List<PlannedTarget> targets = new ArrayList<>();
        destinationTree.forEach(record -> targets.add(tombstone(record, now)));
        for (StoredFileRecord sourceRecord : sourceTree) {
            CanonicalFileRecord sourceMetadata = sourceRecord.metadata();
            FilePath copiedPath = substitute(sourceMetadata.object().path(), source, destination);
            FileId copiedId = copyId(scope, sourceMetadata.object().id(), copiedPath);
            BlobBinding copiedBinding = null;
            if (sourceMetadata.object().kind() == Kind.FILE) {
                requireFileBinding(sourceRecord);
                copiedBinding = new BlobBinding(blobReference(copiedId, sourceMetadata.contentDigest()));
            }
            FileObject copied = new FileObject(
                    copiedId,
                    copiedPath,
                    sourceMetadata.object().kind(),
                    sourceMetadata.object().size(),
                    sourceMetadata.object().mediaType(),
                    now,
                    sourceMetadata.object().hidden());
            StoredFileRecord result = active(
                    scope,
                    copied,
                    copied.kind() == Kind.COLLECTION
                            ? collectionVersion(copied.id(), copiedPath)
                            : sourceMetadata.version(),
                    sourceMetadata.contentDigest(),
                    copiedBinding,
                    now);
            targets.add(new PlannedTarget(
                    ChangeKind.COPIED,
                    sourceRecord,
                    sourceMetadata.object().id().value(),
                    sourceMetadata.object().path().value(),
                    copiedPath.value(),
                    result));
        }
        return draft(
                scope,
                OperationKind.COPY,
                ifMatch,
                ifNoneMatch,
                !overwrite,
                targets,
                treeFences(source, sourceTree, destination, destinationTree));
    }

    public Draft move(
            MutationScope scope,
            FilePath source,
            FilePath destination,
            boolean overwrite) {
        return move(
                scope,
                source,
                destination,
                overwrite,
                EntityTagCondition.notSupplied(),
                EntityTagCondition.notSupplied());
    }

    public Draft move(
            MutationScope scope,
            FilePath source,
            FilePath destination,
            boolean overwrite,
            EntityTagCondition ifMatch,
            EntityTagCondition ifNoneMatch) {
        requireTreeArguments(scope, source, destination);
        List<StoredFileRecord> records = active(scope);
        List<StoredFileRecord> sourceTree = tree(records, source);
        if (sourceTree.isEmpty()) {
            throw failure(NOT_FOUND, "The requested file or folder was not found.");
        }
        StoredFileRecord sourceRoot = byPath(sourceTree, source).orElseThrow();
        requireRequestConditions(sourceRoot, ifMatch, ifNoneMatch);
        ensureParent(records, destination);
        List<StoredFileRecord> destinationTree = tree(records, destination);
        if (!destinationTree.isEmpty() && !overwrite) {
            throw failure(PRECONDITION_FAILED, "Overwrite is false and the destination already exists.");
        }

        Instant now = now();
        List<PlannedTarget> targets = new ArrayList<>();
        destinationTree.forEach(record -> targets.add(tombstone(record, now)));
        for (StoredFileRecord sourceRecord : sourceTree) {
            CanonicalFileRecord sourceMetadata = sourceRecord.metadata();
            if (sourceMetadata.object().kind() == Kind.FILE) {
                requireFileBinding(sourceRecord);
            }
            FilePath movedPath = substitute(sourceMetadata.object().path(), source, destination);
            FileVersion version = sourceMetadata.object().kind() == Kind.COLLECTION
                    ? collectionVersion(sourceMetadata.object().id(), movedPath)
                    : new FileVersion(FilesDigests.sha256(
                            String.valueOf(sourceMetadata.version().value()) + "\u0000" + movedPath.value()));
            FileObject moved = new FileObject(
                    sourceMetadata.object().id(),
                    movedPath,
                    sourceMetadata.object().kind(),
                    sourceMetadata.object().size(),
                    sourceMetadata.object().mediaType(),
                    now,
                    sourceMetadata.object().hidden());
            StoredFileRecord result = active(
                    scope,
                    moved,
                    version,
                    sourceMetadata.contentDigest(),
                    sourceRecord.blobBinding(),
                    now);
            targets.add(new PlannedTarget(
                    ChangeKind.MOVED,
                    sourceRecord,
                    sourceMetadata.object().id().value(),
                    sourceMetadata.object().path().value(),
                    movedPath.value(),
                    result));
        }
        return draft(
                scope,
                OperationKind.MOVE,
                ifMatch,
                ifNoneMatch,
                !overwrite,
                targets,
                treeFences(source, sourceTree, destination, destinationTree));
    }

    public Draft delete(
            MutationScope scope,
            FilePath path,
            FileVersion expectedVersion) {
        return delete(
                scope,
                path,
                expectedVersion,
                EntityTagCondition.notSupplied(),
                EntityTagCondition.notSupplied());
    }

    public Draft delete(
            MutationScope scope,
            FilePath path,
            FileVersion expectedVersion,
            EntityTagCondition ifMatch,
            EntityTagCondition ifNoneMatch) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(path, "path must not be null");
        if (path.root()) {
            throw failure(PATH_CONFLICT, "The Files root cannot be deleted.");
        }
        List<StoredFileRecord> target = tree(active(scope), path);
        if (target.isEmpty()) {
            throw failure(NOT_FOUND, "The requested file or folder was not found.");
        }
        StoredFileRecord root = byPath(target, path).orElseThrow();
        requireRequestConditions(root, ifMatch, ifNoneMatch);
        if (expectedVersion != null
                && expectedVersion.known()
                && !Objects.equals(expectedVersion.value(), root.metadata().version().value())) {
            throw failure(PRECONDITION_FAILED, "The expected Files version is stale.");
        }
        Instant now = now();
        return draft(scope, OperationKind.DELETE, ifMatch, ifNoneMatch, false, target.stream()
                .map(record -> tombstone(record, now))
                .toList(), sourceFences(path, target));
    }

    private Draft draft(
            MutationScope scope,
            OperationKind kind,
            EntityTagCondition ifMatch,
            EntityTagCondition ifNoneMatch,
            boolean destinationMustRemainAbsent,
            List<PlannedTarget> planned,
            List<FenceObservation> observedFences) {
        List<PlannedTarget> ordered = planned.stream()
                .sorted(Comparator
                        .comparing(PlannedTarget::operationPath, UTF8_UNSIGNED)
                        .thenComparing(target -> target.result().metadata().object().id().value(), UTF8_UNSIGNED))
                .toList();
        List<Target> targets = new ArrayList<>(ordered.size());
        for (int ordinal = 0; ordinal < ordered.size(); ordinal++) {
            targets.add(target(ordinal, ordered.get(ordinal)));
        }
        return new Draft(
                scope.operationRef(),
                scope.organizationRef(),
                scope.spaceRef(),
                scope.canonicalArgumentsDigest(),
                kind,
                scope.providerBindingRevision(),
                Objects.requireNonNull(ifMatch, "ifMatch must not be null"),
                Objects.requireNonNull(ifNoneMatch, "ifNoneMatch must not be null"),
                destinationMustRemainAbsent,
                targets,
                fences(observedFences));
    }

    private List<FenceObservation> sourceFences(FilePath source, List<StoredFileRecord> sourceTree) {
        String membershipDigest = membershipDigest(sourceTree);
        return sourceTree.stream()
                .map(record -> observation(
                        record.metadata().object().path().equals(source)
                                ? FenceRole.REQUEST_TARGET
                                : FenceRole.SOURCE_MEMBER,
                        record.metadata().object().path(),
                        record,
                        record.metadata().object().path().equals(source) ? membershipDigest : null))
                .toList();
    }

    private List<FenceObservation> treeFences(
            FilePath source,
            List<StoredFileRecord> sourceTree,
            FilePath destination,
            List<StoredFileRecord> destinationTree) {
        List<FenceObservation> observed = new ArrayList<>(sourceFences(source, sourceTree));
        StoredFileRecord destinationRoot = byPath(destinationTree, destination).orElse(null);
        String destinationMembership = destinationRoot == null ? null : membershipDigest(destinationTree);
        observed.add(observation(
                FenceRole.DESTINATION_TARGET,
                destination,
                destinationRoot,
                destinationMembership));
        destinationTree.stream()
                .filter(record -> !record.metadata().object().path().equals(destination))
                .map(record -> observation(
                        FenceRole.DESTINATION_MEMBER,
                        record.metadata().object().path(),
                        record,
                        null))
                .forEach(observed::add);
        return observed;
    }

    private FenceObservation observation(
            FenceRole role,
            FilePath path,
            StoredFileRecord record,
            String subtreeDigest) {
        return new FenceObservation(role, path.value(), record, subtreeDigest);
    }

    private List<Fence> fences(List<FenceObservation> observations) {
        List<FenceObservation> ordered = observations.stream()
                .sorted(Comparator.comparing(FenceObservation::role)
                        .thenComparing(FenceObservation::canonicalPath, UTF8_UNSIGNED)
                        .thenComparing(observation -> observation.record() == null
                                ? ""
                                : observation.record().metadata().object().id().value(), UTF8_UNSIGNED))
                .toList();
        List<Fence> fences = new ArrayList<>(ordered.size());
        for (int ordinal = 0; ordinal < ordered.size(); ordinal++) {
            FenceObservation observation = ordered.get(ordinal);
            StoredFileRecord record = observation.record();
            if (record == null) {
                fences.add(Fence.absent(ordinal, observation.role(), observation.canonicalPath()));
            } else {
                CanonicalFileRecord metadata = record.metadata();
                fences.add(Fence.present(
                        ordinal,
                        observation.role(),
                        observation.canonicalPath(),
                        metadata.object().id().value(),
                        metadata.object().kind(),
                        metadata.lifecycle(),
                        record.adapterRowVersion(),
                        FilesEtags.strong(metadata.object(), metadata.version()),
                        observation.subtreeDigest()));
            }
        }
        return List.copyOf(fences);
    }

    private String membershipDigest(List<StoredFileRecord> tree) {
        return FilesMutationPlan.subtreeMembershipDigest(tree.stream()
                .map(record -> new Membership(
                        record.metadata().object().path().value(),
                        record.metadata().object().id().value()))
                .toList());
    }

    private void requireRequestConditions(
            StoredFileRecord existing,
            EntityTagCondition ifMatch,
            EntityTagCondition ifNoneMatch) {
        EntityTagCondition requiredMatch = Objects.requireNonNull(ifMatch, "ifMatch must not be null");
        EntityTagCondition requiredNoneMatch = Objects.requireNonNull(ifNoneMatch, "ifNoneMatch must not be null");
        String currentEtag = existing == null
                ? null
                : FilesEtags.strong(existing.metadata().object(), existing.metadata().version());
        if (requiredMatch.supplied() && !requiredMatch.matches(currentEtag, true)) {
            throw failure(PRECONDITION_FAILED, "If-Match did not match the current Files state.");
        }
        if (requiredNoneMatch.supplied() && requiredNoneMatch.matches(currentEtag, false)) {
            throw failure(PRECONDITION_FAILED, "If-None-Match matched the current Files state.");
        }
    }

    private Target target(int ordinal, PlannedTarget planned) {
        StoredFileRecord source = planned.source();
        StoredFileRecord result = planned.result();
        CanonicalFileRecord resultMetadata = result.metadata();
        FileObject resultObject = resultMetadata.object();
        boolean sourceContent = source != null && source.metadata().object().kind() == Kind.FILE;
        if (sourceContent) {
            requireFileBinding(source);
        }
        return new Target(
                ordinal,
                planned.changeKind(),
                planned.sourceFileRef(),
                resultObject.id().value(),
                planned.sourcePath(),
                planned.targetPath(),
                resultObject.kind(),
                resultMetadata.lifecycle(),
                sourceContent ? source.blobBinding().opaqueReference() : null,
                sourceContent ? source.metadata().object().size() : null,
                sourceContent ? source.metadata().object().mediaType() : null,
                sourceContent ? source.metadata().contentDigest() : null,
                sourceContent ? source.metadata().version().value() : null,
                sourceContent ? FilesEtags.strong(source.metadata().object(), source.metadata().version()) : null,
                sourceContent ? canonical(source.metadata().object().modifiedAt()) : null,
                sourceContent ? source.metadata().object().hidden() : null,
                sourceContent ? canonical(source.metadata().observedAt()) : null,
                sourceContent ? source.metadata().lifecycle() : null,
                result.blobBinding() == null ? null : result.blobBinding().opaqueReference(),
                resultObject.size(),
                resultObject.mediaType(),
                resultMetadata.contentDigest(),
                resultObject.kind() == Kind.FILE ? resultMetadata.version().value() : null,
                resultObject.kind() == Kind.FILE ? FilesEtags.strong(resultObject, resultMetadata.version()) : null,
                canonical(resultObject.modifiedAt()),
                resultObject.hidden(),
                canonical(resultMetadata.observedAt()));
    }

    private PlannedTarget tombstone(StoredFileRecord source, Instant observedAt) {
        if (source.metadata().object().kind() == Kind.FILE) {
            requireFileBinding(source);
        }
        CanonicalFileRecord metadata = source.metadata();
        StoredFileRecord result = new StoredFileRecord(
                new CanonicalFileRecord(
                        metadata.organizationRef(),
                        metadata.spaceRef(),
                        normalizedObject(metadata.object()),
                        metadata.version(),
                        metadata.contentDigest(),
                        metadata.providerBindingRevision(),
                        TOMBSTONED,
                        observedAt),
                source.blobBinding());
        return new PlannedTarget(
                ChangeKind.TOMBSTONED,
                source,
                metadata.object().id().value(),
                metadata.object().path().value(),
                null,
                result);
    }

    private FileObject normalizedObject(FileObject object) {
        return new FileObject(
                object.id(),
                object.path(),
                object.kind(),
                object.size(),
                object.mediaType(),
                canonical(object.modifiedAt()),
                object.hidden());
    }

    private StoredFileRecord active(
            MutationScope scope,
            FileObject object,
            FileVersion version,
            String digest,
            BlobBinding binding,
            Instant observedAt) {
        return new StoredFileRecord(
                new CanonicalFileRecord(
                        scope.organizationRef(),
                        scope.spaceRef(),
                        object,
                        version,
                        digest,
                        scope.providerBindingRevision(),
                        ACTIVE,
                        observedAt),
                binding);
    }

    private List<StoredFileRecord> active(MutationScope scope) {
        Objects.requireNonNull(scope, "scope must not be null");
        return authority.activeFiles(scope.organizationRef(), scope.spaceRef());
    }

    private void ensureParent(List<StoredFileRecord> records, FilePath path) {
        FilePath parent = parent(path);
        if (parent.root()) {
            return;
        }
        StoredFileRecord parentRecord = byPath(records, parent)
                .orElseThrow(() -> failure(PARENT_MISSING, "The parent Files collection does not exist."));
        if (parentRecord.metadata().object().kind() != Kind.COLLECTION) {
            throw failure(PARENT_NOT_COLLECTION, "The parent Files path is not a collection.");
        }
    }

    private void requireTreeArguments(MutationScope scope, FilePath source, FilePath destination) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(destination, "destination must not be null");
        if (source.root() || destination.root()
                || source.equals(destination)
                || destination.value().startsWith(source.value() + "/")
                || source.value().startsWith(destination.value() + "/")) {
            throw failure(PATH_CONFLICT, "The Files tree source and destination overlap.");
        }
    }

    private List<StoredFileRecord> tree(List<StoredFileRecord> records, FilePath root) {
        String prefix = root.value() + "/";
        return records.stream()
                .filter(record -> record.metadata().object().path().equals(root)
                        || record.metadata().object().path().value().startsWith(prefix))
                .toList();
    }

    private Optional<StoredFileRecord> byPath(List<StoredFileRecord> records, FilePath path) {
        return records.stream()
                .filter(record -> record.metadata().object().path().equals(path))
                .findFirst();
    }

    private FilePath substitute(FilePath current, FilePath source, FilePath destination) {
        if (current.equals(source)) {
            return destination;
        }
        return new FilePath(destination.value() + current.value().substring(source.value().length()));
    }

    private FilePath parent(FilePath path) {
        if (path.root() || path.value().lastIndexOf('/') == 0) {
            return new FilePath("/");
        }
        return new FilePath(path.value().substring(0, path.value().lastIndexOf('/')));
    }

    private FileId canonicalId(MutationScope scope) {
        return new FileId("file:" + hash("create\u0000" + scope.operationRef()));
    }

    private FileVersion collectionVersion(FileId id, FilePath path) {
        return new FileVersion(FilesDigests.sha256(
                "collection\u0000" + id.value() + "\u0000" + path.value()));
    }

    private FileId copyId(MutationScope scope, FileId sourceId, FilePath copiedPath) {
        return new FileId("file:" + hash("copy\u0000"
                + scope.operationRef()
                + "\u0000" + sourceId.value()
                + "\u0000" + copiedPath.value()));
    }

    private String blobReference(FileId id, String digest) {
        if (digest == null) {
            throw failure(INVALID_BLOB_BINDING, "A file target is missing its content digest.");
        }
        return "v1/" + hash(id.value()) + "/" + digest.substring("sha256:".length());
    }

    private void requireFileBinding(StoredFileRecord record) {
        if (record.blobBinding() == null
                || record.metadata().contentDigest() == null
                || !record.metadata().version().known()) {
            throw failure(INVALID_BLOB_BINDING, "The Files metadata does not contain a complete blob binding.");
        }
    }

    private Instant now() {
        return canonical(Instant.now(clock));
    }

    private Instant canonical(Instant value) {
        return value == null ? null : value.truncatedTo(ChronoUnit.MICROS);
    }

    private String hash(String value) {
        return FilesDigests.sha256(value).substring("sha256:".length());
    }

    private FilesMutationPlanningException failure(
            FilesMutationPlanningException.Code code,
            String message) {
        return new FilesMutationPlanningException(code, message);
    }

    private static int compareUtf8(String left, String right) {
        byte[] a = left.getBytes(StandardCharsets.UTF_8);
        byte[] b = right.getBytes(StandardCharsets.UTF_8);
        int common = Math.min(a.length, b.length);
        for (int index = 0; index < common; index++) {
            int compared = Integer.compare(Byte.toUnsignedInt(a[index]), Byte.toUnsignedInt(b[index]));
            if (compared != 0) {
                return compared;
            }
        }
        return Integer.compare(a.length, b.length);
    }

    public record MutationScope(
            String operationRef,
            String organizationRef,
            String spaceRef,
            String canonicalArgumentsDigest,
            long providerBindingRevision) {

        public MutationScope {
            operationRef = required(operationRef, "operationRef");
            organizationRef = required(organizationRef, "organizationRef");
            spaceRef = required(spaceRef, "spaceRef");
            canonicalArgumentsDigest = required(canonicalArgumentsDigest, "canonicalArgumentsDigest");
            if (!canonicalArgumentsDigest.matches("sha256:[a-f0-9]{64}")) {
                throw new IllegalArgumentException("canonicalArgumentsDigest must be a sha256 digest");
            }
            if (providerBindingRevision < 1) {
                throw new IllegalArgumentException("providerBindingRevision must be positive");
            }
        }

        private static String required(String value, String field) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(field + " must not be blank");
            }
            return value.trim();
        }
    }

    private record PlannedTarget(
            ChangeKind changeKind,
            StoredFileRecord source,
            String sourceFileRef,
            String sourcePath,
            String targetPath,
            StoredFileRecord result) {

        private String operationPath() {
            return switch (changeKind) {
                case COPIED -> targetPath;
                case MOVED, TOMBSTONED -> sourcePath;
                case CREATED, CONTENT_UPDATED -> targetPath;
            };
        }
    }

    private record FenceObservation(
            FenceRole role,
            String canonicalPath,
            StoredFileRecord record,
            String subtreeDigest) {
    }
}

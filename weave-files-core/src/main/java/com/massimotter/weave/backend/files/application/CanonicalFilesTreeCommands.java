package com.massimotter.weave.backend.files.application;

import static com.massimotter.weave.backend.files.application.FilesTreeCommandException.Code.CONTENT_INTEGRITY_FAILED;
import static com.massimotter.weave.backend.files.application.FilesTreeCommandException.Code.INVALID_BLOB_REFERENCE;
import static com.massimotter.weave.backend.files.application.FilesTreeCommandException.Code.METADATA_CONFLICT;
import static com.massimotter.weave.backend.files.application.FilesTreeCommandException.Code.NOT_FOUND;
import static com.massimotter.weave.backend.files.application.FilesTreeCommandException.Code.PARENT_MISSING;
import static com.massimotter.weave.backend.files.application.FilesTreeCommandException.Code.PARENT_NOT_COLLECTION;
import static com.massimotter.weave.backend.files.application.FilesTreeCommandException.Code.PRECONDITION_FAILED;
import static com.massimotter.weave.backend.files.application.FilesTreeCommandException.Code.TREE_CONFLICT;
import static com.massimotter.weave.backend.files.domain.FilesAuthority.Lifecycle.ACTIVE;
import static com.massimotter.weave.backend.files.domain.FilesAuthority.Lifecycle.TOMBSTONED;

import com.massimotter.weave.backend.files.domain.FilesAuthority.CanonicalFileRecord;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileId;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileObject;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileVersion;
import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import com.massimotter.weave.backend.files.port.BlobStorePort;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobReference;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobScope;
import com.massimotter.weave.backend.files.port.FilesAuthorityRepository;
import com.massimotter.weave.backend.files.port.FilesAuthorityRepository.ConcurrentMutationException;
import com.massimotter.weave.backend.files.port.StoredFileRecord;
import com.massimotter.weave.backend.files.port.StoredFileRecord.BlobBinding;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Canonical provider-independent Files COPY, MOVE and DELETE tree use cases. */
public final class CanonicalFilesTreeCommands {

    private final FilesAuthorityRepository authority;
    private final BlobStorePort blobs;
    private final Clock clock;

    public CanonicalFilesTreeCommands(
            FilesAuthorityRepository authority,
            BlobStorePort blobs,
            Clock clock) {
        this.authority = Objects.requireNonNull(authority, "authority must not be null");
        this.blobs = Objects.requireNonNull(blobs, "blobs must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public FileObject copy(
            FilesCommandScope scope,
            FilePath source,
            FilePath destination,
            boolean overwrite) {
        requireArguments(scope, source, destination);
        requireDistinctTreePaths(source, destination);
        List<StoredFileRecord> records = active(scope);
        List<StoredFileRecord> sourceTree = tree(records, source);
        if (sourceTree.isEmpty()) {
            throw failure(NOT_FOUND, "The requested file or folder was not found.");
        }
        ensureParent(records, destination);
        List<StoredFileRecord> destinationTree = tree(records, destination);
        if (!destinationTree.isEmpty() && !overwrite) {
            throw failure(
                    PRECONDITION_FAILED,
                    "Overwrite is false and the destination already exists.");
        }

        Instant now = Instant.now(clock);
        List<StoredFileRecord> activations = new ArrayList<>();
        for (StoredFileRecord sourceRecord : sourceTree) {
            CanonicalFileRecord sourceMetadata = sourceRecord.metadata();
            FilePath copiedPath = substitute(sourceMetadata.object().path(), source, destination);
            FileId copiedId = canonicalId(scope, copiedPath);
            String digest = sourceMetadata.contentDigest();
            BlobBinding blobBinding = null;
            if (sourceMetadata.object().kind() == Kind.FILE) {
                BlobReference copiedReference = blobReference(copiedId, digest);
                try {
                    BoundedBlobTransfer.copy(
                            blobs,
                            blobScope(scope),
                            reference(sourceRecord),
                            copiedReference,
                            sourceMetadata.object().size(),
                            digest,
                            sourceMetadata.object().mediaType() == null
                                    ? "application/octet-stream"
                                    : sourceMetadata.object().mediaType());
                } catch (BoundedBlobTransfer.TransferException transferFailure) {
                    throw failure(
                            CONTENT_INTEGRITY_FAILED,
                            "The canonical Files content could not be copied safely.");
                }
                blobBinding = new BlobBinding(copiedReference.value());
            }
            FileObject copied = new FileObject(
                    copiedId,
                    copiedPath,
                    sourceMetadata.object().kind(),
                    sourceMetadata.object().size(),
                    sourceMetadata.object().mediaType(),
                    now,
                    sourceMetadata.object().hidden());
            activations.add(active(
                    scope,
                    copied,
                    sourceMetadata.version(),
                    digest,
                    blobBinding,
                    now));
        }

        try {
            if (sourceTree.size() == 1 && destinationTree.isEmpty()) {
                StoredFileRecord activation = activations.getFirst();
                try {
                    return authority.activate(activation).metadata().object();
                } catch (ConcurrentMutationException concurrentMutation) {
                    StoredFileRecord concurrent = authority
                            .findByPath(
                                    scope.organizationRef(),
                                    scope.spaceRef(),
                                    destination)
                            .orElse(null);
                    if (equivalent(concurrent, activation)) {
                        return concurrent.metadata().object();
                    }
                    throw concurrentMutation;
                }
            }
            authority.replaceTree(
                    destination,
                    tombstones(destinationTree, now),
                    activations);
        } catch (ConcurrentMutationException concurrentMutation) {
            cleanupBlobs(scope, activations);
            throw failure(
                    METADATA_CONFLICT,
                    "The canonical Files metadata changed concurrently.");
        }
        cleanupBlobs(scope, destinationTree);
        return root(activations, destination).metadata().object();
    }

    public FileObject move(
            FilesCommandScope scope,
            FilePath source,
            FilePath destination,
            boolean overwrite) {
        requireArguments(scope, source, destination);
        requireDistinctTreePaths(source, destination);
        List<StoredFileRecord> records = active(scope);
        List<StoredFileRecord> sourceTree = tree(records, source);
        if (sourceTree.isEmpty()) {
            throw failure(NOT_FOUND, "The requested file or folder was not found.");
        }
        ensureParent(records, destination);
        List<StoredFileRecord> destinationTree = tree(records, destination);
        if (!destinationTree.isEmpty() && !overwrite) {
            throw failure(
                    PRECONDITION_FAILED,
                    "Overwrite is false and the destination already exists.");
        }

        Instant now = Instant.now(clock);
        if (sourceTree.size() == 1 && destinationTree.isEmpty()) {
            StoredFileRecord sourceRecord = sourceTree.getFirst();
            try {
                return authority.moveNode(
                        scope.organizationRef(),
                        scope.spaceRef(),
                        sourceRecord.metadata().object().id(),
                        source,
                        destination,
                        now).metadata().object();
            } catch (ConcurrentMutationException concurrentMutation) {
                throw failure(
                        METADATA_CONFLICT,
                        "The canonical Files metadata changed concurrently.");
            }
        }

        List<StoredFileRecord> moved = sourceTree.stream()
                .map(record -> moveRecord(scope, record, source, destination, now))
                .toList();
        try {
            authority.replaceTree(
                    destination,
                    tombstones(destinationTree, now),
                    moved);
        } catch (ConcurrentMutationException concurrentMutation) {
            throw failure(
                    METADATA_CONFLICT,
                    "The canonical Files metadata changed concurrently.");
        }
        cleanupBlobs(scope, destinationTree);
        return root(moved, destination).metadata().object();
    }

    public void delete(
            FilesCommandScope scope,
            FilePath path,
            FileVersion expectedVersion) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(path, "path must not be null");
        if (path.root()) {
            throw failure(TREE_CONFLICT, "The Files root cannot be deleted.");
        }
        List<StoredFileRecord> target = tree(active(scope), path);
        if (target.isEmpty()) {
            throw failure(NOT_FOUND, "The requested file or folder was not found.");
        }
        CanonicalFileRecord root = root(target, path).metadata();
        if (expectedVersion != null
                && expectedVersion.known()
                && !Objects.equals(expectedVersion.value(), root.version().value())) {
            throw failure(
                    PRECONDITION_FAILED,
                    "The expected Files version is stale.");
        }
        try {
            authority.replaceTree(
                    path,
                    tombstones(target, Instant.now(clock)),
                    List.of());
        } catch (ConcurrentMutationException concurrentMutation) {
            throw failure(
                    METADATA_CONFLICT,
                    "The canonical Files metadata changed concurrently.");
        }
        cleanupBlobs(scope, target);
    }

    private StoredFileRecord moveRecord(
            FilesCommandScope scope,
            StoredFileRecord record,
            FilePath source,
            FilePath destination,
            Instant now) {
        CanonicalFileRecord metadata = record.metadata();
        FilePath movedPath = substitute(metadata.object().path(), source, destination);
        FileObject object = new FileObject(
                metadata.object().id(),
                movedPath,
                metadata.object().kind(),
                metadata.object().size(),
                metadata.object().mediaType(),
                now,
                metadata.object().hidden());
        String version = FilesDigests.sha256(
                String.valueOf(metadata.version().value()) + "\u0000" + movedPath.value());
        return active(
                scope,
                object,
                new FileVersion(version),
                metadata.contentDigest(),
                record.blobBinding(),
                now);
    }

    private BlobReference reference(StoredFileRecord record) {
        if (record.blobBinding() == null) {
            throw failure(
                    INVALID_BLOB_REFERENCE,
                    "The Files metadata does not reference content.");
        }
        try {
            return new BlobReference(record.blobBinding().opaqueReference());
        } catch (IllegalArgumentException invalidReference) {
            throw failure(
                    INVALID_BLOB_REFERENCE,
                    "The Files metadata contains an invalid content reference.");
        }
    }

    private void cleanupBlobs(
            FilesCommandScope scope,
            List<StoredFileRecord> records) {
        Set<BlobBinding> retained = active(scope).stream()
                .map(StoredFileRecord::blobBinding)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
        for (StoredFileRecord record : records) {
            if (record.metadata().object().kind() == Kind.FILE
                    && record.blobBinding() != null
                    && !retained.contains(record.blobBinding())) {
                try {
                    blobs.delete(blobScope(scope), reference(record));
                } catch (RuntimeException ignored) {
                    // Metadata is authoritative; bounded reconciliation owns incomplete cleanup.
                }
            }
        }
    }

    private boolean equivalent(
            StoredFileRecord current,
            StoredFileRecord requested) {
        CanonicalFileRecord currentMetadata = current == null ? null : current.metadata();
        CanonicalFileRecord requestedMetadata = requested.metadata();
        return current != null
                && currentMetadata.lifecycle() == ACTIVE
                && currentMetadata.object().id().equals(requestedMetadata.object().id())
                && currentMetadata.object().kind() == requestedMetadata.object().kind()
                && currentMetadata.object().size() == requestedMetadata.object().size()
                && Objects.equals(
                        currentMetadata.object().mediaType(),
                        requestedMetadata.object().mediaType())
                && Objects.equals(currentMetadata.contentDigest(), requestedMetadata.contentDigest())
                && Objects.equals(current.blobBinding(), requested.blobBinding());
    }

    private List<StoredFileRecord> active(FilesCommandScope scope) {
        return authority.activeFiles(scope.organizationRef(), scope.spaceRef());
    }

    private void ensureParent(
            List<StoredFileRecord> records,
            FilePath path) {
        FilePath parent = parent(path);
        if (parent.root()) {
            return;
        }
        CanonicalFileRecord parentRecord = byPath(records, parent)
                .map(StoredFileRecord::metadata)
                .orElseThrow(() -> failure(
                        PARENT_MISSING,
                        "The parent Files collection does not exist."));
        if (parentRecord.object().kind() != Kind.COLLECTION) {
            throw failure(
                    PARENT_NOT_COLLECTION,
                    "The parent Files path is not a collection.");
        }
    }

    private List<StoredFileRecord> tree(
            List<StoredFileRecord> records,
            FilePath root) {
        String prefix = root.value() + "/";
        return records.stream()
                .filter(record -> record.metadata().object().path().equals(root)
                        || record.metadata().object().path().value().startsWith(prefix))
                .sorted(Comparator
                        .comparingInt((StoredFileRecord record) ->
                                record.metadata().object().path().value().length())
                        .thenComparing(record -> record.metadata().object().path().value()))
                .toList();
    }

    private StoredFileRecord root(
            List<StoredFileRecord> records,
            FilePath path) {
        return byPath(records, path).orElseThrow(() -> failure(
                METADATA_CONFLICT,
                "The canonical Files tree result is incomplete."));
    }

    private Optional<StoredFileRecord> byPath(
            List<StoredFileRecord> records,
            FilePath path) {
        return records.stream()
                .filter(record -> record.metadata().object().path().equals(path))
                .findFirst();
    }

    private List<StoredFileRecord> tombstones(
            List<StoredFileRecord> records,
            Instant now) {
        return records.stream()
                .map(record -> new StoredFileRecord(
                        new CanonicalFileRecord(
                                record.metadata().organizationRef(),
                                record.metadata().spaceRef(),
                                record.metadata().object(),
                                record.metadata().version(),
                                record.metadata().contentDigest(),
                                record.metadata().providerBindingRevision(),
                                TOMBSTONED,
                                later(now, record.metadata().observedAt())),
                        record.blobBinding()))
                .toList();
    }

    private StoredFileRecord active(
            FilesCommandScope scope,
            FileObject object,
            FileVersion version,
            String digest,
            BlobBinding blobBinding,
            Instant now) {
        return new StoredFileRecord(
                new CanonicalFileRecord(
                        scope.organizationRef(),
                        scope.spaceRef(),
                        object,
                        version,
                        digest,
                        scope.providerBindingRevision(),
                        ACTIVE,
                        now),
                blobBinding);
    }

    private FileId canonicalId(
            FilesCommandScope scope,
            FilePath initialPath) {
        String seed = scope.organizationRef()
                + "\u0000"
                + scope.spaceRef()
                + "\u0000"
                + initialPath.value();
        return new FileId("file:" + hash(seed));
    }

    private BlobReference blobReference(
            FileId id,
            String digest) {
        if (digest == null || !digest.matches("sha256:[a-f0-9]{64}")) {
            throw failure(
                    CONTENT_INTEGRITY_FAILED,
                    "The source file does not have a valid content digest.");
        }
        return new BlobReference(
                "v1/" + hash(id.value()) + "/" + digest.substring("sha256:".length()));
    }

    private BlobScope blobScope(FilesCommandScope scope) {
        return new BlobScope(scope.organizationRef(), scope.spaceRef());
    }

    private FilePath parent(FilePath path) {
        if (path.root() || path.value().lastIndexOf('/') == 0) {
            return new FilePath("/");
        }
        return new FilePath(path.value().substring(0, path.value().lastIndexOf('/')));
    }

    private FilePath substitute(
            FilePath path,
            FilePath source,
            FilePath destination) {
        return new FilePath(
                destination.value() + path.value().substring(source.value().length()));
    }

    private void requireArguments(
            FilesCommandScope scope,
            FilePath source,
            FilePath destination) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(destination, "destination must not be null");
    }

    private void requireDistinctTreePaths(
            FilePath source,
            FilePath destination) {
        boolean overlap = source.root()
                || destination.root()
                || source.equals(destination)
                || destination.value().startsWith(source.value() + "/")
                || source.value().startsWith(destination.value() + "/");
        if (overlap) {
            throw failure(
                    TREE_CONFLICT,
                    "The Files tree operation has an invalid destination.");
        }
    }

    private Instant later(Instant candidate, Instant floor) {
        return candidate.isBefore(floor) ? floor : candidate;
    }

    private String hash(String value) {
        return FilesDigests.sha256(value).substring("sha256:".length());
    }

    private FilesTreeCommandException failure(
            FilesTreeCommandException.Code code,
            String message) {
        return new FilesTreeCommandException(code, message);
    }
}

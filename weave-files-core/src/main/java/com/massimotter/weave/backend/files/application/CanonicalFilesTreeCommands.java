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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
        List<CanonicalFileRecord> records = active(scope);
        List<CanonicalFileRecord> sourceTree = tree(records, source);
        if (sourceTree.isEmpty()) {
            throw failure(NOT_FOUND, "The requested file or folder was not found.");
        }
        ensureParent(records, destination);
        List<CanonicalFileRecord> destinationTree = tree(records, destination);
        if (!destinationTree.isEmpty() && !overwrite) {
            throw failure(
                    PRECONDITION_FAILED,
                    "Overwrite is false and the destination already exists.");
        }

        Instant now = Instant.now(clock);
        List<CanonicalFileRecord> activations = new ArrayList<>();
        for (CanonicalFileRecord sourceRecord : sourceTree) {
            FilePath copiedPath = substitute(sourceRecord.object().path(), source, destination);
            FileId copiedId = canonicalId(scope, copiedPath);
            String digest = sourceRecord.contentDigest();
            String storageReference = null;
            if (sourceRecord.object().kind() == Kind.FILE) {
                byte[] content = readVerified(scope, sourceRecord);
                BlobReference copiedReference = blobReference(copiedId, digest);
                blobs.putStream(
                        blobScope(scope),
                        copiedReference,
                        new ByteArrayInputStream(content),
                        content.length,
                        digest);
                storageReference = copiedReference.value();
            }
            FileObject copied = new FileObject(
                    copiedId,
                    copiedPath,
                    sourceRecord.object().kind(),
                    sourceRecord.object().size(),
                    sourceRecord.object().mediaType(),
                    now,
                    sourceRecord.object().hidden());
            activations.add(active(
                    scope,
                    copied,
                    sourceRecord.version(),
                    digest,
                    storageReference,
                    now));
        }

        try {
            if (sourceTree.size() == 1 && destinationTree.isEmpty()) {
                CanonicalFileRecord activation = activations.getFirst();
                try {
                    return authority.activate(activation).object();
                } catch (ConcurrentMutationException concurrentMutation) {
                    CanonicalFileRecord concurrent = authority
                            .findByPath(
                                    scope.organizationRef(),
                                    scope.spaceRef(),
                                    destination)
                            .orElse(null);
                    if (equivalent(concurrent, activation)) {
                        return concurrent.object();
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
        return root(activations, destination).object();
    }

    public FileObject move(
            FilesCommandScope scope,
            FilePath source,
            FilePath destination,
            boolean overwrite) {
        requireArguments(scope, source, destination);
        requireDistinctTreePaths(source, destination);
        List<CanonicalFileRecord> records = active(scope);
        List<CanonicalFileRecord> sourceTree = tree(records, source);
        if (sourceTree.isEmpty()) {
            throw failure(NOT_FOUND, "The requested file or folder was not found.");
        }
        ensureParent(records, destination);
        List<CanonicalFileRecord> destinationTree = tree(records, destination);
        if (!destinationTree.isEmpty() && !overwrite) {
            throw failure(
                    PRECONDITION_FAILED,
                    "Overwrite is false and the destination already exists.");
        }

        Instant now = Instant.now(clock);
        if (sourceTree.size() == 1 && destinationTree.isEmpty()) {
            CanonicalFileRecord sourceRecord = sourceTree.getFirst();
            try {
                return authority.moveNode(
                        scope.organizationRef(),
                        scope.spaceRef(),
                        sourceRecord.object().id(),
                        source,
                        destination,
                        now).object();
            } catch (ConcurrentMutationException concurrentMutation) {
                throw failure(
                        METADATA_CONFLICT,
                        "The canonical Files metadata changed concurrently.");
            }
        }

        List<CanonicalFileRecord> moved = sourceTree.stream()
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
        return root(moved, destination).object();
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
        List<CanonicalFileRecord> target = tree(active(scope), path);
        if (target.isEmpty()) {
            throw failure(NOT_FOUND, "The requested file or folder was not found.");
        }
        CanonicalFileRecord root = root(target, path);
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

    private CanonicalFileRecord moveRecord(
            FilesCommandScope scope,
            CanonicalFileRecord record,
            FilePath source,
            FilePath destination,
            Instant now) {
        FilePath movedPath = substitute(record.object().path(), source, destination);
        FileObject object = new FileObject(
                record.object().id(),
                movedPath,
                record.object().kind(),
                record.object().size(),
                record.object().mediaType(),
                now,
                record.object().hidden());
        String version = FilesDigests.sha256(
                String.valueOf(record.version().value()) + "\u0000" + movedPath.value());
        return active(
                scope,
                object,
                new FileVersion(version),
                record.contentDigest(),
                record.storageReference(),
                now);
    }

    private byte[] readVerified(
            FilesCommandScope scope,
            CanonicalFileRecord record) {
        ByteArrayOutputStream target = new ByteArrayOutputStream(
                Math.toIntExact(Math.min(record.object().size(), Integer.MAX_VALUE)));
        blobs.readStream(blobScope(scope), reference(record), target);
        byte[] content = target.toByteArray();
        String actualDigest = FilesDigests.sha256(content);
        if (record.object().size() != content.length
                || record.contentDigest() == null
                || !MessageDigest.isEqual(
                        record.contentDigest().getBytes(StandardCharsets.US_ASCII),
                        actualDigest.getBytes(StandardCharsets.US_ASCII))) {
            throw failure(
                    CONTENT_INTEGRITY_FAILED,
                    "The canonical Files metadata and blob content do not match.");
        }
        return content;
    }

    private BlobReference reference(CanonicalFileRecord record) {
        if (record.storageReference() == null) {
            throw failure(
                    INVALID_BLOB_REFERENCE,
                    "The Files metadata does not reference content.");
        }
        try {
            return new BlobReference(record.storageReference());
        } catch (IllegalArgumentException invalidReference) {
            throw failure(
                    INVALID_BLOB_REFERENCE,
                    "The Files metadata contains an invalid content reference.");
        }
    }

    private void cleanupBlobs(
            FilesCommandScope scope,
            List<CanonicalFileRecord> records) {
        Set<String> retained = active(scope).stream()
                .map(CanonicalFileRecord::storageReference)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
        for (CanonicalFileRecord record : records) {
            if (record.object().kind() == Kind.FILE
                    && record.storageReference() != null
                    && !retained.contains(record.storageReference())) {
                try {
                    blobs.delete(blobScope(scope), reference(record));
                } catch (RuntimeException ignored) {
                    // Metadata is authoritative; bounded reconciliation owns incomplete cleanup.
                }
            }
        }
    }

    private boolean equivalent(
            CanonicalFileRecord current,
            CanonicalFileRecord requested) {
        return current != null
                && current.lifecycle() == ACTIVE
                && current.object().id().equals(requested.object().id())
                && current.object().kind() == requested.object().kind()
                && current.object().size() == requested.object().size()
                && Objects.equals(current.object().mediaType(), requested.object().mediaType())
                && Objects.equals(current.contentDigest(), requested.contentDigest())
                && Objects.equals(current.storageReference(), requested.storageReference());
    }

    private List<CanonicalFileRecord> active(FilesCommandScope scope) {
        return authority.activeFiles(scope.organizationRef(), scope.spaceRef());
    }

    private void ensureParent(
            List<CanonicalFileRecord> records,
            FilePath path) {
        FilePath parent = parent(path);
        if (parent.root()) {
            return;
        }
        CanonicalFileRecord parentRecord = byPath(records, parent)
                .orElseThrow(() -> failure(
                        PARENT_MISSING,
                        "The parent Files collection does not exist."));
        if (parentRecord.object().kind() != Kind.COLLECTION) {
            throw failure(
                    PARENT_NOT_COLLECTION,
                    "The parent Files path is not a collection.");
        }
    }

    private List<CanonicalFileRecord> tree(
            List<CanonicalFileRecord> records,
            FilePath root) {
        String prefix = root.value() + "/";
        return records.stream()
                .filter(record -> record.object().path().equals(root)
                        || record.object().path().value().startsWith(prefix))
                .sorted(Comparator
                        .comparingInt((CanonicalFileRecord record) ->
                                record.object().path().value().length())
                        .thenComparing(record -> record.object().path().value()))
                .toList();
    }

    private CanonicalFileRecord root(
            List<CanonicalFileRecord> records,
            FilePath path) {
        return byPath(records, path).orElseThrow(() -> failure(
                METADATA_CONFLICT,
                "The canonical Files tree result is incomplete."));
    }

    private Optional<CanonicalFileRecord> byPath(
            List<CanonicalFileRecord> records,
            FilePath path) {
        return records.stream()
                .filter(record -> record.object().path().equals(path))
                .findFirst();
    }

    private List<CanonicalFileRecord> tombstones(
            List<CanonicalFileRecord> records,
            Instant now) {
        return records.stream()
                .map(record -> new CanonicalFileRecord(
                        record.organizationRef(),
                        record.spaceRef(),
                        record.object(),
                        record.version(),
                        record.contentDigest(),
                        record.storageReference(),
                        record.providerBindingRevision(),
                        TOMBSTONED,
                        later(now, record.observedAt())))
                .toList();
    }

    private CanonicalFileRecord active(
            FilesCommandScope scope,
            FileObject object,
            FileVersion version,
            String digest,
            String storageReference,
            Instant now) {
        return new CanonicalFileRecord(
                scope.organizationRef(),
                scope.spaceRef(),
                object,
                version,
                digest,
                storageReference,
                scope.providerBindingRevision(),
                ACTIVE,
                now);
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

package com.massimotter.weave.backend.files.application;

import static com.massimotter.weave.backend.files.application.FilesCommandException.Code.METADATA_CONFLICT;
import static com.massimotter.weave.backend.files.application.FilesCommandException.Code.PARENT_MISSING;
import static com.massimotter.weave.backend.files.application.FilesCommandException.Code.PARENT_NOT_COLLECTION;
import static com.massimotter.weave.backend.files.application.FilesCommandException.Code.PATH_CONFLICT;
import static com.massimotter.weave.backend.files.domain.FilesAuthority.Lifecycle.ACTIVE;

import com.massimotter.weave.backend.files.domain.FilesAuthority.CanonicalFileRecord;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileId;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileObject;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileVersion;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileWrite;
import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import com.massimotter.weave.backend.files.port.BlobStorePort;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobReference;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobScope;
import com.massimotter.weave.backend.files.port.FilesAuthorityRepository;
import com.massimotter.weave.backend.files.port.FilesAuthorityRepository.ConcurrentMutationException;
import com.massimotter.weave.backend.files.port.StoredFileRecord;
import com.massimotter.weave.backend.files.port.StoredFileRecord.BlobBinding;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Canonical provider-independent Files collection-creation and content-write use cases.
 */
public final class CanonicalFilesCommands {

    private final FilesAuthorityRepository authority;
    private final BlobStorePort blobs;
    private final Clock clock;

    public CanonicalFilesCommands(
            FilesAuthorityRepository authority,
            BlobStorePort blobs,
            Clock clock) {
        this.authority = Objects.requireNonNull(authority, "authority must not be null");
        this.blobs = Objects.requireNonNull(blobs, "blobs must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public FileObject write(FilesCommandScope scope, FileWrite write) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(write, "write must not be null");
        ensureParent(scope, write.path());

        byte[] content = write.bytes();
        String digest = FilesDigests.sha256(content);
        StoredFileRecord existing = authority
                .findByPath(scope.organizationRef(), scope.spaceRef(), write.path())
                .orElse(null);
        if (existing != null && existing.metadata().object().kind() != Kind.FILE) {
            throw failure(
                    PATH_CONFLICT,
                    "A collection already exists at the requested Files path.");
        }

        FileId id = existing == null
                ? canonicalId(scope, write.path())
                : existing.metadata().object().id();
        BlobReference reference = blobReference(id, digest);
        blobs.putStream(
                blobScope(scope),
                reference,
                new ByteArrayInputStream(content),
                content.length,
                digest);

        Instant now = Instant.now(clock);
        FileObject object = new FileObject(
                id,
                write.path(),
                Kind.FILE,
                content.length,
                write.mediaType(),
                now,
                false);
        StoredFileRecord activation = active(
                scope,
                object,
                new FileVersion(digest),
                digest,
                new BlobBinding(reference.value()),
                now);

        try {
            return authority.activate(activation).metadata().object();
        } catch (ConcurrentMutationException concurrentMutation) {
            StoredFileRecord concurrent = authority
                    .findByPath(scope.organizationRef(), scope.spaceRef(), write.path())
                    .orElseThrow(() -> failure(
                            METADATA_CONFLICT,
                            "The canonical Files metadata changed concurrently."));
            if (concurrent.metadata().object().kind() == Kind.FILE
                    && Objects.equals(concurrent.metadata().contentDigest(), digest)
                    && Objects.equals(concurrent.blobBinding(), activation.blobBinding())) {
                return concurrent.metadata().object();
            }
            throw failure(
                    METADATA_CONFLICT,
                    "The canonical Files metadata changed concurrently.");
        }
    }

    public FileObject createCollection(FilesCommandScope scope, FilePath path) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(path, "path must not be null");
        ensureParent(scope, path);
        if (authority.findByPath(scope.organizationRef(), scope.spaceRef(), path).isPresent()) {
            throw failure(
                    PATH_CONFLICT,
                    "A Files object already exists at the requested path.");
        }

        Instant now = Instant.now(clock);
        FileObject object = new FileObject(
                canonicalId(scope, path),
                path,
                Kind.COLLECTION,
                0,
                null,
                now,
                false);
        String version = FilesDigests.sha256("collection\u0000" + path.value());
        try {
            return authority.activate(active(
                    scope,
                    object,
                    new FileVersion(version),
                    null,
                    null,
                    now)).metadata().object();
        } catch (ConcurrentMutationException concurrentMutation) {
            throw failure(
                    METADATA_CONFLICT,
                    "The canonical Files metadata changed concurrently.");
        }
    }

    private void ensureParent(FilesCommandScope scope, FilePath path) {
        FilePath parent = parent(path);
        if (parent.root()) {
            return;
        }
        CanonicalFileRecord parentRecord = authority
                .findByPath(scope.organizationRef(), scope.spaceRef(), parent)
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

    private FilePath parent(FilePath path) {
        if (path.root() || path.value().lastIndexOf('/') == 0) {
            return new FilePath("/");
        }
        return new FilePath(path.value().substring(0, path.value().lastIndexOf('/')));
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

    private FileId canonicalId(FilesCommandScope scope, FilePath initialPath) {
        String seed = scope.organizationRef()
                + "\u0000"
                + scope.spaceRef()
                + "\u0000"
                + initialPath.value();
        return new FileId("file:" + hash(seed));
    }

    private BlobReference blobReference(FileId id, String digest) {
        return new BlobReference(
                "v1/" + hash(id.value()) + "/" + digest.substring("sha256:".length()));
    }

    private BlobScope blobScope(FilesCommandScope scope) {
        return new BlobScope(scope.organizationRef(), scope.spaceRef());
    }

    private String hash(String value) {
        return FilesDigests.sha256(value).substring("sha256:".length());
    }

    private FilesCommandException failure(
            FilesCommandException.Code code,
            String message) {
        return new FilesCommandException(code, message);
    }
}

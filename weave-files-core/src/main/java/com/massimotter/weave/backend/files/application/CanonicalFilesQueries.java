package com.massimotter.weave.backend.files.application;

import static com.massimotter.weave.backend.files.application.FilesApplicationException.Code.CONTENT_INTEGRITY_FAILED;
import static com.massimotter.weave.backend.files.application.FilesApplicationException.Code.INVALID_BLOB_REFERENCE;
import static com.massimotter.weave.backend.files.application.FilesApplicationException.Code.NOT_A_COLLECTION;
import static com.massimotter.weave.backend.files.application.FilesApplicationException.Code.NOT_A_FILE;
import static com.massimotter.weave.backend.files.application.FilesApplicationException.Code.NOT_FOUND;

import com.massimotter.weave.backend.files.domain.FilesAuthority.CanonicalFileRecord;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileContent;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileId;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileListing;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileObject;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileQuota;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileVersion;
import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import com.massimotter.weave.backend.files.domain.FilesDomain.VersionedFile;
import com.massimotter.weave.backend.files.domain.FilesDomain.VersionedListing;
import com.massimotter.weave.backend.files.port.BlobStorePort;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobReference;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobScope;
import com.massimotter.weave.backend.files.port.FilesAuthorityRepository;
import com.massimotter.weave.backend.files.port.FilesBlobProtectionPort;
import com.massimotter.weave.backend.files.port.StoredFileRecord;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Canonical provider-independent Files list/find/read and integrity use cases. */
public final class CanonicalFilesQueries {

    private final FilesAuthorityRepository authority;
    private final BlobStorePort blobs;
    private final FilesBlobProtectionPort blobProtection;
    private final int reconciliationLimit;

    public CanonicalFilesQueries(
            FilesAuthorityRepository authority,
            BlobStorePort blobs,
            int reconciliationLimit) {
        this(authority, blobs, FilesBlobProtectionPort.none(), reconciliationLimit);
    }

    public CanonicalFilesQueries(
            FilesAuthorityRepository authority,
            BlobStorePort blobs,
            FilesBlobProtectionPort blobProtection,
            int reconciliationLimit) {
        this.authority = Objects.requireNonNull(authority, "authority must not be null");
        this.blobs = Objects.requireNonNull(blobs, "blobs must not be null");
        this.blobProtection = Objects.requireNonNull(blobProtection, "blobProtection must not be null");
        this.reconciliationLimit = Math.max(1, reconciliationLimit);
    }

    public VersionedListing list(FilesScope scope, FilePath path) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(path, "path must not be null");
        List<StoredFileRecord> records = active(scope);
        if (!path.root()) {
            CanonicalFileRecord requested = byPath(records, path)
                    .map(StoredFileRecord::metadata)
                    .orElseThrow(() -> failure(NOT_FOUND, "The requested file or folder was not found."));
            if (requested.object().kind() != Kind.COLLECTION) {
                throw failure(NOT_A_COLLECTION, "The requested Files path is not a collection.");
            }
        }
        List<StoredFileRecord> children = records.stream()
                .filter(record -> parent(record.metadata().object().path()).equals(path))
                .sorted(Comparator.comparing(record -> record.metadata().object().path().value()))
                .toList();
        Map<FilePath, FileVersion> versions = new LinkedHashMap<>();
        children.forEach(child -> versions.put(
                child.metadata().object().path(),
                child.metadata().version()));
        long used = records.stream()
                .filter(record -> record.metadata().object().kind() == Kind.FILE)
                .mapToLong(record -> record.metadata().object().size())
                .sum();
        return new VersionedListing(
                new FileListing(
                        path,
                        children.stream().map(record -> record.metadata().object()).toList(),
                        new FileQuota(null, used)),
                listingVersion(path, children),
                versions);
    }

    public Optional<VersionedFile> find(FilesScope scope, FilePath path) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(path, "path must not be null");
        if (path.root()) {
            List<StoredFileRecord> children = active(scope).stream()
                    .filter(record -> parent(record.metadata().object().path()).equals(path))
                    .toList();
            return Optional.of(new VersionedFile(rootObject(), listingVersion(path, children)));
        }
        return authority.findByPath(scope.organizationRef(), scope.spaceRef(), path)
                .map(StoredFileRecord::metadata)
                .map(record -> new VersionedFile(record.object(), record.version()));
    }

    public FileContent read(FilesScope scope, FileId id) {
        StoredFileRecord record = file(scope, id);
        ByteArrayOutputStream target = new ByteArrayOutputStream(
                Math.toIntExact(Math.min(record.metadata().object().size(), Integer.MAX_VALUE)));
        readVerified(scope, record, target);
        return new FileContent(record.metadata().object(), target.toByteArray());
    }

    public void readTo(FilesScope scope, FileId id, OutputStream target) {
        Objects.requireNonNull(target, "target must not be null");
        readVerified(scope, file(scope, id), target);
    }

    public ReconciliationReport reconcile(FilesScope scope) {
        Objects.requireNonNull(scope, "scope must not be null");
        BlobScope blobScope = blobScope(scope);
        List<StoredFileRecord> active = active(scope);
        Set<BlobReference> referenced = new LinkedHashSet<>();
        for (StoredFileRecord stored : authority.storedFiles(scope.organizationRef(), scope.spaceRef())) {
            if (stored.metadata().object().kind() == Kind.FILE && stored.blobBinding() != null) {
                try {
                    referenced.add(new BlobReference(stored.blobBinding().opaqueReference()));
                } catch (IllegalArgumentException ignored) {
                    // Active invalid bindings are counted by the verified pass below; invalid tombstones protect nothing.
                }
            }
        }
        referenced.addAll(blobProtection.protectedBindings(scope));
        int inconsistent = 0;
        for (StoredFileRecord record : active) {
            if (record.metadata().object().kind() != Kind.FILE) {
                continue;
            }
            try {
                BlobReference reference = reference(record);
                referenced.add(reference);
                readVerified(scope, record, OutputStream.nullOutputStream());
            } catch (RuntimeException exception) {
                inconsistent++;
            }
        }
        List<BlobReference> inventory = blobs.inventory(blobScope, reconciliationLimit);
        int deleted = 0;
        for (BlobReference candidate : inventory) {
            if (!referenced.contains(candidate)) {
                blobs.delete(blobScope, candidate);
                deleted++;
            }
        }
        return new ReconciliationReport(active.size(), inventory.size(), deleted, inconsistent);
    }

    private StoredFileRecord file(FilesScope scope, FileId id) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(id, "id must not be null");
        StoredFileRecord record = authority.findById(scope.organizationRef(), scope.spaceRef(), id)
                .orElseThrow(() -> failure(NOT_FOUND, "The requested file or folder was not found."));
        if (record.metadata().object().kind() != Kind.FILE) {
            throw failure(NOT_A_FILE, "The requested Files object has no file content.");
        }
        return record;
    }

    private void readVerified(
            FilesScope scope,
            StoredFileRecord record,
            OutputStream target) {
        CanonicalFileRecord metadata = record.metadata();
        MessageDigest digest = FilesDigests.newSha256();
        long[] count = {0};
        OutputStream verifying = new DigestOutputStream(new OutputStream() {
            @Override
            public void write(int value) throws IOException {
                target.write(value);
                count[0]++;
            }

            @Override
            public void write(byte[] value, int offset, int length) throws IOException {
                target.write(value, offset, length);
                count[0] += length;
            }
        }, digest);
        blobs.readStream(blobScope(scope), reference(record), verifying);
        String actualDigest = "sha256:" + java.util.HexFormat.of().formatHex(digest.digest());
        if (count[0] != metadata.object().size()
                || metadata.contentDigest() == null
                || !MessageDigest.isEqual(
                metadata.contentDigest().getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                actualDigest.getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
            throw failure(
                    CONTENT_INTEGRITY_FAILED,
                    "The native Files metadata and blob content do not match.");
        }
    }

    private BlobReference reference(StoredFileRecord record) {
        if (record.blobBinding() == null) {
            throw failure(INVALID_BLOB_REFERENCE, "The Files metadata does not reference content.");
        }
        try {
            return new BlobReference(record.blobBinding().opaqueReference());
        } catch (IllegalArgumentException exception) {
            throw failure(INVALID_BLOB_REFERENCE, "The Files metadata contains an invalid content reference.");
        }
    }

    private List<StoredFileRecord> active(FilesScope scope) {
        return authority.activeFiles(scope.organizationRef(), scope.spaceRef());
    }

    private BlobScope blobScope(FilesScope scope) {
        return new BlobScope(scope.organizationRef(), scope.spaceRef());
    }

    private Optional<StoredFileRecord> byPath(
            List<StoredFileRecord> records,
            FilePath path) {
        return records.stream()
                .filter(record -> record.metadata().object().path().equals(path))
                .findFirst();
    }

    private FilePath parent(FilePath path) {
        if (path.root() || path.value().lastIndexOf('/') == 0) {
            return new FilePath("/");
        }
        return new FilePath(path.value().substring(0, path.value().lastIndexOf('/')));
    }

    private FileVersion listingVersion(
            FilePath path,
            List<StoredFileRecord> children) {
        StringBuilder canonical = new StringBuilder(path.value());
        children.stream()
                .sorted(Comparator.comparing(record -> record.metadata().object().path().value()))
                .forEach(record -> canonical
                        .append('\n').append(record.metadata().object().id().value())
                        .append('\n').append(record.metadata().version().value()));
        return new FileVersion(FilesDigests.sha256(canonical.toString()));
    }

    private FileObject rootObject() {
        return new FileObject(
                new FileId("files:root"),
                new FilePath("/"),
                Kind.COLLECTION,
                0,
                null,
                null,
                false);
    }

    private FilesApplicationException failure(
            FilesApplicationException.Code code,
            String message) {
        return new FilesApplicationException(code, message);
    }

    public record ReconciliationReport(
            int activeMetadataRecords,
            int inventoriedBlobs,
            int orphanBlobsDeleted,
            int inconsistentMetadataRecords) {
    }
}

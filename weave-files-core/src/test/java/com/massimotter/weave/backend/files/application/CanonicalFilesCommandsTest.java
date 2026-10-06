package com.massimotter.weave.backend.files.application;

import static com.massimotter.weave.backend.files.application.FilesCommandException.Code.METADATA_CONFLICT;
import static com.massimotter.weave.backend.files.application.FilesCommandException.Code.PARENT_MISSING;
import static com.massimotter.weave.backend.files.application.FilesCommandException.Code.PARENT_NOT_COLLECTION;
import static com.massimotter.weave.backend.files.application.FilesCommandException.Code.PATH_CONFLICT;
import static com.massimotter.weave.backend.files.application.FilesCommandException.Code.VERSION_CHANGED;
import static com.massimotter.weave.backend.files.domain.FilesAuthority.Lifecycle.ACTIVE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.massimotter.weave.backend.files.domain.FilesAuthority.FileLockRecord;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileId;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileObject;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileVersion;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileWrite;
import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import com.massimotter.weave.backend.files.port.BlobStorePort;
import com.massimotter.weave.backend.files.port.FilesAuthorityRepository;
import com.massimotter.weave.backend.files.port.FilesAuthorityRepository.ConcurrentMutationException;
import com.massimotter.weave.backend.files.port.StoredFileRecord;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CanonicalFilesCommandsTest {

    private static final FilesCommandScope SCOPE =
            new FilesCommandScope("org-1", "space-1", 3);
    private static final Instant NOW = Instant.parse("2026-08-19T00:00:00Z");

    private InMemoryAuthority authority;
    private InMemoryBlobs blobs;
    private CanonicalFilesCommands commands;

    @BeforeEach
    void setUp() {
        authority = new InMemoryAuthority();
        blobs = new InMemoryBlobs();
        commands = new CanonicalFilesCommands(
                authority,
                blobs,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsCollectionAndReplacesContentWithoutChangingCanonicalIdentity() {
        FileObject collection = commands.createCollection(SCOPE, new FilePath("/docs"));
        byte[] firstContent = "first".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] replacementContent = "replacement".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        FileObject first = commands.write(
                SCOPE,
                new FileWrite(new FilePath("/docs/readme.txt"), firstContent, "text/plain"));
        StoredFileRecord firstRecord = authority
                .findByPath(SCOPE.organizationRef(), SCOPE.spaceRef(), first.path())
                .orElseThrow();

        FileObject replacement = commands.write(
                SCOPE,
                new FileWrite(
                        new FilePath("/docs/readme.txt"),
                        replacementContent,
                        "text/markdown"));
        StoredFileRecord replacementRecord = authority
                .findByPath(SCOPE.organizationRef(), SCOPE.spaceRef(), replacement.path())
                .orElseThrow();

        assertEquals(Kind.COLLECTION, collection.kind());
        assertEquals(first.id(), replacement.id());
        assertNotEquals(
                firstRecord.metadata().contentDigest(),
                replacementRecord.metadata().contentDigest());
        assertEquals("text/markdown", replacement.mediaType());
        assertArrayEquals(
                replacementContent,
                blobs.values.get(new BlobStorePort.BlobReference(
                        replacementRecord.blobBinding().opaqueReference())));
        assertEquals(2, authority.activeFiles(SCOPE.organizationRef(), SCOPE.spaceRef()).size());
        assertEquals(2, blobs.values.size());
    }

    @Test
    void enforcesParentAndPathInvariantsBeforePublishingMetadata() {
        FilesCommandException missingParent = assertThrows(
                FilesCommandException.class,
                () -> commands.createCollection(SCOPE, new FilePath("/missing/child")));
        assertEquals(PARENT_MISSING, missingParent.code());

        commands.write(
                SCOPE,
                new FileWrite(
                        new FilePath("/plain.txt"),
                        new byte[] {1},
                        "text/plain"));
        FilesCommandException nonCollectionParent = assertThrows(
                FilesCommandException.class,
                () -> commands.write(
                        SCOPE,
                        new FileWrite(
                                new FilePath("/plain.txt/child"),
                                new byte[] {2},
                                "application/octet-stream")));
        assertEquals(PARENT_NOT_COLLECTION, nonCollectionParent.code());

        commands.createCollection(SCOPE, new FilePath("/docs"));
        FilesCommandException occupiedPath = assertThrows(
                FilesCommandException.class,
                () -> commands.write(
                        SCOPE,
                        new FileWrite(
                                new FilePath("/docs"),
                                new byte[] {3},
                                "application/octet-stream")));
        assertEquals(PATH_CONFLICT, occupiedPath.code());
    }

    @Test
    void identicalConcurrentActivationIsAnIdempotentSuccess() {
        authority.nextConflict = ConflictMode.STORE_REQUESTED_AND_THROW;

        byte[] content = "idempotent".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        FileObject stored = commands.write(
                SCOPE,
                new FileWrite(new FilePath("/idempotent.txt"), content, "text/plain"));

        StoredFileRecord record = authority
                .findByPath(SCOPE.organizationRef(), SCOPE.spaceRef(), stored.path())
                .orElseThrow();
        assertEquals(stored.id(), record.metadata().object().id());
        assertArrayEquals(
                content,
                blobs.values.get(new BlobStorePort.BlobReference(
                        record.blobBinding().opaqueReference())));
    }

    @Test
    void divergentConcurrentActivationFailsClosed() {
        commands.write(
                SCOPE,
                new FileWrite(
                        new FilePath("/race.txt"),
                        "existing".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        "text/plain"));
        authority.nextConflict = ConflictMode.THROW_ONLY;

        FilesCommandException conflict = assertThrows(
                FilesCommandException.class,
                () -> commands.write(
                        SCOPE,
                        new FileWrite(
                                new FilePath("/race.txt"),
                                "replacement".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                                "text/plain")));

        assertEquals(METADATA_CONFLICT, conflict.code());
        assertTrue(blobs.values.size() >= 2);
    }

    @Test
    void conditionalWriteKeepsIdentityAndRejectsStaleVersionOrReplacement() {
        FilePath path = new FilePath("/conditional.txt");
        FileObject original = commands.write(SCOPE, new FileWrite(path, "first".getBytes(), "text/plain"));
        FileVersion firstVersion = authority.findByPath(SCOPE.organizationRef(), SCOPE.spaceRef(), path)
                .orElseThrow().metadata().version();

        FileObject updated = commands.writeIfIdAndVersion(SCOPE, original.id(),
                new FileWrite(path, "second".getBytes(), "text/plain"), firstVersion);
        StoredFileRecord current = authority.findByPath(SCOPE.organizationRef(), SCOPE.spaceRef(), path)
                .orElseThrow();
        assertEquals(original.id(), updated.id());
        assertNotEquals(firstVersion, current.metadata().version());

        FilesCommandException stale = assertThrows(FilesCommandException.class,
                () -> commands.writeIfIdAndVersion(SCOPE, original.id(),
                        new FileWrite(path, "stale".getBytes(), "text/plain"), firstVersion));
        assertEquals(VERSION_CHANGED, stale.code());
        assertEquals(current, authority.findByPath(SCOPE.organizationRef(), SCOPE.spaceRef(), path)
                .orElseThrow());

        FileObject replacement = new FileObject(new FileId("file:replacement"), path, Kind.FILE,
                current.metadata().object().size(), "text/plain", NOW, false);
        authority.records.clear();
        authority.save(new StoredFileRecord(new com.massimotter.weave.backend.files.domain.FilesAuthority.CanonicalFileRecord(
                SCOPE.organizationRef(), SCOPE.spaceRef(), replacement, current.metadata().version(),
                current.metadata().contentDigest(), SCOPE.providerBindingRevision(), ACTIVE, NOW),
                current.blobBinding()));
        FilesCommandException replaced = assertThrows(FilesCommandException.class,
                () -> commands.writeIfIdAndVersion(SCOPE, original.id(),
                        new FileWrite(path, "third".getBytes(), "text/plain"), current.metadata().version()));
        assertEquals(VERSION_CHANGED, replaced.code());
        assertEquals(replacement.id(), authority.findByPath(SCOPE.organizationRef(), SCOPE.spaceRef(), path)
                .orElseThrow().metadata().object().id());
    }

    @Test
    void conditionalWriteFailsClosedWhenMetadataChangesAtActivation() {
        FilePath path = new FilePath("/raced.txt");
        FileObject original = commands.write(SCOPE, new FileWrite(path, "first".getBytes(), "text/plain"));
        StoredFileRecord before = authority.findByPath(SCOPE.organizationRef(), SCOPE.spaceRef(), path)
                .orElseThrow();
        authority.nextConflict = ConflictMode.THROW_ONLY;

        FilesCommandException raced = assertThrows(FilesCommandException.class,
                () -> commands.writeIfIdAndVersion(SCOPE, original.id(),
                        new FileWrite(path, "second".getBytes(), "text/plain"), before.metadata().version()));

        assertEquals(VERSION_CHANGED, raced.code());
        assertEquals(before, authority.findByPath(SCOPE.organizationRef(), SCOPE.spaceRef(), path)
                .orElseThrow());
    }

    private enum ConflictMode {
        NONE,
        STORE_REQUESTED_AND_THROW,
        THROW_ONLY
    }

    private static final class InMemoryAuthority implements FilesAuthorityRepository {
        private final List<StoredFileRecord> records = new ArrayList<>();
        private ConflictMode nextConflict = ConflictMode.NONE;

        @Override
        public StoredFileRecord save(StoredFileRecord record) {
            ConflictMode conflict = nextConflict;
            nextConflict = ConflictMode.NONE;
            if (conflict == ConflictMode.STORE_REQUESTED_AND_THROW) {
                replaceRecord(record);
                throw new ConcurrentMutationException(record.metadata().object().path());
            }
            if (conflict == ConflictMode.THROW_ONLY) {
                throw new ConcurrentMutationException(record.metadata().object().path());
            }
            replaceRecord(record);
            return record;
        }

        @Override
        public synchronized StoredFileRecord activateIfIdAndVersion(
                StoredFileRecord replacement, FileId expectedId, FileVersion expectedVersion) {
            StoredFileRecord current = findByPath(replacement.metadata().organizationRef(),
                    replacement.metadata().spaceRef(), replacement.metadata().object().path()).orElse(null);
            if (current == null || !current.metadata().object().id().equals(expectedId)
                    || !replacement.metadata().object().id().equals(expectedId)
                    || !current.metadata().version().equals(expectedVersion)
                    || current.metadata().providerBindingRevision()
                            != replacement.metadata().providerBindingRevision()) {
                throw new ConcurrentMutationException(replacement.metadata().object().path());
            }
            return save(replacement);
        }

        private void replaceRecord(StoredFileRecord record) {
            records.removeIf(existing -> existing.metadata().organizationRef()
                    .equals(record.metadata().organizationRef())
                    && existing.metadata().spaceRef().equals(record.metadata().spaceRef())
                    && existing.metadata().object().id().equals(record.metadata().object().id()));
            records.add(record);
        }

        @Override
        public Optional<StoredFileRecord> findByPath(
                String organizationRef,
                String spaceRef,
                FilePath path) {
            return records.stream()
                    .filter(record -> matches(record, organizationRef, spaceRef)
                            && record.metadata().object().path().equals(path))
                    .findFirst();
        }

        @Override
        public Optional<StoredFileRecord> findById(
                String organizationRef,
                String spaceRef,
                FileId id) {
            return records.stream()
                    .filter(record -> matches(record, organizationRef, spaceRef)
                            && record.metadata().object().id().equals(id))
                    .findFirst();
        }

        @Override
        public List<StoredFileRecord> activeFiles(
                String organizationRef,
                String spaceRef) {
            return records.stream()
                    .filter(record -> matches(record, organizationRef, spaceRef))
                    .toList();
        }

        private boolean matches(
                StoredFileRecord record,
                String organizationRef,
                String spaceRef) {
            return record.metadata().organizationRef().equals(organizationRef)
                    && record.metadata().spaceRef().equals(spaceRef)
                    && record.metadata().lifecycle() == ACTIVE;
        }

        @Override
        public List<StoredFileRecord> replace(
                List<StoredFileRecord> tombstones,
                List<StoredFileRecord> activations) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StoredFileRecord move(
                String organizationRef,
                String spaceRef,
                FileId id,
                FilePath expectedPath,
                FilePath destination,
                Instant movedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FileLockRecord acquireLock(FileLockRecord requested, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<FileLockRecord> activeLock(
                String organizationRef,
                String spaceRef,
                FilePath path,
                Instant now) {
            return Optional.empty();
        }

        @Override
        public List<FileLockRecord> activeLocks(
                String organizationRef,
                String spaceRef,
                Instant now) {
            return List.of();
        }

        @Override
        public void releaseLock(
                String organizationRef,
                String spaceRef,
                FilePath path,
                String tokenDigest,
                String ownerRef,
                Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void moveLock(
                String organizationRef,
                String spaceRef,
                FilePath source,
                FilePath destination,
                String tokenDigest,
                String ownerRef,
                Instant now) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class InMemoryBlobs implements BlobStorePort {
        private final Map<BlobReference, byte[]> values = new LinkedHashMap<>();

        @Override
        public boolean configured() {
            return true;
        }

        @Override
        public BlobReceipt putStream(
                BlobScope scope,
                BlobReference reference,
                InputStream source,
                long expectedSize,
                String expectedDigest) {
            try {
                byte[] bytes = source.readAllBytes();
                if (bytes.length != expectedSize
                        || !FilesDigests.sha256(bytes).equals(expectedDigest)) {
                    throw new IllegalArgumentException("blob input did not match its declaration");
                }
                values.put(reference, bytes);
                return new BlobReceipt(reference, expectedDigest, bytes.length);
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public void readStream(
                BlobScope scope,
                BlobReference reference,
                OutputStream target) {
            byte[] bytes = values.get(reference);
            if (bytes == null) {
                throw new IllegalStateException("blob missing");
            }
            try {
                target.write(bytes);
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public void delete(BlobScope scope, BlobReference reference) {
            values.remove(reference);
        }

        @Override
        public List<BlobReference> inventory(BlobScope scope, int limit) {
            return values.keySet().stream().limit(limit).toList();
        }
    }
}

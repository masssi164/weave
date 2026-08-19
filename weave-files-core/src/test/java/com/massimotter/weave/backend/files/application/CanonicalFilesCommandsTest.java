package com.massimotter.weave.backend.files.application;

import static com.massimotter.weave.backend.files.application.FilesCommandException.Code.METADATA_CONFLICT;
import static com.massimotter.weave.backend.files.application.FilesCommandException.Code.PARENT_MISSING;
import static com.massimotter.weave.backend.files.application.FilesCommandException.Code.PARENT_NOT_COLLECTION;
import static com.massimotter.weave.backend.files.application.FilesCommandException.Code.PATH_CONFLICT;
import static com.massimotter.weave.backend.files.domain.FilesAuthority.Lifecycle.ACTIVE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.massimotter.weave.backend.files.domain.FilesAuthority.CanonicalFileRecord;
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
        CanonicalFileRecord firstRecord = authority
                .findByPath(SCOPE.organizationRef(), SCOPE.spaceRef(), first.path())
                .orElseThrow();

        FileObject replacement = commands.write(
                SCOPE,
                new FileWrite(
                        new FilePath("/docs/readme.txt"),
                        replacementContent,
                        "text/markdown"));
        CanonicalFileRecord replacementRecord = authority
                .findByPath(SCOPE.organizationRef(), SCOPE.spaceRef(), replacement.path())
                .orElseThrow();

        assertEquals(Kind.COLLECTION, collection.kind());
        assertEquals(first.id(), replacement.id());
        assertNotEquals(firstRecord.contentDigest(), replacementRecord.contentDigest());
        assertEquals("text/markdown", replacement.mediaType());
        assertArrayEquals(
                replacementContent,
                blobs.values.get(new BlobStorePort.BlobReference(
                        replacementRecord.storageReference())));
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

        CanonicalFileRecord record = authority
                .findByPath(SCOPE.organizationRef(), SCOPE.spaceRef(), stored.path())
                .orElseThrow();
        assertEquals(stored.id(), record.object().id());
        assertArrayEquals(
                content,
                blobs.values.get(new BlobStorePort.BlobReference(record.storageReference())));
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

    private enum ConflictMode {
        NONE,
        STORE_REQUESTED_AND_THROW,
        THROW_ONLY
    }

    private static final class InMemoryAuthority implements FilesAuthorityRepository {
        private final List<CanonicalFileRecord> records = new ArrayList<>();
        private ConflictMode nextConflict = ConflictMode.NONE;

        @Override
        public CanonicalFileRecord save(CanonicalFileRecord record) {
            ConflictMode conflict = nextConflict;
            nextConflict = ConflictMode.NONE;
            if (conflict == ConflictMode.STORE_REQUESTED_AND_THROW) {
                replaceRecord(record);
                throw new ConcurrentMutationException(record.object().path());
            }
            if (conflict == ConflictMode.THROW_ONLY) {
                throw new ConcurrentMutationException(record.object().path());
            }
            replaceRecord(record);
            return record;
        }

        private void replaceRecord(CanonicalFileRecord record) {
            records.removeIf(existing -> existing.organizationRef().equals(record.organizationRef())
                    && existing.spaceRef().equals(record.spaceRef())
                    && existing.object().id().equals(record.object().id()));
            records.add(record);
        }

        @Override
        public Optional<CanonicalFileRecord> findByPath(
                String organizationRef,
                String spaceRef,
                FilePath path) {
            return records.stream()
                    .filter(record -> matches(record, organizationRef, spaceRef)
                            && record.object().path().equals(path))
                    .findFirst();
        }

        @Override
        public Optional<CanonicalFileRecord> findById(
                String organizationRef,
                String spaceRef,
                FileId id) {
            return records.stream()
                    .filter(record -> matches(record, organizationRef, spaceRef)
                            && record.object().id().equals(id))
                    .findFirst();
        }

        @Override
        public List<CanonicalFileRecord> activeFiles(
                String organizationRef,
                String spaceRef) {
            return records.stream()
                    .filter(record -> matches(record, organizationRef, spaceRef))
                    .toList();
        }

        private boolean matches(
                CanonicalFileRecord record,
                String organizationRef,
                String spaceRef) {
            return record.organizationRef().equals(organizationRef)
                    && record.spaceRef().equals(spaceRef)
                    && record.lifecycle() == ACTIVE;
        }

        @Override
        public List<CanonicalFileRecord> replace(
                List<CanonicalFileRecord> tombstones,
                List<CanonicalFileRecord> activations) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CanonicalFileRecord move(
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

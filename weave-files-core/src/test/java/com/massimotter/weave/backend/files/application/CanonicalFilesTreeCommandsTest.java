package com.massimotter.weave.backend.files.application;

import static com.massimotter.weave.backend.files.application.FilesTreeCommandException.Code.CONTENT_INTEGRITY_FAILED;
import static com.massimotter.weave.backend.files.application.FilesTreeCommandException.Code.METADATA_CONFLICT;
import static com.massimotter.weave.backend.files.application.FilesTreeCommandException.Code.PRECONDITION_FAILED;
import static com.massimotter.weave.backend.files.application.FilesTreeCommandException.Code.TREE_CONFLICT;
import static com.massimotter.weave.backend.files.domain.FilesAuthority.Lifecycle.ACTIVE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.massimotter.weave.backend.files.domain.FilesAuthority.CanonicalFileRecord;
import com.massimotter.weave.backend.files.domain.FilesAuthority.FileLockRecord;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileId;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileObject;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileVersion;
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

class CanonicalFilesTreeCommandsTest {

    private static final FilesCommandScope SCOPE =
            new FilesCommandScope("org-tree", "space-main", 5);
    private static final Instant NOW = Instant.parse("2026-08-19T03:00:00Z");

    private InMemoryAuthority authority;
    private InMemoryBlobs blobs;
    private CanonicalFilesTreeCommands commands;

    @BeforeEach
    void setUp() {
        authority = new InMemoryAuthority();
        blobs = new InMemoryBlobs();
        commands = new CanonicalFilesTreeCommands(
                authority,
                blobs,
                Clock.fixed(NOW, ZoneOffset.UTC));
        addCollection("collection-docs", "/docs", "docs-v1");
        addCollection("collection-nested", "/docs/nested", "nested-v1");
        addCollection("collection-archive", "/archive", "archive-v1");
        addFile("file-a", "/docs/a.txt", "alpha");
        addFile("file-b", "/docs/nested/b.txt", "beta");
    }

    @Test
    void copyCreatesIndependentCanonicalTreeWithVerifiedContent() {
        CanonicalFileRecord sourceA = recordAt("/docs/a.txt");
        byte[] sourceBytes = bytes(sourceA);

        FileObject copiedRoot = commands.copy(
                SCOPE,
                new FilePath("/docs"),
                new FilePath("/archive/docs"),
                false);

        CanonicalFileRecord copiedA = recordAt("/archive/docs/a.txt");
        CanonicalFileRecord copiedB = recordAt("/archive/docs/nested/b.txt");
        assertEquals(new FilePath("/archive/docs"), copiedRoot.path());
        assertNotEquals(sourceA.object().id(), copiedA.object().id());
        assertArrayEquals(sourceBytes, bytes(copiedA));
        assertArrayEquals(
                "beta".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                bytes(copiedB));
        assertTrue(authority.findByPath(
                SCOPE.organizationRef(),
                SCOPE.spaceRef(),
                new FilePath("/docs/a.txt")).isPresent());
        assertEquals(
                9,
                authority.activeFiles(SCOPE.organizationRef(), SCOPE.spaceRef()).size());
    }

    @Test
    void moveKeepsCanonicalIdsAndCleansOverwrittenDestinationContent() {
        addCollection("old-destination", "/archive/docs", "old-destination-v1");
        addFile("old-file", "/archive/docs/old.txt", "obsolete");
        CanonicalFileRecord sourceA = recordAt("/docs/a.txt");
        CanonicalFileRecord sourceB = recordAt("/docs/nested/b.txt");
        BlobStorePort.BlobReference obsoleteReference =
                new BlobStorePort.BlobReference(
                        recordAt("/archive/docs/old.txt").storageReference());

        FileObject movedRoot = commands.move(
                SCOPE,
                new FilePath("/docs"),
                new FilePath("/archive/docs"),
                true);

        assertEquals(new FilePath("/archive/docs"), movedRoot.path());
        assertEquals(
                sourceA.object().id(),
                recordAt("/archive/docs/a.txt").object().id());
        assertEquals(
                sourceB.object().id(),
                recordAt("/archive/docs/nested/b.txt").object().id());
        assertFalse(authority.findByPath(
                SCOPE.organizationRef(),
                SCOPE.spaceRef(),
                new FilePath("/docs/a.txt")).isPresent());
        assertFalse(blobs.values.containsKey(obsoleteReference));
        assertArrayEquals(
                "alpha".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                bytes(recordAt("/archive/docs/a.txt")));
    }

    @Test
    void deleteTombstonesTreeAndCleansUnreferencedContent() {
        FileVersion expected = recordAt("/docs").version();

        commands.delete(SCOPE, new FilePath("/docs"), expected);

        assertFalse(authority.activeFiles(SCOPE.organizationRef(), SCOPE.spaceRef()).stream()
                .anyMatch(record -> record.object().path().value().startsWith("/docs")));
        assertTrue(authority.findByPath(
                SCOPE.organizationRef(),
                SCOPE.spaceRef(),
                new FilePath("/archive")).isPresent());
        assertTrue(blobs.values.isEmpty());
    }

    @Test
    void rejectsUnsafeTreesStaleVersionsAndCorruptSourceContent() {
        FilesTreeCommandException overlap = assertThrows(
                FilesTreeCommandException.class,
                () -> commands.copy(
                        SCOPE,
                        new FilePath("/docs"),
                        new FilePath("/docs/nested/copy"),
                        false));
        assertEquals(TREE_CONFLICT, overlap.code());

        addCollection("occupied-destination", "/archive/docs", "occupied-v1");
        FilesTreeCommandException occupied = assertThrows(
                FilesTreeCommandException.class,
                () -> commands.copy(
                        SCOPE,
                        new FilePath("/docs"),
                        new FilePath("/archive/docs"),
                        false));
        assertEquals(PRECONDITION_FAILED, occupied.code());

        FilesTreeCommandException stale = assertThrows(
                FilesTreeCommandException.class,
                () -> commands.delete(
                        SCOPE,
                        new FilePath("/docs"),
                        new FileVersion("stale")));
        assertEquals(PRECONDITION_FAILED, stale.code());

        CanonicalFileRecord source = recordAt("/docs/a.txt");
        blobs.values.put(
                new BlobStorePort.BlobReference(source.storageReference()),
                "corrupt".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        FilesTreeCommandException corrupt = assertThrows(
                FilesTreeCommandException.class,
                () -> commands.copy(
                        SCOPE,
                        source.object().path(),
                        new FilePath("/archive/a.txt"),
                        false));
        assertEquals(CONTENT_INTEGRITY_FAILED, corrupt.code());
        assertFalse(authority.findByPath(
                SCOPE.organizationRef(),
                SCOPE.spaceRef(),
                new FilePath("/archive/a.txt")).isPresent());
    }

    @Test
    void failedTreeActivationCleansPublishedCopiesForReconciliation() {
        authority.failNextReplace = true;

        FilesTreeCommandException conflict = assertThrows(
                FilesTreeCommandException.class,
                () -> commands.copy(
                        SCOPE,
                        new FilePath("/docs"),
                        new FilePath("/archive/docs"),
                        false));

        assertEquals(METADATA_CONFLICT, conflict.code());
        assertEquals(2, blobs.values.size());
        assertFalse(authority.findByPath(
                SCOPE.organizationRef(),
                SCOPE.spaceRef(),
                new FilePath("/archive/docs")).isPresent());
    }

    private void addCollection(String id, String path, String version) {
        authority.save(new CanonicalFileRecord(
                SCOPE.organizationRef(),
                SCOPE.spaceRef(),
                new FileObject(
                        new FileId(id),
                        new FilePath(path),
                        Kind.COLLECTION,
                        0,
                        null,
                        NOW,
                        false),
                new FileVersion(version),
                null,
                null,
                SCOPE.providerBindingRevision(),
                ACTIVE,
                NOW));
    }

    private void addFile(String id, String path, String value) {
        byte[] content = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String digest = FilesDigests.sha256(content);
        String referenceValue = "v1/source/" + id.replace(':', '-');
        CanonicalFileRecord record = new CanonicalFileRecord(
                SCOPE.organizationRef(),
                SCOPE.spaceRef(),
                new FileObject(
                        new FileId(id),
                        new FilePath(path),
                        Kind.FILE,
                        content.length,
                        "text/plain",
                        NOW,
                        false),
                new FileVersion(digest),
                digest,
                referenceValue,
                SCOPE.providerBindingRevision(),
                ACTIVE,
                NOW);
        authority.save(record);
        blobs.values.put(new BlobStorePort.BlobReference(referenceValue), content);
    }

    private CanonicalFileRecord recordAt(String path) {
        return authority.findByPath(
                SCOPE.organizationRef(),
                SCOPE.spaceRef(),
                new FilePath(path)).orElseThrow();
    }

    private byte[] bytes(CanonicalFileRecord record) {
        return blobs.values.get(new BlobStorePort.BlobReference(record.storageReference()));
    }

    private static final class InMemoryAuthority implements FilesAuthorityRepository {
        private final List<CanonicalFileRecord> records = new ArrayList<>();
        private boolean failNextReplace;

        @Override
        public CanonicalFileRecord save(CanonicalFileRecord record) {
            replaceRecord(record);
            return record;
        }

        @Override
        public Optional<CanonicalFileRecord> findByPath(
                String organizationRef,
                String spaceRef,
                FilePath path) {
            return records.stream()
                    .filter(record -> activeMatch(record, organizationRef, spaceRef)
                            && record.object().path().equals(path))
                    .findFirst();
        }

        @Override
        public Optional<CanonicalFileRecord> findById(
                String organizationRef,
                String spaceRef,
                FileId id) {
            return records.stream()
                    .filter(record -> activeMatch(record, organizationRef, spaceRef)
                            && record.object().id().equals(id))
                    .findFirst();
        }

        @Override
        public List<CanonicalFileRecord> activeFiles(
                String organizationRef,
                String spaceRef) {
            return records.stream()
                    .filter(record -> activeMatch(record, organizationRef, spaceRef))
                    .toList();
        }

        @Override
        public List<CanonicalFileRecord> replace(
                List<CanonicalFileRecord> tombstones,
                List<CanonicalFileRecord> activations) {
            if (failNextReplace) {
                failNextReplace = false;
                FilePath path = activations.isEmpty()
                        ? tombstones.getFirst().object().path()
                        : activations.getFirst().object().path();
                throw new ConcurrentMutationException(path);
            }
            tombstones.forEach(this::replaceRecord);
            activations.forEach(this::replaceRecord);
            return List.copyOf(activations);
        }

        @Override
        public CanonicalFileRecord move(
                String organizationRef,
                String spaceRef,
                FileId id,
                FilePath expectedPath,
                FilePath destination,
                Instant movedAt) {
            CanonicalFileRecord current = findById(organizationRef, spaceRef, id)
                    .filter(record -> record.object().path().equals(expectedPath))
                    .orElseThrow(() -> new ConcurrentMutationException(expectedPath));
            FileObject moved = new FileObject(
                    current.object().id(),
                    destination,
                    current.object().kind(),
                    current.object().size(),
                    current.object().mediaType(),
                    movedAt,
                    current.object().hidden());
            CanonicalFileRecord updated = new CanonicalFileRecord(
                    current.organizationRef(),
                    current.spaceRef(),
                    moved,
                    current.version(),
                    current.contentDigest(),
                    current.storageReference(),
                    current.providerBindingRevision(),
                    ACTIVE,
                    movedAt);
            replaceRecord(updated);
            return updated;
        }

        private void replaceRecord(CanonicalFileRecord record) {
            records.removeIf(existing -> existing.organizationRef().equals(record.organizationRef())
                    && existing.spaceRef().equals(record.spaceRef())
                    && existing.object().id().equals(record.object().id()));
            records.add(record);
        }

        private boolean activeMatch(
                CanonicalFileRecord record,
                String organizationRef,
                String spaceRef) {
            return record.organizationRef().equals(organizationRef)
                    && record.spaceRef().equals(spaceRef)
                    && record.lifecycle() == ACTIVE;
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
                byte[] content = source.readAllBytes();
                if (content.length != expectedSize
                        || !FilesDigests.sha256(content).equals(expectedDigest)) {
                    throw new IllegalArgumentException("blob declaration mismatch");
                }
                values.put(reference, content);
                return new BlobReceipt(reference, expectedDigest, content.length);
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public void readStream(
                BlobScope scope,
                BlobReference reference,
                OutputStream target) {
            byte[] content = values.get(reference);
            if (content == null) {
                throw new IllegalStateException("blob missing");
            }
            try {
                target.write(content);
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

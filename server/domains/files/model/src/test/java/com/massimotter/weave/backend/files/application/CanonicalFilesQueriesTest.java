package com.massimotter.weave.backend.files.application;

import static com.massimotter.weave.backend.files.application.FilesApplicationException.Code.CONTENT_INTEGRITY_FAILED;
import static com.massimotter.weave.backend.files.application.FilesApplicationException.Code.INVALID_BLOB_REFERENCE;
import static com.massimotter.weave.backend.files.application.FilesApplicationException.Code.NOT_FOUND;
import static com.massimotter.weave.backend.files.domain.FilesAuthority.Lifecycle.ACTIVE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.massimotter.weave.backend.files.port.StoredFileRecord;
import com.massimotter.weave.backend.files.port.StoredFileRecord.BlobBinding;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CanonicalFilesQueriesTest {

    private static final FilesScope SCOPE = new FilesScope("org-1", "space-1");
    private static final Instant NOW = Instant.parse("2026-08-18T20:00:00Z");

    private final InMemoryAuthority authority = new InMemoryAuthority();
    private final InMemoryBlobs blobs = new InMemoryBlobs();
    private final CanonicalFilesQueries queries = new CanonicalFilesQueries(authority, blobs, 100);
    private StoredFileRecord file;
    private byte[] content;

    @BeforeEach
    void setUp() {
        content = "canonical-files".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        StoredFileRecord collection = record(
                "collection-docs",
                "/docs",
                Kind.COLLECTION,
                0,
                null,
                null,
                "collection-v1");
        String digest = FilesDigests.sha256(content);
        file = record(
                "file-readme",
                "/docs/readme.txt",
                Kind.FILE,
                content.length,
                digest,
                "v1/readme/blob",
                digest);
        authority.records.add(collection);
        authority.records.add(file);
        blobs.values.put(new BlobStorePort.BlobReference("v1/readme/blob"), content.clone());
    }

    @Test
    void listsFindsAndReadsCanonicalStateWithoutProviderTypes() {
        var root = queries.list(SCOPE, new FilePath("/"));
        assertEquals(1, root.listing().children().size());
        assertEquals(new FilePath("/docs"), root.listing().children().getFirst().path());

        var docs = queries.list(SCOPE, new FilePath("/docs"));
        assertEquals(1, docs.listing().children().size());
        assertEquals(file.metadata().object().id(), docs.listing().children().getFirst().id());
        assertTrue(queries.find(SCOPE, file.metadata().object().path()).isPresent());
        assertArrayEquals(content, queries.read(SCOPE, file.metadata().object().id()).bytes());
    }

    @Test
    void emitsCanonicalFailuresForMissingAndCorruptContent() {
        FilesApplicationException missing = assertThrows(
                FilesApplicationException.class,
                () -> queries.read(SCOPE, new FileId("missing-file")));
        assertEquals(NOT_FOUND, missing.code());

        blobs.values.put(new BlobStorePort.BlobReference("v1/readme/blob"), "corrupt".getBytes());
        FilesApplicationException corrupt = assertThrows(
                FilesApplicationException.class,
                () -> queries.read(SCOPE, file.metadata().object().id()));
        assertEquals(CONTENT_INTEGRITY_FAILED, corrupt.code());
    }

    @Test
    void rejectsAnUnsafePersistedBlobBindingBeforeBlobAccess() {
        StoredFileRecord unsafe = new StoredFileRecord(
                file.metadata(),
                new BlobBinding("../outside-scope"));
        authority.records.remove(file);
        authority.records.add(unsafe);

        FilesApplicationException invalid = assertThrows(
                FilesApplicationException.class,
                () -> queries.read(SCOPE, unsafe.metadata().object().id()));

        assertEquals(INVALID_BLOB_REFERENCE, invalid.code());
        assertEquals(0, blobs.readCalls);
        assertFalse(invalid.getMessage().contains("../outside-scope"));
    }

    @Test
    void rejectsAMissingPersistedBlobBindingBeforeBlobAccess() {
        StoredFileRecord missingBinding = new StoredFileRecord(file.metadata(), null);
        authority.records.remove(file);
        authority.records.add(missingBinding);

        FilesApplicationException invalid = assertThrows(
                FilesApplicationException.class,
                () -> queries.read(SCOPE, missingBinding.metadata().object().id()));

        assertEquals(INVALID_BLOB_REFERENCE, invalid.code());
        assertEquals(0, blobs.readCalls);
        assertEquals("The Files metadata does not reference content.", invalid.getMessage());
    }

    @Test
    void reconciliationDeletesOrphansAndReportsInconsistentMetadata() {
        BlobStorePort.BlobReference orphan = new BlobStorePort.BlobReference("v1/orphan/blob");
        blobs.values.put(orphan, "orphan".getBytes());
        blobs.values.put(new BlobStorePort.BlobReference("v1/readme/blob"), "broken".getBytes());

        CanonicalFilesQueries.ReconciliationReport report = queries.reconcile(SCOPE);

        assertEquals(2, report.activeMetadataRecords());
        assertEquals(2, report.inventoriedBlobs());
        assertEquals(1, report.orphanBlobsDeleted());
        assertEquals(1, report.inconsistentMetadataRecords());
        assertFalse(blobs.values.containsKey(orphan));
    }

    private StoredFileRecord record(
            String id,
            String path,
            Kind kind,
            long size,
            String digest,
            String opaqueReference,
            String version) {
        CanonicalFileRecord metadata = new CanonicalFileRecord(
                SCOPE.organizationRef(),
                SCOPE.spaceRef(),
                new FileObject(
                        new FileId(id),
                        new FilePath(path),
                        kind,
                        size,
                        kind == Kind.FILE ? "text/plain" : null,
                        NOW,
                        false),
                new FileVersion(version),
                digest,
                1,
                ACTIVE,
                NOW);
        return new StoredFileRecord(
                metadata,
                opaqueReference == null ? null : new BlobBinding(opaqueReference));
    }

    private static final class InMemoryAuthority implements FilesAuthorityRepository {
        private final List<StoredFileRecord> records = new ArrayList<>();

        @Override
        public StoredFileRecord save(StoredFileRecord record) {
            records.removeIf(existing -> existing.metadata().object().id()
                    .equals(record.metadata().object().id()));
            records.add(record);
            return record;
        }

        @Override
        public Optional<StoredFileRecord> findByPath(
                String organizationRef,
                String spaceRef,
                FilePath path) {
            return records.stream().filter(record -> matches(record, organizationRef, spaceRef)
                    && record.metadata().object().path().equals(path)).findFirst();
        }

        @Override
        public Optional<StoredFileRecord> findById(
                String organizationRef,
                String spaceRef,
                FileId id) {
            return records.stream().filter(record -> matches(record, organizationRef, spaceRef)
                    && record.metadata().object().id().equals(id)).findFirst();
        }

        @Override
        public List<StoredFileRecord> activeFiles(String organizationRef, String spaceRef) {
            return records.stream().filter(record -> matches(record, organizationRef, spaceRef)).toList();
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
        private int readCalls;

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
                values.put(reference, bytes);
                return new BlobReceipt(reference, expectedDigest, bytes.length);
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public void readStream(BlobScope scope, BlobReference reference, OutputStream target) {
            readCalls++;
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

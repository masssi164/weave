package com.massimotter.weave.backend.files.application;

import static com.massimotter.weave.backend.files.application.FilesCommandException.Code.PARENT_MISSING;
import static com.massimotter.weave.backend.files.application.FilesCommandException.Code.PATH_CONFLICT;
import static com.massimotter.weave.backend.files.domain.FilesAuthority.Lifecycle.ACTIVE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.massimotter.weave.backend.files.domain.FilesAuthority.FileLockRecord;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileId;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import com.massimotter.weave.backend.files.port.FilesAuthorityRepository;
import com.massimotter.weave.backend.files.port.StoredFileRecord;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CanonicalFilesCommandsTest {

    private static final FilesCommandScope SCOPE =
            new FilesCommandScope("org-1", "space-1", 3);
    private static final Instant NOW = Instant.parse("2026-08-19T00:00:00Z");

    private InMemoryAuthority authority;
    private CanonicalFilesCommands commands;

    @BeforeEach
    void setUp() {
        authority = new InMemoryAuthority();
        commands = new CanonicalFilesCommands(
                authority, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsOnlyCanonicalCollectionMetadata() {
        var collection = commands.createCollection(SCOPE, new FilePath("/docs"));

        assertEquals(Kind.COLLECTION, collection.kind());
        assertEquals(new FilePath("/docs"), collection.path());
        assertEquals(NOW, collection.modifiedAt());
        assertEquals(1, authority.activeFiles(SCOPE.organizationRef(), SCOPE.spaceRef()).size());
    }

    @Test
    void enforcesCollectionParentAndOccupiedPathInvariants() {
        FilesCommandException missingParent = assertThrows(
                FilesCommandException.class,
                () -> commands.createCollection(SCOPE, new FilePath("/missing/child")));
        assertEquals(PARENT_MISSING, missingParent.code());

        commands.createCollection(SCOPE, new FilePath("/docs"));
        FilesCommandException occupied = assertThrows(
                FilesCommandException.class,
                () -> commands.createCollection(SCOPE, new FilePath("/docs")));
        assertEquals(PATH_CONFLICT, occupied.code());
    }

    private static final class InMemoryAuthority implements FilesAuthorityRepository {
        private final List<StoredFileRecord> records = new ArrayList<>();

        @Override
        public StoredFileRecord save(StoredFileRecord record) {
            records.removeIf(existing -> existing.metadata().organizationRef()
                    .equals(record.metadata().organizationRef())
                    && existing.metadata().spaceRef().equals(record.metadata().spaceRef())
                    && existing.metadata().object().id().equals(record.metadata().object().id()));
            records.add(record);
            return record;
        }

        @Override
        public Optional<StoredFileRecord> findByPath(
                String organizationRef, String spaceRef, FilePath path) {
            return records.stream()
                    .filter(record -> matches(record, organizationRef, spaceRef)
                            && record.metadata().object().path().equals(path))
                    .findFirst();
        }

        @Override
        public Optional<StoredFileRecord> findById(
                String organizationRef, String spaceRef, FileId id) {
            return records.stream()
                    .filter(record -> matches(record, organizationRef, spaceRef)
                            && record.metadata().object().id().equals(id))
                    .findFirst();
        }

        @Override
        public List<StoredFileRecord> activeFiles(String organizationRef, String spaceRef) {
            return records.stream()
                    .filter(record -> matches(record, organizationRef, spaceRef))
                    .toList();
        }

        private boolean matches(
                StoredFileRecord record, String organizationRef, String spaceRef) {
            return record.metadata().organizationRef().equals(organizationRef)
                    && record.metadata().spaceRef().equals(spaceRef)
                    && record.metadata().lifecycle() == ACTIVE;
        }

        @Override
        public List<StoredFileRecord> replace(
                List<StoredFileRecord> tombstones, List<StoredFileRecord> activations) {
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
                String organizationRef, String spaceRef, FilePath path, Instant now) {
            return Optional.empty();
        }

        @Override
        public List<FileLockRecord> activeLocks(
                String organizationRef, String spaceRef, Instant now) {
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
}

package com.massimotter.weave.backend.files.port;

import com.massimotter.weave.backend.files.domain.FilesAuthority.FileLockRecord;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileId;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Provider-independent persistence port for canonical Files metadata and locks. */
public interface FilesAuthorityRepository {

    StoredFileRecord save(StoredFileRecord record);

    /**
     * Activates one canonical metadata observation for a create or content-write command.
     *
     * <p>The default keeps non-concurrent adapters source-compatible. Persistence adapters with
     * uniqueness or optimistic-lock semantics override this method and translate implementation
     * failures into {@link ConcurrentMutationException}. Generic {@link #save} callers retain
     * their existing persistence behavior.</p>
     */
    default StoredFileRecord activate(StoredFileRecord record) {
        return save(record);
    }

    Optional<StoredFileRecord> findByPath(
            String organizationRef,
            String spaceRef,
            FilePath path);

    Optional<StoredFileRecord> findById(
            String organizationRef,
            String spaceRef,
            FileId id);

    List<StoredFileRecord> activeFiles(String organizationRef, String spaceRef);

    /** Atomically tombstones old path owners before activating replacement records. */
    List<StoredFileRecord> replace(
            List<StoredFileRecord> tombstones,
            List<StoredFileRecord> activations);

    /**
     * Command-specific tree replacement boundary.
     *
     * <p>Concurrent persistence implementations override this method and translate uniqueness or
     * optimistic-lock failures into {@link ConcurrentMutationException} for {@code operationRoot}.</p>
     */
    default List<StoredFileRecord> replaceTree(
            FilePath operationRoot,
            List<StoredFileRecord> tombstones,
            List<StoredFileRecord> activations) {
        return replace(tombstones, activations);
    }

    StoredFileRecord move(
            String organizationRef,
            String spaceRef,
            FileId id,
            FilePath expectedPath,
            FilePath destination,
            Instant movedAt);

    /** Command-specific single-node move boundary. */
    default StoredFileRecord moveNode(
            String organizationRef,
            String spaceRef,
            FileId id,
            FilePath expectedPath,
            FilePath destination,
            Instant movedAt) {
        return move(organizationRef, spaceRef, id, expectedPath, destination, movedAt);
    }

    FileLockRecord acquireLock(FileLockRecord requested, Instant now);

    Optional<FileLockRecord> activeLock(
            String organizationRef,
            String spaceRef,
            FilePath path,
            Instant now);

    List<FileLockRecord> activeLocks(
            String organizationRef,
            String spaceRef,
            Instant now);

    void releaseLock(
            String organizationRef,
            String spaceRef,
            FilePath path,
            String tokenDigest,
            String ownerRef,
            Instant now);

    void moveLock(
            String organizationRef,
            String spaceRef,
            FilePath source,
            FilePath destination,
            String tokenDigest,
            String ownerRef,
            Instant now);

    /**
     * Signals that a canonical metadata activation or tree mutation lost a concurrent persistence race.
     *
     * <p>Persistence adapters must translate framework-specific constraint or optimistic-lock
     * failures into this support-safe port failure before command use cases are wired to them.</p>
     */
    final class ConcurrentMutationException extends RuntimeException {
        public ConcurrentMutationException(FilePath path) {
            this(path, null);
        }

        public ConcurrentMutationException(FilePath path, Throwable cause) {
            super("canonical file metadata changed concurrently at " + path.value(), cause);
        }
    }

    final class LockConflictException extends RuntimeException {
        public LockConflictException(FilePath path) {
            super("file path has an active or mismatched lock: " + path.value());
        }
    }
}

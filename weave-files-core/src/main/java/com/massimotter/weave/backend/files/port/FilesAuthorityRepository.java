package com.massimotter.weave.backend.files.port;

import com.massimotter.weave.backend.files.domain.FilesAuthority.CanonicalFileRecord;
import com.massimotter.weave.backend.files.domain.FilesAuthority.FileLockRecord;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileId;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Provider-independent persistence port for canonical Files metadata and locks. */
public interface FilesAuthorityRepository {

    CanonicalFileRecord save(CanonicalFileRecord record);

    Optional<CanonicalFileRecord> findByPath(String organizationRef, String spaceRef, FilePath path);

    Optional<CanonicalFileRecord> findById(String organizationRef, String spaceRef, FileId id);

    List<CanonicalFileRecord> activeFiles(String organizationRef, String spaceRef);

    /** Atomically tombstones old path owners before activating replacement records. */
    List<CanonicalFileRecord> replace(
            List<CanonicalFileRecord> tombstones,
            List<CanonicalFileRecord> activations);

    CanonicalFileRecord move(
            String organizationRef,
            String spaceRef,
            FileId id,
            FilePath expectedPath,
            FilePath destination,
            Instant movedAt);

    FileLockRecord acquireLock(FileLockRecord requested, Instant now);

    Optional<FileLockRecord> activeLock(
            String organizationRef,
            String spaceRef,
            FilePath path,
            Instant now);

    List<FileLockRecord> activeLocks(String organizationRef, String spaceRef, Instant now);

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

    final class LockConflictException extends RuntimeException {
        public LockConflictException(FilePath path) {
            super("file path has an active or mismatched lock: " + path.value());
        }
    }
}

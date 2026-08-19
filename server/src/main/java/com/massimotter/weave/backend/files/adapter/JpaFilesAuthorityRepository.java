package com.massimotter.weave.backend.files.adapter;

import static java.util.Objects.requireNonNull;

import com.massimotter.weave.backend.files.domain.FilesAuthority.CanonicalFileRecord;
import com.massimotter.weave.backend.files.domain.FilesAuthority.FileLockRecord;
import com.massimotter.weave.backend.files.domain.FilesAuthority.Lifecycle;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileId;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.port.FilesAuthorityRepository;
import com.massimotter.weave.backend.files.port.FilesAuthorityRepository.ConcurrentMutationException;
import com.massimotter.weave.backend.files.port.FilesAuthorityRepository.LockConflictException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** JPA adapter for canonical Files metadata and fenced WebDAV locks. */
@Repository
@Transactional(readOnly = true)
public class JpaFilesAuthorityRepository implements FilesAuthorityRepository {

    private final FileObjectJpaRepository files;
    private final FileLockJpaRepository locks;

    public JpaFilesAuthorityRepository(
            FileObjectJpaRepository files,
            FileLockJpaRepository locks) {
        this.files = requireNonNull(files, "files");
        this.locks = requireNonNull(locks, "locks");
    }

    @Override
    @Transactional
    public CanonicalFileRecord save(CanonicalFileRecord record) {
        CanonicalFileRecord requested = requireNonNull(record, "record");
        CanonicalFileId id = CanonicalFileId.from(requested);
        FileObjectJpaEntity entity = files.findById(id)
                .orElseGet(() -> FileObjectJpaEntity.create(id));
        entity.observe(requested);
        return files.saveAndFlush(entity).toDomain();
    }

    @Override
    @Transactional
    public CanonicalFileRecord activate(CanonicalFileRecord record) {
        CanonicalFileRecord requested = requireNonNull(record, "record");
        try {
            return save(requested);
        } catch (DataIntegrityViolationException | OptimisticLockingFailureException concurrentMutation) {
            throw new ConcurrentMutationException(requested.object().path(), concurrentMutation);
        }
    }

    @Override
    public Optional<CanonicalFileRecord> findByPath(
            String organizationRef,
            String spaceRef,
            FilePath path) {
        return files
                .findByIdOrganizationRefAndIdSpaceRefAndCanonicalPath(
                        organizationRef,
                        spaceRef,
                        path.value())
                .map(FileObjectJpaEntity::toDomain)
                .filter(record -> record.lifecycle() == Lifecycle.ACTIVE);
    }

    @Override
    public Optional<CanonicalFileRecord> findById(
            String organizationRef,
            String spaceRef,
            FileId id) {
        return files.findById(new CanonicalFileId(
                        organizationRef,
                        spaceRef,
                        id.value()))
                .map(FileObjectJpaEntity::toDomain)
                .filter(record -> record.lifecycle() == Lifecycle.ACTIVE);
    }

    @Override
    public List<CanonicalFileRecord> activeFiles(String organizationRef, String spaceRef) {
        return files
                .findByIdOrganizationRefAndIdSpaceRefAndLifecycleOrderByCanonicalPath(
                        organizationRef, spaceRef, Lifecycle.ACTIVE)
                .stream()
                .map(FileObjectJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public List<CanonicalFileRecord> replace(
            List<CanonicalFileRecord> tombstones,
            List<CanonicalFileRecord> activations) {
        for (CanonicalFileRecord record : List.copyOf(tombstones)) {
            if (record.lifecycle() != Lifecycle.TOMBSTONED) {
                throw new IllegalArgumentException("replacement deactivation must be tombstoned");
            }
            entity(record).observe(record);
        }
        files.flush();
        List<CanonicalFileRecord> activated = new java.util.ArrayList<>();
        for (CanonicalFileRecord record : List.copyOf(activations)) {
            if (record.lifecycle() != Lifecycle.ACTIVE) {
                throw new IllegalArgumentException("replacement activation must be active");
            }
            FileObjectJpaEntity entity = entity(record);
            entity.observe(record);
            activated.add(entity.toDomain());
        }
        files.flush();
        return List.copyOf(activated);
    }

    @Override
    @Transactional
    public CanonicalFileRecord move(
            String organizationRef,
            String spaceRef,
            FileId id,
            FilePath expectedPath,
            FilePath destination,
            Instant movedAt) {
        FileObjectJpaEntity entity = files.findById(new CanonicalFileId(
                        organizationRef,
                        spaceRef,
                        id.value()))
                .orElseThrow(() -> new StaleCanonicalFileException(id, expectedPath));
        if (!entity.move(expectedPath, destination, movedAt)) {
            throw new StaleCanonicalFileException(id, expectedPath);
        }
        return files.saveAndFlush(entity).toDomain();
    }

    @Override
    @Transactional
    public FileLockRecord acquireLock(
            FileLockRecord requested,
            Instant now) {
        FileLockId id = FileLockId.from(requested);
        FileLockJpaEntity current = locks.lockById(id).orElse(null);
        if (current != null && current.activeAt(now)) {
            throw new LockConflictException(requested.path());
        }
        FileLockJpaEntity acquired = current == null
                ? FileLockJpaEntity.create(id, requested, 1)
                : current.reacquire(requested);
        try {
            return locks.saveAndFlush(acquired).toDomain();
        } catch (DataIntegrityViolationException concurrentAcquisition) {
            throw new LockConflictException(requested.path());
        }
    }

    @Override
    public Optional<FileLockRecord> activeLock(
            String organizationRef,
            String spaceRef,
            FilePath path,
            Instant now) {
        return locks.findById(new FileLockId(
                        organizationRef,
                        spaceRef,
                        path.value()))
                .filter(lock -> lock.activeAt(now))
                .map(FileLockJpaEntity::toDomain);
    }

    @Override
    public List<FileLockRecord> activeLocks(
            String organizationRef,
            String spaceRef,
            Instant now) {
        return locks
                .findByIdOrganizationRefAndIdSpaceRefAndReleasedAtIsNullAndExpiresAtAfterOrderByIdCanonicalPath(
                        organizationRef,
                        spaceRef,
                        FilesPersistenceTime.utc(now))
                .stream()
                .map(FileLockJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void releaseLock(
            String organizationRef,
            String spaceRef,
            FilePath path,
            String tokenDigest,
            String ownerRef,
            Instant now) {
        FileLockJpaEntity lock = locks.lockById(new FileLockId(
                        organizationRef,
                        spaceRef,
                        path.value()))
                .orElseThrow(() -> new LockConflictException(path));
        if (!lock.release(tokenDigest, ownerRef, now)) {
            throw new LockConflictException(path);
        }
        locks.saveAndFlush(lock);
    }

    @Override
    @Transactional
    public void moveLock(
            String organizationRef,
            String spaceRef,
            FilePath source,
            FilePath destination,
            String tokenDigest,
            String ownerRef,
            Instant now) {
        FileLockId sourceId = new FileLockId(
                organizationRef,
                spaceRef,
                source.value());
        FileLockJpaEntity lock = locks.lockById(sourceId)
                .orElseThrow(() -> new LockConflictException(source));
        if (!lock.ownedAndActive(tokenDigest, ownerRef, now)
                || locks.existsById(new FileLockId(
                        organizationRef,
                        spaceRef,
                        destination.value()))) {
            throw new LockConflictException(source);
        }
        FileLockJpaEntity moved = lock.rekey(destination);
        locks.delete(lock);
        locks.flush();
        locks.saveAndFlush(moved);
    }

    public static final class StaleCanonicalFileException extends RuntimeException {
        public StaleCanonicalFileException(FileId id, FilePath expectedPath) {
            super("canonical file changed before move: " + id.value()
                    + " at " + expectedPath.value());
        }
    }

    private FileObjectJpaEntity entity(CanonicalFileRecord record) {
        CanonicalFileId id = CanonicalFileId.from(record);
        return files.findById(id).orElseGet(() -> {
            FileObjectJpaEntity created = FileObjectJpaEntity.create(id);
            return files.save(created);
        });
    }
}

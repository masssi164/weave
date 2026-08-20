package com.massimotter.weave.backend.files.adapter;

import static java.util.Objects.requireNonNull;

import com.massimotter.weave.backend.files.domain.FilesAuthority.FileLockRecord;
import com.massimotter.weave.backend.files.domain.FilesAuthority.Lifecycle;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileId;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import com.massimotter.weave.backend.files.domain.FilesSearch;
import com.massimotter.weave.backend.files.domain.FilesSearch.ScopeDepth;
import com.massimotter.weave.backend.files.port.FilesAuthorityRepository;
import com.massimotter.weave.backend.files.port.FilesAuthorityRepository.ConcurrentMutationException;
import com.massimotter.weave.backend.files.port.FilesAuthorityRepository.LockConflictException;
import com.massimotter.weave.backend.files.port.StoredFileRecord;
import jakarta.persistence.OptimisticLockException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.hibernate.StaleStateException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
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
    public StoredFileRecord save(StoredFileRecord record) {
        StoredFileRecord requested = requireNonNull(record, "record");
        CanonicalFileId id = CanonicalFileId.from(requested);
        FileObjectJpaEntity entity = files.findById(id)
                .orElseGet(() -> FileObjectJpaEntity.create(id));
        entity.observe(requested);
        return files.saveAndFlush(entity).toStoredRecord();
    }

    @Override
    @Transactional
    public StoredFileRecord activate(StoredFileRecord record) {
        StoredFileRecord requested = requireNonNull(record, "record");
        try {
            return save(requested);
        } catch (DataIntegrityViolationException
                 | OptimisticLockingFailureException
                 | ConstraintViolationException concurrentMutation) {
            throw concurrent(requested.metadata().object().path(), concurrentMutation);
        }
    }

    @Override
    public Optional<StoredFileRecord> findByPath(
            String organizationRef,
            String spaceRef,
            FilePath path) {
        return files
                .findByIdOrganizationRefAndIdSpaceRefAndCanonicalPath(
                        organizationRef,
                        spaceRef,
                        path.value())
                .map(FileObjectJpaEntity::toStoredRecord)
                .filter(record -> record.metadata().lifecycle() == Lifecycle.ACTIVE);
    }

    @Override
    public Optional<StoredFileRecord> findById(
            String organizationRef,
            String spaceRef,
            FileId id) {
        return files.findById(new CanonicalFileId(
                        organizationRef,
                        spaceRef,
                        id.value()))
                .map(FileObjectJpaEntity::toStoredRecord)
                .filter(record -> record.metadata().lifecycle() == Lifecycle.ACTIVE);
    }

    @Override
    public List<StoredFileRecord> activeFiles(String organizationRef, String spaceRef) {
        return files
                .findByIdOrganizationRefAndIdSpaceRefAndLifecycleOrderByCanonicalPath(
                        organizationRef, spaceRef, Lifecycle.ACTIVE)
                .stream()
                .map(FileObjectJpaEntity::toStoredRecord)
                .toList();
    }

    @Override
    public List<StoredFileRecord> activeSearchCandidates(
            String organizationRef,
            String spaceRef,
            FilePath scopePath,
            ScopeDepth scopeDepth,
            int maximumRows) {
        requireNonNull(organizationRef, "organizationRef");
        requireNonNull(spaceRef, "spaceRef");
        FilePath requiredPath = requireNonNull(scopePath, "scopePath");
        ScopeDepth requiredDepth = requireNonNull(scopeDepth, "scopeDepth");
        if (maximumRows < 1 || maximumRows > FilesSearch.MAXIMUM_CANDIDATES + 1) {
            throw new IllegalArgumentException("Files search maximumRows must be between 1 and 1001");
        }

        List<FileObjectJpaEntity> selected = new java.util.ArrayList<>(maximumRows);
        if (!requiredPath.root()) {
            FileObjectJpaEntity scope = files
                    .findByIdOrganizationRefAndIdSpaceRefAndActivePathKey(
                            organizationRef,
                            spaceRef,
                            requiredPath.value())
                    .orElse(null);
            if (scope == null) {
                return List.of();
            }
            selected.add(scope);
            if (maximumRows == 1
                    || requiredDepth == ScopeDepth.ZERO
                    || scope.toStoredRecord().metadata().object().kind() != Kind.COLLECTION) {
                return selected.stream().map(FileObjectJpaEntity::toStoredRecord).toList();
            }
        } else if (requiredDepth == ScopeDepth.ZERO) {
            return List.of();
        }

        int remaining = maximumRows - selected.size();
        String pathPrefix = requiredPath.root() ? "/" : requiredPath.value() + "/";
        int childStart = pathPrefix.length() + 1;
        List<FileObjectJpaEntity> descendants = requiredDepth == ScopeDepth.ONE
                ? files.findActiveChildren(
                        organizationRef,
                        spaceRef,
                        pathPrefix,
                        pathPrefix.length(),
                        childStart,
                        PageRequest.of(0, remaining))
                : files.findActiveDescendants(
                        organizationRef,
                        spaceRef,
                        pathPrefix,
                        pathPrefix.length(),
                        PageRequest.of(0, remaining));
        selected.addAll(descendants);
        return selected.stream().map(FileObjectJpaEntity::toStoredRecord).toList();
    }

    @Override
    public List<StoredFileRecord> storedFiles(String organizationRef, String spaceRef) {
        return files
                .findByIdOrganizationRefAndIdSpaceRefOrderByCanonicalPath(
                        organizationRef, spaceRef)
                .stream()
                .map(FileObjectJpaEntity::toStoredRecord)
                .toList();
    }

    @Override
    @Transactional
    public List<StoredFileRecord> replace(
            List<StoredFileRecord> tombstones,
            List<StoredFileRecord> activations) {
        for (StoredFileRecord record : List.copyOf(tombstones)) {
            if (record.metadata().lifecycle() != Lifecycle.TOMBSTONED) {
                throw new IllegalArgumentException("replacement deactivation must be tombstoned");
            }
            observe(record);
        }
        files.flush();
        List<StoredFileRecord> activated = new java.util.ArrayList<>();
        for (StoredFileRecord record : List.copyOf(activations)) {
            if (record.metadata().lifecycle() != Lifecycle.ACTIVE) {
                throw new IllegalArgumentException("replacement activation must be active");
            }
            FileObjectJpaEntity entity = observe(record);
            activated.add(entity.toStoredRecord());
        }
        files.flush();
        return List.copyOf(activated);
    }

    @Override
    @Transactional
    public List<StoredFileRecord> replaceTree(
            FilePath operationRoot,
            List<StoredFileRecord> tombstones,
            List<StoredFileRecord> activations) {
        FilePath root = requireNonNull(operationRoot, "operationRoot");
        try {
            return replace(tombstones, activations);
        } catch (DataIntegrityViolationException
                 | OptimisticLockingFailureException
                 | OptimisticLockException
                 | ConstraintViolationException
                 | StaleStateException concurrentMutation) {
            throw concurrent(root, concurrentMutation);
        }
    }

    @Override
    @Transactional
    public StoredFileRecord move(
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
        return files.saveAndFlush(entity).toStoredRecord();
    }

    @Override
    @Transactional
    public StoredFileRecord moveNode(
            String organizationRef,
            String spaceRef,
            FileId id,
            FilePath expectedPath,
            FilePath destination,
            Instant movedAt) {
        FilePath source = requireNonNull(expectedPath, "expectedPath");
        try {
            return move(
                    organizationRef,
                    spaceRef,
                    id,
                    source,
                    destination,
                    movedAt);
        } catch (StaleCanonicalFileException
                 | DataIntegrityViolationException
                 | OptimisticLockingFailureException
                 | OptimisticLockException
                 | ConstraintViolationException
                 | StaleStateException concurrentMutation) {
            throw concurrent(source, concurrentMutation);
        }
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

    private ConcurrentMutationException concurrent(
            FilePath path,
            RuntimeException cause) {
        return new ConcurrentMutationException(path, cause);
    }

    private FileObjectJpaEntity observe(StoredFileRecord record) {
        CanonicalFileId id = CanonicalFileId.from(record);
        FileObjectJpaEntity entity = files.findById(id)
                .orElseGet(() -> FileObjectJpaEntity.create(id));
        entity.observe(record);
        return files.save(entity);
    }
}

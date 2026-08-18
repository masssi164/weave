package com.massimotter.weave.backend.files.application;

import static com.massimotter.weave.backend.data.domain.CanonicalData.Lifecycle;
import static com.massimotter.weave.backend.data.domain.CanonicalData.ModelVersion;
import static com.massimotter.weave.backend.data.domain.CanonicalData.ObjectId;
import static com.massimotter.weave.backend.data.domain.CanonicalData.Provenance;
import static com.massimotter.weave.backend.data.domain.CanonicalData.ProvenanceKind;
import static com.massimotter.weave.backend.data.domain.CanonicalData.Revision;
import static com.massimotter.weave.backend.data.domain.CanonicalData.Scope;

import com.massimotter.weave.backend.files.domain.CanonicalFile;
import com.massimotter.weave.backend.files.port.FilesIdGenerator;
import com.massimotter.weave.backend.files.port.persistence.CanonicalFilesRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Provider-independent Files metadata use cases. */
public final class CanonicalFilesService {

    private final CanonicalFilesRepository repository;
    private final FilesIdGenerator idGenerator;
    private final ModelVersion modelVersion;
    private final Clock clock;

    public CanonicalFilesService(
            CanonicalFilesRepository repository,
            FilesIdGenerator idGenerator,
            ModelVersion modelVersion,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator must not be null");
        this.modelVersion = Objects.requireNonNull(modelVersion, "modelVersion must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public CanonicalFile createCollection(CreateCollection command) {
        Objects.requireNonNull(command, "command must not be null");
        ensureCollectionParent(command.scope(), command.parentId());
        ensureNameAvailable(command.scope(), command.parentId(), command.name(), null);

        Instant now = clock.instant();
        CanonicalFile collection = CanonicalFile.collection(
                idGenerator.nextId(),
                modelVersion,
                repository.nextRevision(command.scope()),
                Lifecycle.ACTIVE,
                nativeProvenance(now),
                command.parentId(),
                command.name(),
                now,
                now,
                List.of());
        repository.insert(command.scope(), collection);
        return collection;
    }

    public CanonicalFile putFile(PutFile command) {
        Objects.requireNonNull(command, "command must not be null");
        ensureCollectionParent(command.scope(), command.parentId());

        Optional<CanonicalFile> existing = repository.findChild(
                command.scope(), command.parentId(), command.name());
        Instant now = clock.instant();
        Revision nextRevision = repository.nextRevision(command.scope());

        if (existing.isEmpty()) {
            if (command.expectedRevision() != null) {
                throw new FilesConflictException(
                        "expected revision was supplied for a file that does not exist");
            }
            CanonicalFile created = CanonicalFile.file(
                    idGenerator.nextId(),
                    modelVersion,
                    nextRevision,
                    Lifecycle.ACTIVE,
                    nativeProvenance(now),
                    command.parentId(),
                    command.name(),
                    command.mediaType(),
                    command.byteSize(),
                    command.contentDigest(),
                    now,
                    now,
                    List.of());
            repository.insert(command.scope(), created);
            return created;
        }

        CanonicalFile current = existing.orElseThrow();
        if (current.kind() != CanonicalFile.Kind.FILE || current.lifecycle() != Lifecycle.ACTIVE) {
            throw new FilesConflictException("the target name is not an active file");
        }
        requireExpectedRevision(current, command.expectedRevision());

        CanonicalFile replacement = CanonicalFile.file(
                current.objectId(),
                modelVersion,
                nextRevision,
                Lifecycle.ACTIVE,
                nativeProvenance(now),
                current.parentId(),
                current.name(),
                command.mediaType(),
                command.byteSize(),
                command.contentDigest(),
                current.createdAt(),
                now,
                current.relatedDependencies());
        repository.replace(command.scope(), current.revision(), replacement);
        return replacement;
    }

    public CanonicalFile move(Move command) {
        Objects.requireNonNull(command, "command must not be null");
        CanonicalFile current = requireActive(command.scope(), command.objectId());
        requireExpectedRevision(current, command.expectedRevision());
        ensureCollectionParent(command.scope(), command.newParentId());
        ensureNameAvailable(
                command.scope(),
                command.newParentId(),
                command.newName(),
                current.objectId());

        if (Objects.equals(current.parentId(), command.newParentId())
                && current.name().equals(command.newName().trim())) {
            return current;
        }

        Instant now = clock.instant();
        CanonicalFile replacement = new CanonicalFile(
                current.objectId(),
                modelVersion,
                repository.nextRevision(command.scope()),
                Lifecycle.ACTIVE,
                nativeProvenance(now),
                current.kind(),
                command.newParentId(),
                command.newName(),
                current.mediaType(),
                current.byteSize(),
                current.contentDigest(),
                current.createdAt(),
                now,
                current.relatedDependencies());
        repository.replace(command.scope(), current.revision(), replacement);
        return replacement;
    }

    public CanonicalFile delete(Delete command) {
        Objects.requireNonNull(command, "command must not be null");
        CanonicalFile current = requireActive(command.scope(), command.objectId());
        requireExpectedRevision(current, command.expectedRevision());
        if (current.kind() == CanonicalFile.Kind.COLLECTION
                && !repository.listChildren(command.scope(), current.objectId()).stream()
                        .filter(child -> child.lifecycle() == Lifecycle.ACTIVE)
                        .toList()
                        .isEmpty()) {
            throw new FilesConflictException("an active non-empty collection cannot be deleted");
        }

        Instant now = clock.instant();
        CanonicalFile tombstone = new CanonicalFile(
                current.objectId(),
                modelVersion,
                repository.nextRevision(command.scope()),
                Lifecycle.TOMBSTONED,
                nativeProvenance(now),
                current.kind(),
                current.parentId(),
                current.name(),
                current.mediaType(),
                current.byteSize(),
                current.contentDigest(),
                current.createdAt(),
                now,
                current.relatedDependencies());
        repository.replace(command.scope(), current.revision(), tombstone);
        return tombstone;
    }

    public CanonicalFile read(Scope scope, ObjectId objectId) {
        return requireActive(scope, objectId);
    }

    public List<CanonicalFile> listChildren(Scope scope, ObjectId parentId) {
        Objects.requireNonNull(scope, "scope must not be null");
        if (parentId != null) {
            ensureCollectionParent(scope, parentId);
        }
        return repository.listChildren(scope, parentId).stream()
                .filter(file -> file.lifecycle() == Lifecycle.ACTIVE)
                .toList();
    }

    private CanonicalFile requireActive(Scope scope, ObjectId objectId) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(objectId, "objectId must not be null");
        CanonicalFile file = repository.find(scope, objectId)
                .orElseThrow(() -> new FilesNotFoundException("canonical Files object not found"));
        if (file.lifecycle() != Lifecycle.ACTIVE) {
            throw new FilesNotFoundException("canonical Files object is not active");
        }
        return file;
    }

    private void ensureCollectionParent(Scope scope, ObjectId parentId) {
        Objects.requireNonNull(scope, "scope must not be null");
        if (parentId == null) {
            return;
        }
        CanonicalFile parent = requireActive(scope, parentId);
        if (parent.kind() != CanonicalFile.Kind.COLLECTION) {
            throw new FilesConflictException("parent must be an active collection");
        }
    }

    private void ensureNameAvailable(
            Scope scope, ObjectId parentId, String name, ObjectId allowedObjectId) {
        repository.findChild(scope, parentId, name).ifPresent(existing -> {
            boolean sameObject = allowedObjectId != null && existing.objectId().equals(allowedObjectId);
            if (!sameObject && existing.lifecycle() == Lifecycle.ACTIVE) {
                throw new FilesConflictException("an active Files object already uses the target name");
            }
        });
    }

    private static void requireExpectedRevision(CanonicalFile current, Revision expectedRevision) {
        if (expectedRevision == null || !current.revision().equals(expectedRevision)) {
            throw new FilesConflictException("expected revision does not match current Files state");
        }
    }

    private static Provenance nativeProvenance(Instant now) {
        return new Provenance(ProvenanceKind.NATIVE, null, now);
    }

    public record CreateCollection(Scope scope, ObjectId parentId, String name) {
        public CreateCollection {
            scope = Objects.requireNonNull(scope, "scope must not be null");
        }
    }

    public record PutFile(
            Scope scope,
            ObjectId parentId,
            String name,
            String mediaType,
            long byteSize,
            String contentDigest,
            Revision expectedRevision) {
        public PutFile {
            scope = Objects.requireNonNull(scope, "scope must not be null");
        }
    }

    public record Move(
            Scope scope,
            ObjectId objectId,
            ObjectId newParentId,
            String newName,
            Revision expectedRevision) {
        public Move {
            scope = Objects.requireNonNull(scope, "scope must not be null");
            objectId = Objects.requireNonNull(objectId, "objectId must not be null");
        }
    }

    public record Delete(Scope scope, ObjectId objectId, Revision expectedRevision) {
        public Delete {
            scope = Objects.requireNonNull(scope, "scope must not be null");
            objectId = Objects.requireNonNull(objectId, "objectId must not be null");
        }
    }

    public static final class FilesConflictException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public FilesConflictException(String message) {
            super(message);
        }
    }

    public static final class FilesNotFoundException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public FilesNotFoundException(String message) {
            super(message);
        }
    }
}

package com.massimotter.weave.backend.files.application;

import static com.massimotter.weave.backend.data.domain.CanonicalData.Domain;
import static com.massimotter.weave.backend.data.domain.CanonicalData.Lifecycle;
import static com.massimotter.weave.backend.data.domain.CanonicalData.ModelVersion;
import static com.massimotter.weave.backend.data.domain.CanonicalData.ObjectId;
import static com.massimotter.weave.backend.data.domain.CanonicalData.Revision;
import static com.massimotter.weave.backend.data.domain.CanonicalData.Scope;
import static com.massimotter.weave.backend.files.application.CanonicalFilesService.CreateCollection;
import static com.massimotter.weave.backend.files.application.CanonicalFilesService.Delete;
import static com.massimotter.weave.backend.files.application.CanonicalFilesService.FilesConflictException;
import static com.massimotter.weave.backend.files.application.CanonicalFilesService.FilesNotFoundException;
import static com.massimotter.weave.backend.files.application.CanonicalFilesService.Move;
import static com.massimotter.weave.backend.files.application.CanonicalFilesService.PutFile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.massimotter.weave.backend.files.domain.CanonicalFile;
import com.massimotter.weave.backend.files.port.FilesIdGenerator;
import com.massimotter.weave.backend.files.port.persistence.CanonicalFilesRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CanonicalFilesServiceTest {

    private static final Scope SCOPE = new Scope("org-1", "space-1");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-18T16:00:00Z"), ZoneOffset.UTC);

    private InMemoryRepository repository;
    private CanonicalFilesService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryRepository();
        AtomicInteger ids = new AtomicInteger();
        FilesIdGenerator idGenerator = () -> new ObjectId(
                Domain.FILES, "files-" + ids.incrementAndGet());
        service = new CanonicalFilesService(
                repository, idGenerator, new ModelVersion("files-1"), CLOCK);
    }

    @Test
    void createsUpdatesMovesAndTombstonesCanonicalFiles() {
        CanonicalFile firstCollection = service.createCollection(
                new CreateCollection(SCOPE, null, "documents"));
        CanonicalFile secondCollection = service.createCollection(
                new CreateCollection(SCOPE, null, "archive"));

        CanonicalFile created = service.putFile(new PutFile(
                SCOPE,
                firstCollection.objectId(),
                "report.txt",
                "text/plain",
                5,
                "sha256:first",
                null));
        assertEquals(firstCollection.objectId(), created.parentId());
        assertEquals(new Revision(3), created.revision());

        CanonicalFile updated = service.putFile(new PutFile(
                SCOPE,
                firstCollection.objectId(),
                "report.txt",
                "text/plain",
                6,
                "sha256:second",
                created.revision()));
        assertEquals(created.objectId(), updated.objectId());
        assertEquals("sha256:second", updated.contentDigest());

        CanonicalFile moved = service.move(new Move(
                SCOPE,
                updated.objectId(),
                secondCollection.objectId(),
                "final.txt",
                updated.revision()));
        assertEquals(secondCollection.objectId(), moved.parentId());
        assertEquals("final.txt", moved.name());
        assertEquals(List.of(moved), service.listChildren(SCOPE, secondCollection.objectId()));

        CanonicalFile tombstone = service.delete(
                new Delete(SCOPE, moved.objectId(), moved.revision()));
        assertEquals(Lifecycle.TOMBSTONED, tombstone.lifecycle());
        assertEquals(List.of(), service.listChildren(SCOPE, secondCollection.objectId()));
        assertThrows(
                FilesNotFoundException.class,
                () -> service.read(SCOPE, moved.objectId()));
    }

    @Test
    void rejectsStalePreconditionsAndNameCollisions() {
        CanonicalFile collection = service.createCollection(
                new CreateCollection(SCOPE, null, "documents"));
        CanonicalFile file = service.putFile(new PutFile(
                SCOPE,
                collection.objectId(),
                "report.txt",
                "text/plain",
                5,
                "sha256:first",
                null));

        assertThrows(
                FilesConflictException.class,
                () -> service.putFile(new PutFile(
                        SCOPE,
                        collection.objectId(),
                        "report.txt",
                        "text/plain",
                        6,
                        "sha256:second",
                        new Revision(file.revision().value() + 1))));

        service.createCollection(new CreateCollection(SCOPE, collection.objectId(), "nested"));
        assertThrows(
                FilesConflictException.class,
                () -> service.createCollection(
                        new CreateCollection(SCOPE, collection.objectId(), "nested")));
    }

    @Test
    void rejectsDeletingNonEmptyCollection() {
        CanonicalFile collection = service.createCollection(
                new CreateCollection(SCOPE, null, "documents"));
        service.putFile(new PutFile(
                SCOPE,
                collection.objectId(),
                "report.txt",
                "text/plain",
                5,
                "sha256:first",
                null));

        assertThrows(
                FilesConflictException.class,
                () -> service.delete(
                        new Delete(SCOPE, collection.objectId(), collection.revision())));
    }

    @Test
    void keepsOrganizationScopesIsolated() {
        Scope other = new Scope("org-2", "space-1");
        CanonicalFile collection = service.createCollection(
                new CreateCollection(SCOPE, null, "documents"));

        assertThrows(
                FilesNotFoundException.class,
                () -> service.read(other, collection.objectId()));
        assertEquals(List.of(), service.listChildren(other, null));
    }

    private static final class InMemoryRepository implements CanonicalFilesRepository {
        private final Map<Scope, Map<ObjectId, CanonicalFile>> scoped = new HashMap<>();
        private final Map<Scope, Long> revisions = new HashMap<>();

        @Override
        public Optional<CanonicalFile> find(Scope scope, ObjectId objectId) {
            return Optional.ofNullable(objects(scope).get(objectId));
        }

        @Override
        public Optional<CanonicalFile> findChild(
                Scope scope, ObjectId parentId, String name) {
            return objects(scope).values().stream()
                    .filter(file -> Objects.equals(file.parentId(), parentId))
                    .filter(file -> file.name().equals(name.trim()))
                    .findFirst();
        }

        @Override
        public List<CanonicalFile> listChildren(Scope scope, ObjectId parentId) {
            List<CanonicalFile> children = new ArrayList<>();
            for (CanonicalFile file : objects(scope).values()) {
                if (Objects.equals(file.parentId(), parentId)) {
                    children.add(file);
                }
            }
            return List.copyOf(children);
        }

        @Override
        public Revision nextRevision(Scope scope) {
            long next = revisions.merge(scope, 1L, Long::sum);
            return new Revision(next);
        }

        @Override
        public void insert(Scope scope, CanonicalFile file) {
            CanonicalFile existing = objects(scope).putIfAbsent(file.objectId(), file);
            if (existing != null) {
                throw new FilesConflictException("duplicate canonical Files object id");
            }
        }

        @Override
        public void replace(
                Scope scope, Revision expectedRevision, CanonicalFile replacement) {
            Map<ObjectId, CanonicalFile> objects = objects(scope);
            CanonicalFile current = objects.get(replacement.objectId());
            if (current == null || !current.revision().equals(expectedRevision)) {
                throw new FilesConflictException("persistence revision conflict");
            }
            objects.put(replacement.objectId(), replacement);
        }

        private Map<ObjectId, CanonicalFile> objects(Scope scope) {
            return scoped.computeIfAbsent(scope, ignored -> new LinkedHashMap<>());
        }
    }
}

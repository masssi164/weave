package com.massimotter.weave.backend.files.application;

import static com.massimotter.weave.backend.data.domain.CanonicalData.Domain;
import static com.massimotter.weave.backend.data.domain.CanonicalData.Lifecycle;
import static com.massimotter.weave.backend.data.domain.CanonicalData.ModelVersion;
import static com.massimotter.weave.backend.data.domain.CanonicalData.ObjectId;
import static com.massimotter.weave.backend.data.domain.CanonicalData.Provenance;
import static com.massimotter.weave.backend.data.domain.CanonicalData.ProvenanceKind;
import static com.massimotter.weave.backend.data.domain.CanonicalData.Revision;
import static com.massimotter.weave.backend.data.domain.CanonicalData.Scope;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.massimotter.weave.backend.files.domain.CanonicalFile;
import com.massimotter.weave.backend.files.port.persistence.CanonicalFilesRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CanonicalFilesPathResolverTest {

    private static final Scope SCOPE = new Scope("org-1", "space-1");

    private Repository repository;
    private CanonicalFilesPathResolver resolver;
    private CanonicalFile documents;
    private CanonicalFile report;

    @BeforeEach
    void setUp() {
        repository = new Repository();
        resolver = new CanonicalFilesPathResolver(repository);
        documents = collection("collection-1", null, "documents");
        report = file("file-1", documents.objectId(), "report.txt");
        repository.insert(SCOPE, documents);
        repository.insert(SCOPE, report);
    }

    @Test
    void resolvesExistingPathAndProducesStableCanonicalPath() {
        assertEquals(
                Optional.of(report),
                resolver.resolveExisting(SCOPE, "/documents/report.txt"));
        assertEquals(
                Optional.of(documents),
                resolver.resolveExisting(SCOPE, "/documents/"));
        assertEquals("/documents/report.txt", resolver.pathOf(SCOPE, report.objectId()));
    }

    @Test
    void resolvesWriteTargetWithoutInventingProviderPathIdentity() {
        assertEquals(
                new CanonicalFilesPathResolver.Target(documents.objectId(), "new.txt"),
                resolver.resolveTarget(SCOPE, "/documents/new.txt"));
        assertEquals(
                new CanonicalFilesPathResolver.Target(null, "root.txt"),
                resolver.resolveTarget(SCOPE, "/root.txt"));
    }

    @Test
    void rejectsTraversalDoubleSeparatorsAndRootMutation() {
        assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolveExisting(SCOPE, "/documents/../secret"));
        assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolveExisting(SCOPE, "/documents//report.txt"));
        assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolveTarget(SCOPE, "/"));
    }

    @Test
    void detectsCanonicalHierarchyCycle() {
        CanonicalFile first = collection("cycle-1", new ObjectId(Domain.FILES, "cycle-2"), "one");
        CanonicalFile second = collection("cycle-2", first.objectId(), "two");
        repository.insert(SCOPE, first);
        repository.insert(SCOPE, second);

        assertThrows(
                IllegalStateException.class,
                () -> resolver.pathOf(SCOPE, first.objectId()));
    }

    @Test
    void keepsScopesIsolated() {
        assertTrue(resolver.resolveExisting(
                new Scope("org-2", "space-1"), "/documents/report.txt").isEmpty());
    }

    private static CanonicalFile collection(String id, ObjectId parentId, String name) {
        return CanonicalFile.collection(
                new ObjectId(Domain.FILES, id),
                new ModelVersion("files-1"),
                new Revision(1),
                Lifecycle.ACTIVE,
                new Provenance(ProvenanceKind.NATIVE, null, Instant.EPOCH),
                parentId,
                name,
                Instant.EPOCH,
                Instant.EPOCH,
                List.of());
    }

    private static CanonicalFile file(String id, ObjectId parentId, String name) {
        return CanonicalFile.file(
                new ObjectId(Domain.FILES, id),
                new ModelVersion("files-1"),
                new Revision(1),
                Lifecycle.ACTIVE,
                new Provenance(ProvenanceKind.NATIVE, null, Instant.EPOCH),
                parentId,
                name,
                "text/plain",
                4,
                "sha256:text",
                Instant.EPOCH,
                Instant.EPOCH,
                List.of());
    }

    private static final class Repository implements CanonicalFilesRepository {
        private final Map<Scope, Map<ObjectId, CanonicalFile>> scoped = new LinkedHashMap<>();
        private long revision;

        @Override
        public Optional<CanonicalFile> find(Scope scope, ObjectId objectId) {
            return Optional.ofNullable(objects(scope).get(objectId));
        }

        @Override
        public Optional<CanonicalFile> findChild(
                Scope scope, ObjectId parentId, String name) {
            return objects(scope).values().stream()
                    .filter(file -> Objects.equals(file.parentId(), parentId))
                    .filter(file -> file.name().equals(name))
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
            return new Revision(++revision);
        }

        @Override
        public void insert(Scope scope, CanonicalFile file) {
            objects(scope).put(file.objectId(), file);
        }

        @Override
        public void replace(
                Scope scope, Revision expectedRevision, CanonicalFile replacement) {
            objects(scope).put(replacement.objectId(), replacement);
        }

        private Map<ObjectId, CanonicalFile> objects(Scope scope) {
            return scoped.computeIfAbsent(scope, ignored -> new LinkedHashMap<>());
        }
    }
}

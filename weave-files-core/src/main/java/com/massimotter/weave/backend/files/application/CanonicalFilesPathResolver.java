package com.massimotter.weave.backend.files.application;

import static com.massimotter.weave.backend.data.domain.CanonicalData.Lifecycle;
import static com.massimotter.weave.backend.data.domain.CanonicalData.ObjectId;
import static com.massimotter.weave.backend.data.domain.CanonicalData.Scope;

import com.massimotter.weave.backend.files.domain.CanonicalFile;
import com.massimotter.weave.backend.files.port.persistence.CanonicalFilesRepository;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Maps decoded Weave WebDAV paths to canonical Files identities. */
public final class CanonicalFilesPathResolver {

    private static final int MAX_DEPTH = 1024;

    private final CanonicalFilesRepository repository;

    public CanonicalFilesPathResolver(CanonicalFilesRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    public Optional<CanonicalFile> resolveExisting(Scope scope, String decodedAbsolutePath) {
        Objects.requireNonNull(scope, "scope must not be null");
        List<String> segments = segments(decodedAbsolutePath);
        if (segments.isEmpty()) {
            return Optional.empty();
        }

        ObjectId parentId = null;
        CanonicalFile current = null;
        for (String segment : segments) {
            Optional<CanonicalFile> next = repository.findChild(scope, parentId, segment)
                    .filter(file -> file.lifecycle() == Lifecycle.ACTIVE);
            if (next.isEmpty()) {
                return Optional.empty();
            }
            current = next.orElseThrow();
            parentId = current.objectId();
        }
        return Optional.ofNullable(current);
    }

    public Target resolveTarget(Scope scope, String decodedAbsolutePath) {
        Objects.requireNonNull(scope, "scope must not be null");
        List<String> segments = segments(decodedAbsolutePath);
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("the DAV root is not a mutable Files object target");
        }

        String name = segments.getLast();
        if (segments.size() == 1) {
            return new Target(null, name);
        }

        String parentPath = "/" + String.join("/", segments.subList(0, segments.size() - 1));
        CanonicalFile parent = resolveExisting(scope, parentPath)
                .orElseThrow(() -> new CanonicalFilesService.FilesNotFoundException(
                        "canonical parent collection was not found"));
        if (parent.kind() != CanonicalFile.Kind.COLLECTION) {
            throw new CanonicalFilesService.FilesConflictException(
                    "canonical parent path is not a collection");
        }
        return new Target(parent.objectId(), name);
    }

    public String pathOf(Scope scope, ObjectId objectId) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(objectId, "objectId must not be null");

        Deque<String> names = new ArrayDeque<>();
        Set<ObjectId> visited = new HashSet<>();
        ObjectId currentId = objectId;
        int depth = 0;
        while (currentId != null) {
            if (!visited.add(currentId)) {
                throw new IllegalStateException("canonical Files hierarchy contains a cycle");
            }
            if (++depth > MAX_DEPTH) {
                throw new IllegalStateException("canonical Files hierarchy exceeds maximum depth");
            }
            CanonicalFile current = repository.find(scope, currentId)
                    .filter(file -> file.lifecycle() == Lifecycle.ACTIVE)
                    .orElseThrow(() -> new CanonicalFilesService.FilesNotFoundException(
                            "canonical Files path contains a missing object"));
            names.addFirst(current.name());
            currentId = current.parentId();
        }
        return "/" + String.join("/", names);
    }

    private static List<String> segments(String path) {
        if (path == null || path.isBlank() || path.charAt(0) != '/') {
            throw new IllegalArgumentException("Files path must be an absolute decoded path");
        }
        if (path.indexOf('\0') >= 0 || path.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Files path contains a forbidden character");
        }
        if (path.equals("/")) {
            return List.of();
        }

        String normalized = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        String[] raw = normalized.substring(1).split("/", -1);
        List<String> segments = new ArrayList<>(raw.length);
        for (String segment : raw) {
            if (segment.isBlank() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Files path is not normalized");
            }
            segments.add(segment);
        }
        return List.copyOf(segments);
    }

    public record Target(ObjectId parentId, String name) {
        public Target {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("target name must not be blank");
            }
            name = name.trim();
        }
    }
}

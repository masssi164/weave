package com.massimotter.weave.backend.files.domain;

import static com.massimotter.weave.backend.data.domain.CanonicalData.Dependency;
import static com.massimotter.weave.backend.data.domain.CanonicalData.Domain;
import static com.massimotter.weave.backend.data.domain.CanonicalData.Lifecycle;
import static com.massimotter.weave.backend.data.domain.CanonicalData.ModelVersion;
import static com.massimotter.weave.backend.data.domain.CanonicalData.ObjectId;
import static com.massimotter.weave.backend.data.domain.CanonicalData.Provenance;
import static com.massimotter.weave.backend.data.domain.CanonicalData.Revision;
import static com.massimotter.weave.backend.data.transfer.CanonicalTransfer.TransferObject;
import static com.massimotter.weave.backend.data.transfer.CanonicalTransfer.digestStrings;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Provider-independent canonical Files object.
 *
 * <p>Hierarchy, metadata, versions and content identity belong here. Blob keys,
 * filesystem paths, provider object IDs, DAV wire values and JPA entities do not.</p>
 */
public record CanonicalFile(
        ObjectId objectId,
        ModelVersion modelVersion,
        Revision revision,
        Lifecycle lifecycle,
        Provenance provenance,
        Kind kind,
        ObjectId parentId,
        String name,
        String mediaType,
        long byteSize,
        String contentDigest,
        Instant createdAt,
        Instant modifiedAt,
        List<Dependency> relatedDependencies) implements TransferObject {

    public enum Kind {
        FILE,
        COLLECTION
    }

    public CanonicalFile {
        objectId = requireFilesObjectId(objectId, "objectId");
        modelVersion = Objects.requireNonNull(modelVersion, "modelVersion must not be null");
        revision = Objects.requireNonNull(revision, "revision must not be null");
        lifecycle = Objects.requireNonNull(lifecycle, "lifecycle must not be null");
        provenance = Objects.requireNonNull(provenance, "provenance must not be null");
        kind = Objects.requireNonNull(kind, "kind must not be null");
        parentId = parentId == null ? null : requireFilesObjectId(parentId, "parentId");
        name = requireName(name);
        mediaType = optionalText(mediaType);
        contentDigest = optionalText(contentDigest);
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        modifiedAt = Objects.requireNonNull(modifiedAt, "modifiedAt must not be null");
        relatedDependencies = List.copyOf(Objects.requireNonNull(
                relatedDependencies, "relatedDependencies must not be null"));

        if (parentId != null && parentId.equals(objectId)) {
            throw new IllegalArgumentException("a Files object cannot be its own parent");
        }
        if (modifiedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("modifiedAt must not be before createdAt");
        }
        if (byteSize < 0) {
            throw new IllegalArgumentException("byteSize must not be negative");
        }
        if (kind == Kind.FILE) {
            if (contentDigest == null) {
                throw new IllegalArgumentException("a file requires a content digest");
            }
            if (mediaType == null) {
                throw new IllegalArgumentException("a file requires a media type");
            }
        } else {
            if (byteSize != 0) {
                throw new IllegalArgumentException("a collection must have byteSize 0");
            }
            if (contentDigest != null || mediaType != null) {
                throw new IllegalArgumentException(
                        "a collection must not carry file content metadata");
            }
        }

        for (Dependency dependency : relatedDependencies) {
            Objects.requireNonNull(dependency, "related dependency must not be null");
            if (!dependency.source().equals(objectId)) {
                throw new IllegalArgumentException(
                        "related dependency source must equal the canonical file object id");
            }
        }
    }

    public static CanonicalFile collection(
            ObjectId objectId,
            ModelVersion modelVersion,
            Revision revision,
            Lifecycle lifecycle,
            Provenance provenance,
            ObjectId parentId,
            String name,
            Instant createdAt,
            Instant modifiedAt,
            List<Dependency> relatedDependencies) {
        return new CanonicalFile(
                objectId,
                modelVersion,
                revision,
                lifecycle,
                provenance,
                Kind.COLLECTION,
                parentId,
                name,
                null,
                0,
                null,
                createdAt,
                modifiedAt,
                relatedDependencies);
    }

    public static CanonicalFile file(
            ObjectId objectId,
            ModelVersion modelVersion,
            Revision revision,
            Lifecycle lifecycle,
            Provenance provenance,
            ObjectId parentId,
            String name,
            String mediaType,
            long byteSize,
            String contentDigest,
            Instant createdAt,
            Instant modifiedAt,
            List<Dependency> relatedDependencies) {
        return new CanonicalFile(
                objectId,
                modelVersion,
                revision,
                lifecycle,
                provenance,
                Kind.FILE,
                parentId,
                name,
                mediaType,
                byteSize,
                contentDigest,
                createdAt,
                modifiedAt,
                relatedDependencies);
    }

    @Override
    public String canonicalDigest() {
        List<String> semanticValues = new ArrayList<>();
        semanticValues.add(kind.name());
        semanticValues.add(parentId == null ? "<root>" : parentId.value());
        semanticValues.add(name);
        semanticValues.add(mediaType == null ? "<none>" : mediaType);
        semanticValues.add(Long.toString(byteSize));
        semanticValues.add(contentDigest == null ? "<none>" : contentDigest);
        semanticValues.add(createdAt.toString());
        semanticValues.add(modifiedAt.toString());
        dependencies().stream()
                .map(dependency -> dependency.relation()
                        + ":" + dependency.target().domain().name()
                        + ":" + dependency.target().value())
                .sorted(Comparator.naturalOrder())
                .forEach(semanticValues::add);
        return digestStrings(semanticValues);
    }

    @Override
    public List<Dependency> dependencies() {
        List<Dependency> dependencies = new ArrayList<>();
        if (parentId != null) {
            dependencies.add(new Dependency(objectId, parentId, "files.parent"));
        }
        dependencies.addAll(relatedDependencies);
        return List.copyOf(dependencies);
    }

    private static ObjectId requireFilesObjectId(ObjectId value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.domain() != Domain.FILES) {
            throw new IllegalArgumentException(field + " must use the FILES domain");
        }
        return value;
    }

    private static String requireName(String value) {
        String name = optionalText(value);
        if (name == null) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (name.equals(".") || name.equals("..") || name.indexOf('/') >= 0 || name.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("name must be one normalized Files path segment");
        }
        return name;
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

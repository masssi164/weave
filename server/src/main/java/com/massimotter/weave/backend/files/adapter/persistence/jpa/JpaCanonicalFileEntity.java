package com.massimotter.weave.backend.files.adapter.persistence.jpa;

import static com.massimotter.weave.backend.data.domain.CanonicalData.Dependency;
import static com.massimotter.weave.backend.data.domain.CanonicalData.Domain;
import static com.massimotter.weave.backend.data.domain.CanonicalData.Lifecycle;
import static com.massimotter.weave.backend.data.domain.CanonicalData.ModelVersion;
import static com.massimotter.weave.backend.data.domain.CanonicalData.ObjectId;
import static com.massimotter.weave.backend.data.domain.CanonicalData.Provenance;
import static com.massimotter.weave.backend.data.domain.CanonicalData.ProvenanceKind;
import static com.massimotter.weave.backend.data.domain.CanonicalData.Revision;
import static com.massimotter.weave.backend.data.domain.CanonicalData.Scope;

import com.massimotter.weave.backend.files.domain.CanonicalFile;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "weave_file_object")
public class JpaCanonicalFileEntity {

    @EmbeddedId
    private JpaCanonicalFileId id;

    @Column(name = "canonical_model_version", nullable = false, length = 64)
    private String canonicalModelVersion;

    @Column(name = "object_revision", nullable = false)
    private long objectRevision;

    @Column(name = "lifecycle", nullable = false, length = 24)
    private String lifecycle;

    @Column(name = "provenance_kind", nullable = false, length = 24)
    private String provenanceKind;

    @Column(name = "provenance_source_ref", length = 512)
    private String provenanceSourceRef;

    @Column(name = "provenance_observed_at", nullable = false)
    private Instant provenanceObservedAt;

    @Column(name = "object_kind", nullable = false, length = 24)
    private String objectKind;

    @Column(name = "parent_object_id", length = 160)
    private String parentObjectId;

    @Column(name = "object_name", nullable = false, length = 512)
    private String objectName;

    @Column(name = "media_type", length = 255)
    private String mediaType;

    @Column(name = "byte_size", nullable = false)
    private long byteSize;

    @Column(name = "content_digest", length = 160)
    private String contentDigest;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "modified_at", nullable = false)
    private Instant modifiedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "weave_file_dependency",
            joinColumns = {
                @JoinColumn(name = "organization_ref", referencedColumnName = "organization_ref"),
                @JoinColumn(name = "context_key", referencedColumnName = "context_key"),
                @JoinColumn(name = "source_object_id", referencedColumnName = "object_id")
            })
    private Set<JpaFileDependency> relatedDependencies = new LinkedHashSet<>();

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected JpaCanonicalFileEntity() {
        // JPA
    }

    static JpaCanonicalFileEntity from(Scope scope, CanonicalFile file) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(file, "file must not be null");
        JpaCanonicalFileEntity entity = new JpaCanonicalFileEntity();
        entity.id = new JpaCanonicalFileId(
                scope.organizationRef(), contextKey(scope), file.objectId().value());
        entity.replaceValues(file);
        return entity;
    }

    CanonicalFile canonicalFile() {
        ObjectId sourceId = new ObjectId(Domain.FILES, id.objectId);
        List<Dependency> dependencies = relatedDependencies.stream()
                .map(dependency -> dependency.canonical(sourceId))
                .sorted(Comparator.comparing(Dependency::relation)
                        .thenComparing(dependency -> dependency.target().domain().name())
                        .thenComparing(dependency -> dependency.target().value()))
                .toList();
        return new CanonicalFile(
                sourceId,
                new ModelVersion(canonicalModelVersion),
                new Revision(objectRevision),
                Lifecycle.valueOf(lifecycle),
                new Provenance(
                        ProvenanceKind.valueOf(provenanceKind),
                        provenanceSourceRef,
                        provenanceObservedAt),
                CanonicalFile.Kind.valueOf(objectKind),
                parentObjectId == null ? null : new ObjectId(Domain.FILES, parentObjectId),
                objectName,
                mediaType,
                byteSize,
                contentDigest,
                createdAt,
                modifiedAt,
                dependencies);
    }

    Revision canonicalRevision() {
        return new Revision(objectRevision);
    }

    void replace(CanonicalFile file) {
        if (!id.objectId.equals(file.objectId().value())) {
            throw new IllegalArgumentException("replacement canonical Files object id changed");
        }
        replaceValues(file);
    }

    private void replaceValues(CanonicalFile file) {
        canonicalModelVersion = file.modelVersion().value();
        objectRevision = file.revision().value();
        lifecycle = file.lifecycle().name();
        provenanceKind = file.provenance().kind().name();
        provenanceSourceRef = file.provenance().sourceRef();
        provenanceObservedAt = file.provenance().observedAt();
        objectKind = file.kind().name();
        parentObjectId = file.parentId() == null ? null : file.parentId().value();
        objectName = file.name();
        mediaType = file.mediaType();
        byteSize = file.byteSize();
        contentDigest = file.contentDigest();
        createdAt = file.createdAt();
        modifiedAt = file.modifiedAt();
        relatedDependencies.clear();
        for (Dependency dependency : file.relatedDependencies()) {
            relatedDependencies.add(JpaFileDependency.from(dependency));
        }
    }

    static String contextKey(Scope scope) {
        return scope.contextRef() == null ? "" : scope.contextRef();
    }

    @Embeddable
    public static class JpaCanonicalFileId implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        @Column(name = "organization_ref", nullable = false, length = 160)
        private String organizationRef;

        @Column(name = "context_key", nullable = false, length = 160)
        private String contextKey;

        @Column(name = "object_id", nullable = false, length = 160)
        private String objectId;

        protected JpaCanonicalFileId() {
            // JPA
        }

        JpaCanonicalFileId(String organizationRef, String contextKey, String objectId) {
            this.organizationRef = Objects.requireNonNull(
                    organizationRef, "organizationRef must not be null");
            this.contextKey = Objects.requireNonNull(contextKey, "contextKey must not be null");
            this.objectId = Objects.requireNonNull(objectId, "objectId must not be null");
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof JpaCanonicalFileId that)) {
                return false;
            }
            return organizationRef.equals(that.organizationRef)
                    && contextKey.equals(that.contextKey)
                    && objectId.equals(that.objectId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(organizationRef, contextKey, objectId);
        }
    }

    @Embeddable
    public static class JpaFileDependency {

        @Column(name = "relation", nullable = false, length = 160)
        private String relation;

        @Column(name = "target_domain", nullable = false, length = 24)
        private String targetDomain;

        @Column(name = "target_object_id", nullable = false, length = 160)
        private String targetObjectId;

        protected JpaFileDependency() {
            // JPA
        }

        private JpaFileDependency(String relation, String targetDomain, String targetObjectId) {
            this.relation = Objects.requireNonNull(relation, "relation must not be null");
            this.targetDomain = Objects.requireNonNull(targetDomain, "targetDomain must not be null");
            this.targetObjectId = Objects.requireNonNull(
                    targetObjectId, "targetObjectId must not be null");
        }

        static JpaFileDependency from(Dependency dependency) {
            return new JpaFileDependency(
                    dependency.relation(),
                    dependency.target().domain().name(),
                    dependency.target().value());
        }

        Dependency canonical(ObjectId sourceId) {
            return new Dependency(
                    sourceId,
                    new ObjectId(Domain.valueOf(targetDomain), targetObjectId),
                    relation);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof JpaFileDependency that)) {
                return false;
            }
            return relation.equals(that.relation)
                    && targetDomain.equals(that.targetDomain)
                    && targetObjectId.equals(that.targetObjectId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(relation, targetDomain, targetObjectId);
        }
    }
}

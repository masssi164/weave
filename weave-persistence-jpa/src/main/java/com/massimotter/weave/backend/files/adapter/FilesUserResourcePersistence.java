package com.massimotter.weave.backend.files.adapter;

import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import com.massimotter.weave.backend.files.domain.FilesUserResource;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Entity
@Table(name = "weave_files_user_resources")
class FilesUserResourceJpaEntity {
    @EmbeddedId
    private FilesUserResourceId id;

    @Column(name = "binding_revision", nullable = false)
    private long bindingRevision;

    @Column(name = "space_ref", nullable = false, length = 255)
    private String spaceRef;

    @Column(name = "parent_file_id", nullable = false, length = 255)
    private String parentFileId;

    @Column(name = "canonical_path", nullable = false, length = 2048)
    private String path;

    @Column(name = "active_path_key", length = 2048)
    private String activePathKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "object_kind", nullable = false, length = 32)
    private Kind kind;

    @Column(name = "owner_principal_ref", nullable = false, length = 255)
    private String ownerPrincipalRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_state", nullable = false, length = 32)
    private FilesUserResource.State state;

    @Column(name = "created_at_utc", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "modified_at_utc", nullable = false)
    private OffsetDateTime modifiedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected FilesUserResourceJpaEntity() {}

    static FilesUserResourceJpaEntity create(FilesUserResourceId id) {
        FilesUserResourceJpaEntity entity = new FilesUserResourceJpaEntity();
        entity.id = id;
        return entity;
    }

    void observe(FilesUserResource resource) {
        if (createdAt != null && !createdAt.toInstant().equals(resource.createdAt())) {
            throw new IllegalArgumentException("Files resource creation time cannot change");
        }
        bindingRevision = resource.bindingRevision();
        spaceRef = resource.spaceRef();
        parentFileId = resource.parentFileId();
        path = resource.path();
        activePathKey = resource.state() == FilesUserResource.State.ACTIVE ? path : null;
        kind = resource.kind();
        ownerPrincipalRef = resource.ownerPrincipalRef();
        state = resource.state();
        createdAt = resource.createdAt().atOffset(ZoneOffset.UTC);
        modifiedAt = resource.modifiedAt().atOffset(ZoneOffset.UTC);
    }

    FilesUserResource toDomain() {
        return new FilesUserResource(id.organizationRef(), id.fileId(), bindingRevision,
                spaceRef, parentFileId, path, kind, ownerPrincipalRef, state,
                createdAt.toInstant(), modifiedAt.toInstant());
    }
}

@Embeddable
class FilesUserResourceId implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    @Column(name = "organization_ref", nullable = false, length = 255)
    private String organizationRef;

    @Column(name = "file_id", nullable = false, length = 255)
    private String fileId;

    protected FilesUserResourceId() {}

    FilesUserResourceId(String organizationRef, String fileId) {
        this.organizationRef = Objects.requireNonNull(organizationRef);
        this.fileId = Objects.requireNonNull(fileId);
    }

    String organizationRef() { return organizationRef; }
    String fileId() { return fileId; }

    @Override public boolean equals(Object candidate) {
        return candidate instanceof FilesUserResourceId other
                && organizationRef.equals(other.organizationRef) && fileId.equals(other.fileId);
    }

    @Override public int hashCode() { return Objects.hash(organizationRef, fileId); }
}

interface FilesUserResourceJpaRepository extends JpaRepository<FilesUserResourceJpaEntity, FilesUserResourceId> {
    @Query("""
            select resource from FilesUserResourceJpaEntity resource
            where resource.id.organizationRef = :organizationRef
              and resource.spaceRef = :spaceRef
              and resource.ownerPrincipalRef = :ownerPrincipalRef
              and resource.state = :state
              and resource.id.fileId > :afterFileId
            order by resource.id.fileId
            """)
    List<FilesUserResourceJpaEntity> activeInSpace(
            @Param("organizationRef") String organizationRef,
            @Param("spaceRef") String spaceRef,
            @Param("ownerPrincipalRef") String ownerPrincipalRef,
            @Param("state") FilesUserResource.State state,
            @Param("afterFileId") String afterFileId,
            Pageable page);

    Optional<FilesUserResourceJpaEntity> findByIdOrganizationRefAndBindingRevisionAndActivePathKey(
            String organizationRef, long bindingRevision, String path);

    List<FilesUserResourceJpaEntity> findByIdOrganizationRefAndParentFileIdAndStateOrderByPath(
            String organizationRef, String parentFileId, FilesUserResource.State state);
}

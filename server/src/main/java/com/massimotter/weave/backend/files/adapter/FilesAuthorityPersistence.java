package com.massimotter.weave.backend.files.adapter;

import com.massimotter.weave.backend.files.domain.FilesAuthority.CanonicalFileRecord;
import com.massimotter.weave.backend.files.domain.FilesAuthority.FileLockRecord;
import com.massimotter.weave.backend.files.domain.FilesAuthority.Lifecycle;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileId;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileObject;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileVersion;
import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Entity
@Table(name = "weave_files_objects")
class FileObjectJpaEntity {

    @EmbeddedId
    private CanonicalFileId id;

    @Column(name = "canonical_path", nullable = false, length = 2048)
    private String canonicalPath;

    @Enumerated(EnumType.STRING)
    @Column(name = "object_kind", nullable = false, length = 32)
    private Kind kind;

    @Column(name = "byte_size", nullable = false)
    private long byteSize;

    @Column(name = "media_type", length = 255)
    private String mediaType;

    @Column(name = "modified_at_utc")
    private OffsetDateTime modifiedAt;

    @Column(name = "hidden", nullable = false)
    private boolean hidden;

    @Column(name = "version_token", length = 1024)
    private String versionToken;

    @Column(name = "content_digest", length = 71)
    private String contentDigest;

    @Column(name = "provider_binding_revision", nullable = false)
    private long providerBindingRevision;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_state", nullable = false, length = 32)
    private Lifecycle lifecycle;

    @Column(name = "observed_at_utc", nullable = false)
    private OffsetDateTime observedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected FileObjectJpaEntity() {
    }

    static FileObjectJpaEntity create(CanonicalFileId id) {
        FileObjectJpaEntity entity = new FileObjectJpaEntity();
        entity.id = id;
        return entity;
    }

    void observe(CanonicalFileRecord record) {
        if (observedAt != null
                && record.observedAt().isBefore(observedAt.toInstant())) {
            throw new IllegalArgumentException(
                    "canonical file observation cannot move backwards");
        }
        canonicalPath = record.object().path().value();
        kind = record.object().kind();
        byteSize = record.object().size();
        mediaType = record.object().mediaType();
        modifiedAt = FilesPersistenceTime.utc(record.object().modifiedAt());
        hidden = record.object().hidden();
        versionToken = record.version().value();
        contentDigest = record.contentDigest();
        providerBindingRevision = record.providerBindingRevision();
        lifecycle = record.lifecycle();
        observedAt = FilesPersistenceTime.utc(record.observedAt());
    }

    boolean move(FilePath expected, FilePath destination, Instant movedAt) {
        if (lifecycle != Lifecycle.ACTIVE
                || !Objects.equals(canonicalPath, expected.value())) {
            return false;
        }
        canonicalPath = destination.value();
        modifiedAt = FilesPersistenceTime.utc(movedAt);
        observedAt = FilesPersistenceTime.utc(movedAt);
        return true;
    }

    CanonicalFileRecord toDomain() {
        FileObject object = new FileObject(
                new FileId(id.fileId()),
                new FilePath(canonicalPath),
                kind,
                byteSize,
                mediaType,
                FilesPersistenceTime.instant(modifiedAt),
                hidden);
        return new CanonicalFileRecord(
                id.organizationRef(),
                id.spaceRef(),
                object,
                new FileVersion(versionToken),
                contentDigest,
                providerBindingRevision,
                lifecycle,
                observedAt.toInstant());
    }
}

@Embeddable
class CanonicalFileId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "organization_ref", nullable = false, length = 255)
    private String organizationRef;

    @Column(name = "space_ref", nullable = false, length = 255)
    private String spaceRef;

    @Column(name = "file_id", nullable = false, length = 255)
    private String fileId;

    protected CanonicalFileId() {
    }

    CanonicalFileId(String organizationRef, String spaceRef, String fileId) {
        this.organizationRef = Objects.requireNonNull(organizationRef, "organizationRef");
        this.spaceRef = Objects.requireNonNull(spaceRef, "spaceRef");
        this.fileId = Objects.requireNonNull(fileId, "fileId");
    }

    static CanonicalFileId from(CanonicalFileRecord record) {
        return new CanonicalFileId(
                record.organizationRef(),
                record.spaceRef(),
                record.object().id().value());
    }

    String organizationRef() {
        return organizationRef;
    }

    String spaceRef() {
        return spaceRef;
    }

    String fileId() {
        return fileId;
    }

    @Override
    public boolean equals(Object candidate) {
        return this == candidate
                || candidate instanceof CanonicalFileId other
                && Objects.equals(organizationRef, other.organizationRef)
                && Objects.equals(spaceRef, other.spaceRef)
                && Objects.equals(fileId, other.fileId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(organizationRef, spaceRef, fileId);
    }
}

interface FileObjectJpaRepository
        extends JpaRepository<FileObjectJpaEntity, CanonicalFileId> {

    Optional<FileObjectJpaEntity>
            findByIdOrganizationRefAndIdSpaceRefAndCanonicalPath(
                    String organizationRef,
                    String spaceRef,
                    String canonicalPath);
}

@Entity
@Table(name = "weave_file_locks")
class FileLockJpaEntity {

    @EmbeddedId
    private FileLockId id;

    @Column(name = "token_digest", nullable = false, length = 71)
    private String tokenDigest;

    @Column(name = "owner_ref", nullable = false, length = 255)
    private String ownerRef;

    @Column(name = "fence", nullable = false)
    private long fence;

    @Column(name = "expires_at_utc", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "created_at_utc", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "released_at_utc")
    private OffsetDateTime releasedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected FileLockJpaEntity() {
    }

    static FileLockJpaEntity create(
            FileLockId id,
            FileLockRecord requested,
            long fence) {
        FileLockJpaEntity entity = new FileLockJpaEntity();
        entity.id = id;
        entity.apply(requested, fence);
        return entity;
    }

    FileLockJpaEntity reacquire(FileLockRecord requested) {
        apply(requested, fence + 1);
        return this;
    }

    boolean activeAt(Instant now) {
        return releasedAt == null && expiresAt.toInstant().isAfter(now);
    }

    boolean ownedAndActive(
            String requestedTokenDigest,
            String requestedOwnerRef,
            Instant now) {
        return activeAt(now)
                && Objects.equals(tokenDigest, requestedTokenDigest)
                && Objects.equals(ownerRef, requestedOwnerRef);
    }

    boolean release(
            String requestedTokenDigest,
            String requestedOwnerRef,
            Instant now) {
        if (!ownedAndActive(requestedTokenDigest, requestedOwnerRef, now)) {
            return false;
        }
        releasedAt = FilesPersistenceTime.utc(now);
        return true;
    }

    FileLockJpaEntity rekey(FilePath destination) {
        FileLockJpaEntity moved = new FileLockJpaEntity();
        moved.id = new FileLockId(
                id.organizationRef(),
                id.spaceRef(),
                destination.value());
        moved.tokenDigest = tokenDigest;
        moved.ownerRef = ownerRef;
        moved.fence = fence;
        moved.expiresAt = expiresAt;
        moved.createdAt = createdAt;
        return moved;
    }

    FileLockRecord toDomain() {
        return new FileLockRecord(
                id.organizationRef(),
                id.spaceRef(),
                new FilePath(id.canonicalPath()),
                tokenDigest,
                ownerRef,
                fence,
                expiresAt.toInstant(),
                createdAt.toInstant());
    }

    private void apply(FileLockRecord requested, long nextFence) {
        tokenDigest = requested.tokenDigest();
        ownerRef = requested.ownerRef();
        fence = nextFence;
        expiresAt = FilesPersistenceTime.utc(requested.expiresAt());
        createdAt = FilesPersistenceTime.utc(requested.createdAt());
        releasedAt = null;
    }
}

@Embeddable
class FileLockId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "organization_ref", nullable = false, length = 255)
    private String organizationRef;

    @Column(name = "space_ref", nullable = false, length = 255)
    private String spaceRef;

    @Column(name = "canonical_path", nullable = false, length = 2048)
    private String canonicalPath;

    protected FileLockId() {
    }

    FileLockId(String organizationRef, String spaceRef, String canonicalPath) {
        this.organizationRef = Objects.requireNonNull(organizationRef, "organizationRef");
        this.spaceRef = Objects.requireNonNull(spaceRef, "spaceRef");
        this.canonicalPath = Objects.requireNonNull(canonicalPath, "canonicalPath");
    }

    static FileLockId from(FileLockRecord record) {
        return new FileLockId(
                record.organizationRef(),
                record.spaceRef(),
                record.path().value());
    }

    String organizationRef() {
        return organizationRef;
    }

    String spaceRef() {
        return spaceRef;
    }

    String canonicalPath() {
        return canonicalPath;
    }

    @Override
    public boolean equals(Object candidate) {
        return this == candidate
                || candidate instanceof FileLockId other
                && Objects.equals(organizationRef, other.organizationRef)
                && Objects.equals(spaceRef, other.spaceRef)
                && Objects.equals(canonicalPath, other.canonicalPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(organizationRef, spaceRef, canonicalPath);
    }
}

interface FileLockJpaRepository
        extends JpaRepository<FileLockJpaEntity, FileLockId> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select lock from FileLockJpaEntity lock where lock.id = :id")
    Optional<FileLockJpaEntity> lockById(@Param("id") FileLockId id);

    List<FileLockJpaEntity>
            findByIdOrganizationRefAndIdSpaceRefAndReleasedAtIsNullAndExpiresAtAfterOrderByIdCanonicalPath(
                    String organizationRef,
                    String spaceRef,
                    OffsetDateTime now);
}

final class FilesPersistenceTime {

    private FilesPersistenceTime() {
    }

    static OffsetDateTime utc(Instant value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}

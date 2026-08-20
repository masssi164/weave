package com.massimotter.weave.backend.files.adapter;

import com.massimotter.weave.backend.files.domain.FilesAuthority.Lifecycle;
import com.massimotter.weave.backend.files.domain.FilesChangeStream.ChangeKind;
import com.massimotter.weave.backend.files.domain.FilesChangeStream.FileChange;
import com.massimotter.weave.backend.files.domain.FilesChangeStream.StreamHead;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileId;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileVersion;
import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import com.massimotter.weave.backend.files.port.FilesMutationPlan;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.Draft;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.EntityTagCondition;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.ExpectedPresence;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.Fence;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.FenceRole;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.OperationKind;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.Sealed;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.Target;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Embeddable
class FilesScopeId implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    @Column(name = "organization_ref", nullable = false, length = 255)
    private String organizationRef;

    @Column(name = "space_ref", nullable = false, length = 255)
    private String spaceRef;

    protected FilesScopeId() {
    }

    FilesScopeId(String organizationRef, String spaceRef) {
        this.organizationRef = Objects.requireNonNull(organizationRef, "organizationRef");
        this.spaceRef = Objects.requireNonNull(spaceRef, "spaceRef");
    }

    String organizationRef() {
        return organizationRef;
    }

    String spaceRef() {
        return spaceRef;
    }

    @Override public boolean equals(Object candidate) {
        return this == candidate || candidate instanceof FilesScopeId other
                && Objects.equals(organizationRef, other.organizationRef)
                && Objects.equals(spaceRef, other.spaceRef);
    }

    @Override public int hashCode() {
        return Objects.hash(organizationRef, spaceRef);
    }
}

@Entity
@Table(name = "weave_files_stream_heads")
class FilesStreamHeadJpaEntity {

    @EmbeddedId
    private FilesScopeId id;

    @Column(name = "latest_revision", nullable = false)
    private long latestRevision;

    @Column(name = "reset_required_floor", nullable = false)
    private long resetRequiredFloor;

    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    @Column(name = "updated_at_utc", nullable = false)
    private OffsetDateTime updatedAt;

    protected FilesStreamHeadJpaEntity() {
    }

    static FilesStreamHeadJpaEntity provision(String organizationRef, String spaceRef, Instant now) {
        FilesStreamHeadJpaEntity entity = new FilesStreamHeadJpaEntity();
        entity.id = new FilesScopeId(organizationRef, spaceRef);
        entity.latestRevision = 0;
        entity.resetRequiredFloor = 0;
        entity.updatedAt = FilesPersistenceTime.utc(now);
        return entity;
    }

    RevisionRange reserve(int targetCount, Instant committedAt) {
        if (targetCount < 1) {
            throw new IllegalArgumentException("targetCount must be positive");
        }
        long start = Math.addExact(latestRevision, 1);
        long end = Math.addExact(latestRevision, targetCount);
        latestRevision = end;
        updatedAt = FilesPersistenceTime.utc(committedAt);
        return new RevisionRange(start, end);
    }

    long latestRevision() {
        return latestRevision;
    }

    long resetRequiredFloor() {
        return resetRequiredFloor;
    }

    StreamHead toStreamHead() {
        return new StreamHead(
                id.organizationRef(),
                id.spaceRef(),
                latestRevision,
                resetRequiredFloor,
                FilesPersistenceTime.instant(updatedAt));
    }

    record RevisionRange(long start, long end) {
    }
}

interface FilesStreamHeadJpaRepository extends JpaRepository<FilesStreamHeadJpaEntity, FilesScopeId> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select head from FilesStreamHeadJpaEntity head where head.id = :id")
    Optional<FilesStreamHeadJpaEntity> lockById(@Param("id") FilesScopeId id);
}

@Entity
@Table(name = "weave_files_mutation_plans")
class FilesMutationPlanJpaEntity {

    @Id
    @Column(name = "operation_ref", nullable = false, length = 255, updatable = false)
    private String operationRef;

    @Column(name = "organization_ref", nullable = false, length = 255, updatable = false)
    private String organizationRef;

    @Column(name = "space_ref", nullable = false, length = 255, updatable = false)
    private String spaceRef;

    @Column(name = "plan_version", nullable = false, length = 64, updatable = false)
    private String planVersion;

    @Column(name = "canonical_arguments_digest", nullable = false, length = 71, updatable = false)
    private String canonicalArgumentsDigest;

    @Column(name = "operation_kind", nullable = false, length = 32, updatable = false)
    private String operationKind;

    @Column(name = "provider_binding_revision", nullable = false, updatable = false)
    private long providerBindingRevision;

    @Column(name = "if_match_condition", nullable = false, length = 4096, updatable = false)
    private String ifMatchCondition;

    @Column(name = "if_none_match_condition", nullable = false, length = 4096, updatable = false)
    private String ifNoneMatchCondition;

    @Column(name = "destination_must_remain_absent", nullable = false, updatable = false)
    private boolean destinationMustRemainAbsent;

    @Column(name = "plan_state", nullable = false, length = 16)
    private String planState;

    @Column(name = "target_count", nullable = false, updatable = false)
    private int targetCount;

    @Column(name = "targets_digest", nullable = false, length = 71, updatable = false)
    private String targetsDigest;

    @Column(name = "fence_count", nullable = false, updatable = false)
    private int fenceCount;

    @Column(name = "fences_digest", nullable = false, length = 71, updatable = false)
    private String fencesDigest;

    @Column(name = "sealed_at_utc")
    private OffsetDateTime sealedAt;

    protected FilesMutationPlanJpaEntity() {
    }

    static FilesMutationPlanJpaEntity open(Sealed plan) {
        FilesMutationPlanJpaEntity entity = new FilesMutationPlanJpaEntity();
        entity.operationRef = plan.operationRef();
        entity.organizationRef = plan.organizationRef();
        entity.spaceRef = plan.spaceRef();
        entity.planVersion = FilesMutationPlan.VERSION;
        entity.canonicalArgumentsDigest = plan.canonicalArgumentsDigest();
        entity.operationKind = plan.operationKind().name();
        entity.providerBindingRevision = plan.providerBindingRevision();
        entity.ifMatchCondition = plan.ifMatchCondition().canonicalValue();
        entity.ifNoneMatchCondition = plan.ifNoneMatchCondition().canonicalValue();
        entity.destinationMustRemainAbsent = plan.destinationMustRemainAbsent();
        entity.planState = "OPEN";
        entity.targetCount = plan.targetCount();
        entity.targetsDigest = plan.targetsDigest();
        entity.fenceCount = plan.fenceCount();
        entity.fencesDigest = plan.fencesDigest();
        return entity;
    }

    void seal(Instant instant) {
        if (!"OPEN".equals(planState)) {
            throw new IllegalStateException("Files mutation plan is not open");
        }
        planState = "SEALED";
        sealedAt = FilesPersistenceTime.utc(instant);
    }

    Sealed toSealed(List<Target> targets, List<Fence> fences) {
        if (!"SEALED".equals(planState)
                || sealedAt == null
                || targets.size() != targetCount
                || fences.size() != fenceCount) {
            throw new IllegalStateException("stored Files mutation plan is incomplete");
        }
        Draft draft = new Draft(
                operationRef,
                organizationRef,
                spaceRef,
                canonicalArgumentsDigest,
                OperationKind.valueOf(operationKind),
                providerBindingRevision,
                EntityTagCondition.parseCanonical(ifMatchCondition),
                EntityTagCondition.parseCanonical(ifNoneMatchCondition),
                destinationMustRemainAbsent,
                targets,
                fences);
        return draft.seal(targetsDigest, fencesDigest, sealedAt.toInstant());
    }
}

interface FilesMutationPlanJpaRepository extends JpaRepository<FilesMutationPlanJpaEntity, String> {
    boolean existsByOrganizationRefAndSpaceRef(
            String organizationRef,
            String spaceRef);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select plan from FilesMutationPlanJpaEntity plan where plan.operationRef = :operationRef")
    Optional<FilesMutationPlanJpaEntity> lockByOperationRef(@Param("operationRef") String operationRef);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select plan from FilesMutationPlanJpaEntity plan where plan.operationRef = :operationRef")
    Optional<FilesMutationPlanJpaEntity> lockForCleanupByOperationRef(
            @Param("operationRef") String operationRef);

    @Query("""
            select plan.operationRef
              from FilesMutationPlanJpaEntity plan
             where plan.operationKind = :operationKind
               and (:afterOperationRef is null or plan.operationRef > :afterOperationRef)
               and exists (
                    select intent.operationRef
                      from OperationIntentJpaEntity intent
                     where intent.operationRef = plan.operationRef
                       and intent.state in :intentStates)
             order by plan.operationRef
            """)
    List<String> findRecoverableOperationRefs(
            @Param("operationKind") String operationKind,
            @Param("intentStates") List<String> intentStates,
            @Param("afterOperationRef") String afterOperationRef,
            Pageable pageable);
}

@Embeddable
class FilesMutationFenceId implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    @Column(name = "operation_ref", nullable = false, length = 255)
    private String operationRef;

    @Column(name = "fence_ordinal", nullable = false)
    private int fenceOrdinal;

    protected FilesMutationFenceId() {
    }

    FilesMutationFenceId(String operationRef, int fenceOrdinal) {
        this.operationRef = Objects.requireNonNull(operationRef, "operationRef");
        this.fenceOrdinal = fenceOrdinal;
    }

    int fenceOrdinal() {
        return fenceOrdinal;
    }

    @Override public boolean equals(Object candidate) {
        return this == candidate || candidate instanceof FilesMutationFenceId other
                && fenceOrdinal == other.fenceOrdinal
                && Objects.equals(operationRef, other.operationRef);
    }

    @Override public int hashCode() {
        return Objects.hash(operationRef, fenceOrdinal);
    }
}

@Entity
@Table(name = "weave_files_mutation_fences")
@SuppressWarnings("checkstyle:MemberName")
class FilesMutationFenceJpaEntity {

    @EmbeddedId private FilesMutationFenceId id;
    @Column(name = "fence_version", nullable = false, length = 64, updatable = false) private String fenceVersion;
    @Column(name = "fence_role", nullable = false, length = 32, updatable = false) private String fenceRole;
    @Column(name = "canonical_path", nullable = false, length = 2048, updatable = false) private String canonicalPath;
    @Column(name = "expected_presence", nullable = false, length = 16, updatable = false) private String expectedPresence;
    @Column(name = "expected_file_ref", length = 255, updatable = false) private String expectedFileRef;
    @Column(name = "expected_object_kind", length = 32, updatable = false) private String expectedObjectKind;
    @Column(name = "expected_lifecycle_state", length = 32, updatable = false) private String expectedLifecycleState;
    @Column(name = "expected_row_version", updatable = false) private Long expectedRowVersion;
    @Column(name = "expected_strong_etag", length = 1024, updatable = false) private String expectedStrongEtag;
    @Column(name = "expected_subtree_digest", length = 71, updatable = false) private String expectedSubtreeDigest;
    @Column(name = "snapshot_digest", nullable = false, length = 71, updatable = false) private String snapshotDigest;

    protected FilesMutationFenceJpaEntity() {
    }

    static FilesMutationFenceJpaEntity create(String operationRef, Fence fence) {
        FilesMutationFenceJpaEntity entity = new FilesMutationFenceJpaEntity();
        entity.id = new FilesMutationFenceId(operationRef, fence.fenceOrdinal());
        entity.fenceVersion = fence.fenceVersion();
        entity.fenceRole = fence.fenceRole().name();
        entity.canonicalPath = fence.canonicalPath();
        entity.expectedPresence = fence.expectedPresence().name();
        entity.expectedFileRef = fence.expectedFileRef();
        entity.expectedObjectKind = fence.expectedObjectKind() == null ? null : fence.expectedObjectKind().name();
        entity.expectedLifecycleState = fence.expectedLifecycleState() == null
                ? null
                : fence.expectedLifecycleState().name();
        entity.expectedRowVersion = fence.expectedRowVersion();
        entity.expectedStrongEtag = fence.expectedStrongEtag();
        entity.expectedSubtreeDigest = fence.expectedSubtreeDigest();
        entity.snapshotDigest = fence.snapshotDigest();
        return entity;
    }

    Fence toFence() {
        return new Fence(
                id.fenceOrdinal(),
                FenceRole.valueOf(fenceRole),
                canonicalPath,
                ExpectedPresence.valueOf(expectedPresence),
                expectedFileRef,
                expectedObjectKind == null ? null : Kind.valueOf(expectedObjectKind),
                expectedLifecycleState == null ? null : Lifecycle.valueOf(expectedLifecycleState),
                expectedRowVersion,
                expectedStrongEtag,
                expectedSubtreeDigest,
                snapshotDigest);
    }
}

interface FilesMutationFenceJpaRepository
        extends JpaRepository<FilesMutationFenceJpaEntity, FilesMutationFenceId> {
    List<FilesMutationFenceJpaEntity> findByIdOperationRefOrderByIdFenceOrdinal(String operationRef);
}

@Embeddable
class FilesMutationTargetId implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    @Column(name = "operation_ref", nullable = false, length = 255)
    private String operationRef;

    @Column(name = "target_ordinal", nullable = false)
    private int targetOrdinal;

    protected FilesMutationTargetId() {
    }

    FilesMutationTargetId(String operationRef, int targetOrdinal) {
        this.operationRef = Objects.requireNonNull(operationRef, "operationRef");
        this.targetOrdinal = targetOrdinal;
    }

    String operationRef() { return operationRef; }
    int targetOrdinal() { return targetOrdinal; }

    @Override public boolean equals(Object candidate) {
        return this == candidate || candidate instanceof FilesMutationTargetId other
                && targetOrdinal == other.targetOrdinal
                && Objects.equals(operationRef, other.operationRef);
    }

    @Override public int hashCode() { return Objects.hash(operationRef, targetOrdinal); }
}

@Entity
@Table(name = "weave_files_mutation_targets")
@SuppressWarnings("checkstyle:MemberName")
class FilesMutationTargetJpaEntity {

    @EmbeddedId private FilesMutationTargetId id;
    @Column(name = "target_version", nullable = false, length = 64, updatable = false) private String targetVersion;
    @Column(name = "change_kind", nullable = false, length = 32, updatable = false) private String changeKind;
    @Column(name = "source_file_ref", length = 255, updatable = false) private String sourceFileRef;
    @Column(name = "target_file_ref", nullable = false, length = 255, updatable = false) private String targetFileRef;
    @Column(name = "source_path", length = 2048, updatable = false) private String sourcePath;
    @Column(name = "target_path", length = 2048, updatable = false) private String targetPath;
    @Column(name = "object_kind", nullable = false, length = 32, updatable = false) private String objectKind;
    @Column(name = "result_lifecycle_state", nullable = false, length = 32, updatable = false) private String resultLifecycleState;
    @Column(name = "source_read_blob_binding", length = 1024, updatable = false) private String sourceReadBlobBinding;
    @Column(name = "source_size", updatable = false) private Long sourceSize;
    @Column(name = "source_media_type", length = 255, updatable = false) private String sourceMediaType;
    @Column(name = "source_content_digest", length = 71, updatable = false) private String sourceContentDigest;
    @Column(name = "source_file_version", length = 1024, updatable = false) private String sourceFileVersion;
    @Column(name = "source_strong_etag", length = 1024, updatable = false) private String sourceStrongEtag;
    @Column(name = "source_modified_at_utc", updatable = false) private OffsetDateTime sourceModifiedAt;
    @Column(name = "source_hidden", updatable = false) private Boolean sourceHidden;
    @Column(name = "source_observed_at_utc", updatable = false) private OffsetDateTime sourceObservedAt;
    @Column(name = "source_lifecycle_state", length = 32, updatable = false) private String sourceLifecycleState;
    @Column(name = "result_blob_binding", length = 1024, updatable = false) private String resultBlobBinding;
    @Column(name = "result_size", nullable = false, updatable = false) private long resultSize;
    @Column(name = "result_media_type", length = 255, updatable = false) private String resultMediaType;
    @Column(name = "result_content_digest", length = 71, updatable = false) private String resultContentDigest;
    @Column(name = "result_file_version", length = 1024, updatable = false) private String resultFileVersion;
    @Column(name = "result_strong_etag", length = 1024, updatable = false) private String resultStrongEtag;
    @Column(name = "result_modified_at_utc", nullable = false, updatable = false) private OffsetDateTime resultModifiedAt;
    @Column(name = "result_hidden", nullable = false, updatable = false) private boolean resultHidden;
    @Column(name = "result_observed_at_utc", nullable = false, updatable = false) private OffsetDateTime resultObservedAt;

    protected FilesMutationTargetJpaEntity() {
    }

    static FilesMutationTargetJpaEntity create(String operationRef, Target target) {
        FilesMutationTargetJpaEntity entity = new FilesMutationTargetJpaEntity();
        entity.id = new FilesMutationTargetId(operationRef, target.targetOrdinal());
        entity.targetVersion = target.targetVersion();
        entity.changeKind = target.changeKind().name();
        entity.sourceFileRef = target.sourceFileRef();
        entity.targetFileRef = target.targetFileRef();
        entity.sourcePath = target.sourcePath();
        entity.targetPath = target.targetPath();
        entity.objectKind = target.objectKind().name();
        entity.resultLifecycleState = target.resultLifecycleState().name();
        entity.sourceReadBlobBinding = target.sourceReadBlobBinding();
        entity.sourceSize = target.sourceSize();
        entity.sourceMediaType = target.sourceMediaType();
        entity.sourceContentDigest = target.sourceContentDigest();
        entity.sourceFileVersion = target.sourceFileVersion();
        entity.sourceStrongEtag = target.sourceStrongEtag();
        entity.sourceModifiedAt = FilesPersistenceTime.utc(target.sourceModifiedAt());
        entity.sourceHidden = target.sourceHidden();
        entity.sourceObservedAt = FilesPersistenceTime.utc(target.sourceObservedAt());
        entity.sourceLifecycleState = target.sourceLifecycleState() == null ? null : target.sourceLifecycleState().name();
        entity.resultBlobBinding = target.resultBlobBinding();
        entity.resultSize = target.resultSize();
        entity.resultMediaType = target.resultMediaType();
        entity.resultContentDigest = target.resultContentDigest();
        entity.resultFileVersion = target.resultFileVersion();
        entity.resultStrongEtag = target.resultStrongEtag();
        entity.resultModifiedAt = FilesPersistenceTime.utc(target.resultModifiedAt());
        entity.resultHidden = target.resultHidden();
        entity.resultObservedAt = FilesPersistenceTime.utc(target.resultObservedAt());
        return entity;
    }

    Target toTarget() {
        return new Target(
                id.targetOrdinal(),
                ChangeKind.valueOf(changeKind),
                sourceFileRef,
                targetFileRef,
                sourcePath,
                targetPath,
                Kind.valueOf(objectKind),
                Lifecycle.valueOf(resultLifecycleState),
                sourceReadBlobBinding,
                sourceSize,
                sourceMediaType,
                sourceContentDigest,
                sourceFileVersion,
                sourceStrongEtag,
                FilesPersistenceTime.instant(sourceModifiedAt),
                sourceHidden,
                FilesPersistenceTime.instant(sourceObservedAt),
                sourceLifecycleState == null ? null : Lifecycle.valueOf(sourceLifecycleState),
                resultBlobBinding,
                resultSize,
                resultMediaType,
                resultContentDigest,
                resultFileVersion,
                resultStrongEtag,
                FilesPersistenceTime.instant(resultModifiedAt),
                resultHidden,
                FilesPersistenceTime.instant(resultObservedAt));
    }

    String sourceReadBlobBinding() {
        return sourceReadBlobBinding;
    }

    String resultBlobBinding() {
        return resultBlobBinding;
    }

    Kind objectKind() {
        return Kind.valueOf(objectKind);
    }
}

interface FilesMutationTargetJpaRepository
        extends JpaRepository<FilesMutationTargetJpaEntity, FilesMutationTargetId> {
    List<FilesMutationTargetJpaEntity> findByIdOperationRefOrderByIdTargetOrdinal(String operationRef);

    @Query("""
            select target
            from FilesMutationTargetJpaEntity target,
                 FilesMutationPlanJpaEntity plan,
                 OperationIntentJpaEntity intent
            where target.id.operationRef = plan.operationRef
              and plan.operationRef = intent.operationRef
              and plan.organizationRef = :organizationRef
              and plan.spaceRef = :spaceRef
              and plan.planState = 'SEALED'
              and intent.organizationRef = :organizationRef
              and intent.domain = 'files'
              and intent.state in :intentStates
              and (target.sourceReadBlobBinding is not null
                   or target.resultBlobBinding is not null)
            order by target.id.operationRef, target.id.targetOrdinal
            """)
    List<FilesMutationTargetJpaEntity> findProtectedTargets(
            @Param("organizationRef") String organizationRef,
            @Param("spaceRef") String spaceRef,
            @Param("intentStates") List<String> intentStates);

    @Query("""
            select target
            from FilesMutationTargetJpaEntity target,
                 FilesMutationPlanJpaEntity plan,
                 OperationIntentJpaEntity intent
            where target.id.operationRef = plan.operationRef
              and plan.operationRef = intent.operationRef
              and plan.organizationRef = :organizationRef
              and plan.spaceRef = :spaceRef
              and plan.planState = 'SEALED'
              and intent.organizationRef = :organizationRef
              and intent.domain = 'files'
              and intent.state in :intentStates
              and (target.sourceReadBlobBinding is not null
                   or target.resultBlobBinding is not null)
              and exists (
                  select missingTarget.id.targetOrdinal
                  from FilesMutationTargetJpaEntity missingTarget
                  where missingTarget.id.operationRef = plan.operationRef
                    and missingTarget.objectKind = 'FILE'
                    and ((missingTarget.sourceReadBlobBinding is not null
                          and not exists (
                              select disposition.id.bindingDigest
                              from FilesBlobCleanupDispositionJpaEntity disposition
                              where disposition.id.operationRef = plan.operationRef
                                and disposition.privateBlobBinding = missingTarget.sourceReadBlobBinding))
                         or (missingTarget.resultBlobBinding is not null
                          and not exists (
                              select disposition.id.bindingDigest
                              from FilesBlobCleanupDispositionJpaEntity disposition
                              where disposition.id.operationRef = plan.operationRef
                                and disposition.privateBlobBinding = missingTarget.resultBlobBinding)))
              )
            order by target.id.operationRef, target.id.targetOrdinal
            """)
    List<FilesMutationTargetJpaEntity> findTerminalFailureTargetsWithIncompleteCleanup(
            @Param("organizationRef") String organizationRef,
            @Param("spaceRef") String spaceRef,
            @Param("intentStates") List<String> intentStates);
}

@Embeddable
class FilesBlobCleanupDispositionId implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    @Column(name = "operation_ref", nullable = false, length = 255)
    private String operationRef;

    @Column(name = "binding_digest", nullable = false, length = 71)
    private String bindingDigest;

    protected FilesBlobCleanupDispositionId() {
    }

    FilesBlobCleanupDispositionId(String operationRef, String bindingDigest) {
        this.operationRef = Objects.requireNonNull(operationRef, "operationRef");
        this.bindingDigest = Objects.requireNonNull(bindingDigest, "bindingDigest");
    }

    String operationRef() {
        return operationRef;
    }

    String bindingDigest() {
        return bindingDigest;
    }

    @Override public boolean equals(Object candidate) {
        return this == candidate || candidate instanceof FilesBlobCleanupDispositionId other
                && Objects.equals(operationRef, other.operationRef)
                && Objects.equals(bindingDigest, other.bindingDigest);
    }

    @Override public int hashCode() {
        return Objects.hash(operationRef, bindingDigest);
    }
}

@Entity
@Table(name = "weave_files_blob_cleanup_dispositions")
class FilesBlobCleanupDispositionJpaEntity {
    static final String VERSION = "weave.files-blob-cleanup-disposition/v1";

    @EmbeddedId
    private FilesBlobCleanupDispositionId id;

    @Column(name = "disposition_version", nullable = false, length = 64, updatable = false)
    private String dispositionVersion;

    @Column(name = "private_blob_binding", nullable = false, length = 1024, updatable = false)
    private String privateBlobBinding;

    @Column(name = "disposition", nullable = false, length = 32, updatable = false)
    private String disposition;

    @Column(name = "recorded_at_utc", nullable = false, updatable = false)
    private OffsetDateTime recordedAt;

    protected FilesBlobCleanupDispositionJpaEntity() {
    }

    static FilesBlobCleanupDispositionJpaEntity create(
            String operationRef,
            String bindingDigest,
            String privateBlobBinding,
            String disposition,
            Instant recordedAt) {
        FilesBlobCleanupDispositionJpaEntity entity = new FilesBlobCleanupDispositionJpaEntity();
        entity.id = new FilesBlobCleanupDispositionId(operationRef, bindingDigest);
        entity.dispositionVersion = VERSION;
        entity.privateBlobBinding = Objects.requireNonNull(privateBlobBinding, "privateBlobBinding");
        entity.disposition = Objects.requireNonNull(disposition, "disposition");
        entity.recordedAt = FilesPersistenceTime.utc(
                Objects.requireNonNull(recordedAt, "recordedAt"));
        return entity;
    }

    String operationRef() {
        return id.operationRef();
    }

    String bindingDigest() {
        return id.bindingDigest();
    }

    String dispositionVersion() {
        return dispositionVersion;
    }

    String privateBlobBinding() {
        return privateBlobBinding;
    }

    String disposition() {
        return disposition;
    }

    Instant recordedAt() {
        return FilesPersistenceTime.instant(recordedAt);
    }
}

interface FilesBlobCleanupDispositionJpaRepository
        extends JpaRepository<FilesBlobCleanupDispositionJpaEntity, FilesBlobCleanupDispositionId> {
    List<FilesBlobCleanupDispositionJpaEntity>
            findByIdOperationRefOrderByIdBindingDigest(String operationRef);

    @Query("""
            select disposition
            from FilesBlobCleanupDispositionJpaEntity disposition
            where disposition.id.operationRef = :operationRef
              and disposition.privateBlobBinding = :privateBlobBinding
            """)
    Optional<FilesBlobCleanupDispositionJpaEntity> findByOperationRefAndPrivateBlobBinding(
            @Param("operationRef") String operationRef,
            @Param("privateBlobBinding") String privateBlobBinding);

    @Query("""
            select count(file)
            from FileObjectJpaEntity file
            where file.id.organizationRef = :organizationRef
              and file.id.spaceRef = :spaceRef
              and file.storageReference = :privateBlobBinding
            """)
    long countCanonicalReferences(
            @Param("organizationRef") String organizationRef,
            @Param("spaceRef") String spaceRef,
            @Param("privateBlobBinding") String privateBlobBinding);

    @Query("""
            select count(target)
            from FilesMutationTargetJpaEntity target,
                 FilesMutationPlanJpaEntity plan,
                 OperationIntentJpaEntity intent
            where target.id.operationRef = plan.operationRef
              and plan.operationRef = intent.operationRef
              and plan.operationRef <> :excludedOperationRef
              and plan.organizationRef = :organizationRef
              and plan.spaceRef = :spaceRef
              and plan.planState = 'SEALED'
              and intent.organizationRef = :organizationRef
              and intent.domain = 'files'
              and intent.state in :intentStates
              and target.objectKind = 'FILE'
              and (target.sourceReadBlobBinding = :privateBlobBinding
                   or target.resultBlobBinding = :privateBlobBinding)
            """)
    long countOtherNonterminalPlanReferences(
            @Param("organizationRef") String organizationRef,
            @Param("spaceRef") String spaceRef,
            @Param("excludedOperationRef") String excludedOperationRef,
            @Param("privateBlobBinding") String privateBlobBinding,
            @Param("intentStates") List<String> intentStates);
}

@Embeddable
class FilesChangeId implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    @Column(name = "organization_ref", nullable = false, length = 255) private String organizationRef;
    @Column(name = "space_ref", nullable = false, length = 255) private String spaceRef;
    @Column(name = "revision", nullable = false) private long revision;

    protected FilesChangeId() {
    }

    FilesChangeId(String organizationRef, String spaceRef, long revision) {
        this.organizationRef = Objects.requireNonNull(organizationRef, "organizationRef");
        this.spaceRef = Objects.requireNonNull(spaceRef, "spaceRef");
        this.revision = revision;
    }

    String organizationRef() { return organizationRef; }
    String spaceRef() { return spaceRef; }
    long revision() { return revision; }

    @Override public boolean equals(Object candidate) {
        return this == candidate || candidate instanceof FilesChangeId other
                && revision == other.revision
                && Objects.equals(organizationRef, other.organizationRef)
                && Objects.equals(spaceRef, other.spaceRef);
    }

    @Override public int hashCode() { return Objects.hash(organizationRef, spaceRef, revision); }
}

@Entity
@Table(name = "weave_files_changes")
class FilesChangeJpaEntity {
    @EmbeddedId private FilesChangeId id;
    @Column(name = "operation_ref", nullable = false, length = 255, updatable = false) private String operationRef;
    @Column(name = "change_kind", nullable = false, length = 32, updatable = false) private String changeKind;
    @Column(name = "file_ref", nullable = false, length = 255, updatable = false) private String fileRef;
    @Column(name = "source_file_ref", length = 255, updatable = false) private String sourceFileRef;
    @Column(name = "source_path", length = 2048, updatable = false) private String sourcePath;
    @Column(name = "target_path", length = 2048, updatable = false) private String targetPath;
    @Column(name = "object_kind", nullable = false, length = 32, updatable = false) private String objectKind;
    @Column(name = "lifecycle_state", nullable = false, length = 32, updatable = false) private String lifecycleState;
    @Column(name = "provider_binding_revision", nullable = false, updatable = false) private long providerBindingRevision;
    @Column(name = "resulting_size", nullable = false, updatable = false) private long resultingSize;
    @Column(name = "resulting_media_type", length = 255, updatable = false) private String resultingMediaType;
    @Column(name = "resulting_content_digest", length = 71, updatable = false) private String resultingContentDigest;
    @Column(name = "resulting_file_version", length = 1024, updatable = false) private String resultingFileVersion;
    @Column(name = "resulting_etag", length = 1024, updatable = false) private String resultingEtag;
    @Column(name = "resulting_modified_at_utc", nullable = false, updatable = false) private OffsetDateTime resultingModifiedAt;
    @Column(name = "resulting_hidden", nullable = false, updatable = false) private boolean resultingHidden;
    @Column(name = "resulting_observed_at_utc", nullable = false, updatable = false) private OffsetDateTime resultingObservedAt;
    @Column(name = "range_start", nullable = false, updatable = false) private long rangeStart;
    @Column(name = "range_end", nullable = false, updatable = false) private long rangeEnd;
    @Column(name = "committed_at_utc", nullable = false, updatable = false) private OffsetDateTime committedAt;

    protected FilesChangeJpaEntity() {
    }

    static FilesChangeJpaEntity create(
            Sealed plan,
            Target target,
            long revision,
            FilesStreamHeadJpaEntity.RevisionRange range,
            Instant committedAt) {
        FilesChangeJpaEntity entity = new FilesChangeJpaEntity();
        entity.id = new FilesChangeId(plan.organizationRef(), plan.spaceRef(), revision);
        entity.operationRef = plan.operationRef();
        entity.changeKind = target.changeKind().name();
        entity.fileRef = target.targetFileRef();
        entity.sourceFileRef = target.sourceFileRef();
        entity.sourcePath = target.sourcePath();
        entity.targetPath = target.targetPath();
        entity.objectKind = target.objectKind().name();
        entity.lifecycleState = target.resultLifecycleState().name();
        entity.providerBindingRevision = plan.providerBindingRevision();
        entity.resultingSize = target.resultSize();
        entity.resultingMediaType = target.objectKind() == Kind.FILE ? target.resultMediaType() : null;
        entity.resultingContentDigest = target.objectKind() == Kind.FILE ? target.resultContentDigest() : null;
        entity.resultingFileVersion = target.objectKind() == Kind.FILE ? target.resultFileVersion() : null;
        entity.resultingEtag = target.objectKind() == Kind.FILE ? target.resultStrongEtag() : null;
        entity.resultingModifiedAt = FilesPersistenceTime.utc(target.resultModifiedAt());
        entity.resultingHidden = target.resultHidden();
        entity.resultingObservedAt = FilesPersistenceTime.utc(target.resultObservedAt());
        entity.rangeStart = range.start();
        entity.rangeEnd = range.end();
        entity.committedAt = FilesPersistenceTime.utc(committedAt);
        return entity;
    }

    long rangeStart() { return rangeStart; }
    long rangeEnd() { return rangeEnd; }

    FileChange toFileChange() {
        return new FileChange(
                id.organizationRef(),
                id.spaceRef(),
                id.revision(),
                operationRef,
                ChangeKind.valueOf(changeKind),
                new FileId(fileRef),
                sourceFileRef == null ? null : new FileId(sourceFileRef),
                sourcePath == null ? null : new FilePath(sourcePath),
                targetPath == null ? null : new FilePath(targetPath),
                Kind.valueOf(objectKind),
                Lifecycle.valueOf(lifecycleState),
                providerBindingRevision,
                resultingSize,
                resultingMediaType,
                resultingContentDigest,
                resultingFileVersion == null
                        ? FileVersion.unknown()
                        : new FileVersion(resultingFileVersion),
                resultingEtag,
                FilesPersistenceTime.instant(resultingModifiedAt),
                resultingHidden,
                FilesPersistenceTime.instant(resultingObservedAt),
                rangeStart,
                rangeEnd,
                FilesPersistenceTime.instant(committedAt));
    }
}

interface FilesChangeJpaRepository extends JpaRepository<FilesChangeJpaEntity, FilesChangeId> {
    boolean existsByIdOrganizationRefAndIdSpaceRef(
            String organizationRef,
            String spaceRef);

    List<FilesChangeJpaEntity> findByOperationRefOrderByIdRevision(String operationRef);

    @Query("""
            select change
            from FilesChangeJpaEntity change
            where change.id.organizationRef = :organizationRef
              and change.id.spaceRef = :spaceRef
              and change.id.revision > :afterRevision
              and change.id.revision <= :highWater
            order by change.id.revision
            """)
    List<FilesChangeJpaEntity> findPage(
            @Param("organizationRef") String organizationRef,
            @Param("spaceRef") String spaceRef,
            @Param("afterRevision") long afterRevision,
            @Param("highWater") long highWater,
            Pageable pageable);
}

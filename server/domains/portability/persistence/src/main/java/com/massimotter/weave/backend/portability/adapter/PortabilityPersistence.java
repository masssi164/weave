package com.massimotter.weave.backend.portability.adapter;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

enum PortabilityLifecycleState {
    DRAFT,
    DISCOVERING,
    PREFLIGHT,
    DRY_RUN,
    REVIEW_REQUIRED,
    APPROVED,
    PREPARING,
    COPYING,
    DELTA_SYNC,
    CUTOVER,
    VERIFYING,
    COMPLETED,
    FAILED,
    ROLLBACK_READY,
    ROLLED_BACK
}

@Entity
@Table(name = "weave_portability_plans")
class PortabilityPlanJpaEntity {
    @Id
    @Column(name = "plan_ref", nullable = false, length = 255, updatable = false)
    private String planRef;

    @Column(name = "organization_ref", nullable = false, length = 255, updatable = false)
    private String organizationRef;

    @Column(name = "domain_key", nullable = false, length = 80, updatable = false)
    private String domain;

    @Column(name = "source_binding_revision", nullable = false, updatable = false)
    private long sourceBindingRevision;

    @Column(name = "target_binding_revision", nullable = false, updatable = false)
    private long targetBindingRevision;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_state", nullable = false, length = 32)
    private PortabilityLifecycleState lifecycleState;

    @Column(name = "created_at_utc", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at_utc", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected PortabilityPlanJpaEntity() {
    }

    static PortabilityPlanJpaEntity draft(
            String planRef,
            String organizationRef,
            String domain,
            long sourceBindingRevision,
            long targetBindingRevision,
            Instant now) {
        if (sourceBindingRevision <= 0
                || targetBindingRevision <= 0
                || sourceBindingRevision == targetBindingRevision) {
            throw new IllegalArgumentException(
                    "Portability plan revisions must be positive and distinct");
        }
        PortabilityPlanJpaEntity entity = new PortabilityPlanJpaEntity();
        entity.planRef = Objects.requireNonNull(planRef, "planRef");
        entity.organizationRef =
                Objects.requireNonNull(organizationRef, "organizationRef");
        entity.domain = Objects.requireNonNull(domain, "domain");
        entity.sourceBindingRevision = sourceBindingRevision;
        entity.targetBindingRevision = targetBindingRevision;
        entity.lifecycleState = PortabilityLifecycleState.DRAFT;
        entity.createdAt = utc(now);
        entity.updatedAt = utc(now);
        return entity;
    }

    void transition(PortabilityLifecycleState target, Instant now) {
        lifecycleState = Objects.requireNonNull(target, "target");
        updatedAt = utc(now);
    }

    private static OffsetDateTime utc(Instant value) {
        return Objects.requireNonNull(value, "now")
                .truncatedTo(ChronoUnit.MICROS)
                .atOffset(ZoneOffset.UTC);
    }
}

interface PortabilityPlanJpaRepository
        extends JpaRepository<PortabilityPlanJpaEntity, String> {
    Optional<PortabilityPlanJpaEntity>
            findFirstByLifecycleStateOrderByUpdatedAtAscPlanRefAsc(
                    PortabilityLifecycleState lifecycleState);
}

enum PortabilityFidelityClass {
    F0,
    F1,
    F2,
    F3,
    F4
}

enum PortabilityDisposition {
    PRESERVE,
    TRANSFORM,
    ARCHIVE,
    BLOCK
}

@Entity
@Table(name = "weave_portability_fidelity_items")
class PortabilityFidelityItemJpaEntity {
    @EmbeddedId
    private PortabilityFidelityItemId id;

    @Enumerated(EnumType.STRING)
    @Column(name = "fidelity_class", nullable = false, length = 2, updatable = false)
    private PortabilityFidelityClass fidelityClass;

    @Enumerated(EnumType.STRING)
    @Column(name = "disposition", nullable = false, length = 32, updatable = false)
    private PortabilityDisposition disposition;

    @Column(name = "recorded_at_utc", nullable = false, updatable = false)
    private OffsetDateTime recordedAt;

    protected PortabilityFidelityItemJpaEntity() {
    }

    static PortabilityFidelityItemJpaEntity record(
            String planRef,
            String canonicalObjectId,
            PortabilityFidelityClass fidelityClass,
            PortabilityDisposition disposition,
            Instant recordedAt) {
        PortabilityFidelityItemJpaEntity entity =
                new PortabilityFidelityItemJpaEntity();
        entity.id = new PortabilityFidelityItemId(
                planRef, canonicalObjectId);
        entity.fidelityClass =
                Objects.requireNonNull(fidelityClass, "fidelityClass");
        entity.disposition =
                Objects.requireNonNull(disposition, "disposition");
        entity.recordedAt = Objects.requireNonNull(recordedAt, "recordedAt")
                .truncatedTo(ChronoUnit.MICROS)
                .atOffset(ZoneOffset.UTC);
        return entity;
    }
}

@Embeddable
class PortabilityFidelityItemId implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "plan_ref", nullable = false, length = 255)
    private String planRef;

    @Column(name = "canonical_object_id", nullable = false, length = 255)
    private String canonicalObjectId;

    protected PortabilityFidelityItemId() {
    }

    PortabilityFidelityItemId(
            String planRef,
            String canonicalObjectId) {
        this.planRef = Objects.requireNonNull(planRef, "planRef");
        this.canonicalObjectId =
                Objects.requireNonNull(canonicalObjectId, "canonicalObjectId");
    }

    @Override
    public boolean equals(Object candidate) {
        return this == candidate
                || candidate instanceof PortabilityFidelityItemId other
                && Objects.equals(planRef, other.planRef)
                && Objects.equals(canonicalObjectId, other.canonicalObjectId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(planRef, canonicalObjectId);
    }
}

interface PortabilityFidelityItemJpaRepository
        extends JpaRepository<
                PortabilityFidelityItemJpaEntity,
                PortabilityFidelityItemId> {
}

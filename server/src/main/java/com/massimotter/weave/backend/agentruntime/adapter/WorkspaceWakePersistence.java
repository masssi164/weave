package com.massimotter.weave.backend.agentruntime.adapter;

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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

enum WorkspaceRevisionState {
    DRAFT,
    ACTIVE,
    SUPERSEDED,
    REJECTED
}

@Entity
@Table(name = "weave_workspace_revisions")
class WorkspaceRevisionJpaEntity {
    @EmbeddedId
    private WorkspaceRevisionId id;

    @Column(name = "manifest_ref", nullable = false, length = 1000, updatable = false)
    private String manifestRef;

    @Column(name = "manifest_digest", nullable = false, length = 71, updatable = false)
    private String manifestDigest;

    @Column(name = "signature_key_ref", nullable = false, length = 255, updatable = false)
    private String signatureKeyRef;

    @Column(name = "signature", nullable = false, length = 2048, updatable = false)
    private String signature;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_state", nullable = false, length = 32)
    private WorkspaceRevisionState lifecycleState;

    @Column(name = "active_slot")
    private Boolean activeSlot;

    @Column(name = "created_at_utc", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "activated_at_utc")
    private OffsetDateTime activatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected WorkspaceRevisionJpaEntity() {
    }

    static WorkspaceRevisionJpaEntity draft(
            String organizationRef,
            String personRef,
            long revision,
            String manifestRef,
            String manifestDigest,
            String signatureKeyRef,
            String signature,
            Instant createdAt) {
        if (revision <= 0) {
            throw new IllegalArgumentException(
                    "Workspace revision must be positive");
        }
        if (manifestDigest == null
                || !manifestDigest.matches("sha256:[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "Workspace manifest digest is invalid");
        }
        WorkspaceRevisionJpaEntity entity =
                new WorkspaceRevisionJpaEntity();
        entity.id = new WorkspaceRevisionId(
                organizationRef, personRef, revision);
        entity.manifestRef =
                Objects.requireNonNull(manifestRef, "manifestRef");
        entity.manifestDigest = manifestDigest;
        entity.signatureKeyRef =
                Objects.requireNonNull(signatureKeyRef, "signatureKeyRef");
        entity.signature = Objects.requireNonNull(signature, "signature");
        entity.lifecycleState = WorkspaceRevisionState.DRAFT;
        entity.createdAt = utc(createdAt);
        return entity;
    }

    void activate(Instant now) {
        lifecycleState = WorkspaceRevisionState.ACTIVE;
        activeSlot = true;
        activatedAt = utc(now);
    }

    void supersede() {
        lifecycleState = WorkspaceRevisionState.SUPERSEDED;
        activeSlot = null;
    }

    private static OffsetDateTime utc(Instant value) {
        return Objects.requireNonNull(value, "timestamp")
                .truncatedTo(ChronoUnit.MICROS)
                .atOffset(ZoneOffset.UTC);
    }
}

@Embeddable
class WorkspaceRevisionId implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "organization_ref", nullable = false, length = 255)
    private String organizationRef;

    @Column(name = "person_ref", nullable = false, length = 255)
    private String personRef;

    @Column(name = "revision", nullable = false)
    private long revision;

    protected WorkspaceRevisionId() {
    }

    WorkspaceRevisionId(
            String organizationRef,
            String personRef,
            long revision) {
        this.organizationRef = Objects.requireNonNull(
                organizationRef, "organizationRef");
        this.personRef = Objects.requireNonNull(personRef, "personRef");
        this.revision = revision;
    }

    @Override
    public boolean equals(Object candidate) {
        return this == candidate
                || candidate instanceof WorkspaceRevisionId other
                && revision == other.revision
                && Objects.equals(organizationRef, other.organizationRef)
                && Objects.equals(personRef, other.personRef);
    }

    @Override
    public int hashCode() {
        return Objects.hash(organizationRef, personRef, revision);
    }
}

interface WorkspaceRevisionJpaRepository
        extends JpaRepository<WorkspaceRevisionJpaEntity, WorkspaceRevisionId> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select revision from WorkspaceRevisionJpaEntity revision
            where revision.id.organizationRef = :organizationRef
              and revision.id.personRef = :personRef
              and revision.lifecycleState = :state
            """)
    Optional<WorkspaceRevisionJpaEntity> lockActive(
            @Param("organizationRef") String organizationRef,
            @Param("personRef") String personRef,
            @Param("state") WorkspaceRevisionState state);
}

enum WakeDeliveryState {
    PENDING,
    DELIVERING,
    DELIVERED,
    FAILED
}

@Entity
@Table(name = "weave_wake_envelopes")
class WakeEnvelopeJpaEntity {
    @EmbeddedId
    private WakeEnvelopeId id;

    @Column(name = "event_digest", nullable = false, length = 71, updatable = false)
    private String eventDigest;

    @Column(name = "outbox_ref", nullable = false, length = 255, updatable = false)
    private String outboxRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_state", nullable = false, length = 32)
    private WakeDeliveryState deliveryState;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at_utc")
    private OffsetDateTime nextAttemptAt;

    @Column(name = "created_at_utc", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "delivered_at_utc")
    private OffsetDateTime deliveredAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected WakeEnvelopeJpaEntity() {
    }

    static WakeEnvelopeJpaEntity pending(
            String organizationRef,
            String cellRef,
            String wakeRef,
            String eventDigest,
            String outboxRef,
            Instant createdAt) {
        if (eventDigest == null
                || !eventDigest.matches("sha256:[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Wake event digest is invalid");
        }
        WakeEnvelopeJpaEntity entity = new WakeEnvelopeJpaEntity();
        entity.id = new WakeEnvelopeId(
                organizationRef, cellRef, wakeRef);
        entity.eventDigest = eventDigest;
        entity.outboxRef = Objects.requireNonNull(outboxRef, "outboxRef");
        entity.deliveryState = WakeDeliveryState.PENDING;
        entity.createdAt = utc(createdAt);
        return entity;
    }

    void deliveryFailed(Instant retryAt) {
        deliveryState = WakeDeliveryState.FAILED;
        attemptCount++;
        nextAttemptAt = utc(retryAt);
    }

    void delivered(Instant now) {
        deliveryState = WakeDeliveryState.DELIVERED;
        nextAttemptAt = null;
        deliveredAt = utc(now);
    }

    private static OffsetDateTime utc(Instant value) {
        return value == null
                ? null
                : value.truncatedTo(ChronoUnit.MICROS)
                        .atOffset(ZoneOffset.UTC);
    }
}

@Embeddable
class WakeEnvelopeId implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "organization_ref", nullable = false, length = 255)
    private String organizationRef;

    @Column(name = "cell_ref", nullable = false, length = 255)
    private String cellRef;

    @Column(name = "wake_ref", nullable = false, length = 255)
    private String wakeRef;

    protected WakeEnvelopeId() {
    }

    WakeEnvelopeId(
            String organizationRef,
            String cellRef,
            String wakeRef) {
        this.organizationRef = Objects.requireNonNull(
                organizationRef, "organizationRef");
        this.cellRef = Objects.requireNonNull(cellRef, "cellRef");
        this.wakeRef = Objects.requireNonNull(wakeRef, "wakeRef");
    }

    @Override
    public boolean equals(Object candidate) {
        return this == candidate
                || candidate instanceof WakeEnvelopeId other
                && Objects.equals(organizationRef, other.organizationRef)
                && Objects.equals(cellRef, other.cellRef)
                && Objects.equals(wakeRef, other.wakeRef);
    }

    @Override
    public int hashCode() {
        return Objects.hash(organizationRef, cellRef, wakeRef);
    }
}

interface WakeEnvelopeJpaRepository
        extends JpaRepository<WakeEnvelopeJpaEntity, WakeEnvelopeId> {
    List<WakeEnvelopeJpaEntity>
            findByDeliveryStateAndNextAttemptAtBeforeOrderByCreatedAtAsc(
                    WakeDeliveryState deliveryState,
                    OffsetDateTime now);
}

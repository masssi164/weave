package com.massimotter.weave.backend.transfer.adapter;

import com.massimotter.weave.backend.transfer.domain.CanonicalObjectId;
import com.massimotter.weave.backend.transfer.domain.TransferPrimitives.LossClass;
import com.massimotter.weave.backend.transfer.domain.TransferPrimitives.LossRecord;
import com.massimotter.weave.backend.transfer.domain.TransferPrimitives.TransferCheckpoint;
import com.massimotter.weave.backend.transfer.domain.TransferPrimitives.TransferFormatVersion;
import com.massimotter.weave.backend.transfer.domain.TransferRun;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Entity
@Table(name = "weave_transfer_runs")
class TransferRunJpaEntity {

    @Id
    @Column(name = "run_id", nullable = false, length = 255)
    private String runId;

    @Column(name = "organization_ref", nullable = false, length = 255, updatable = false)
    private String organizationRef;

    @Column(name = "canonical_model_version", nullable = false, length = 80, updatable = false)
    private String canonicalModelVersion;

    @Column(name = "transfer_format_version", nullable = false, updatable = false)
    private int transferFormatVersion;

    @Column(name = "state_revision", nullable = false)
    private long stateRevision;

    @Enumerated(EnumType.STRING)
    @Column(name = "run_status", nullable = false, length = 32)
    private TransferRun.Status status;

    @Column(name = "checkpoint_cursor", length = 1024)
    private String checkpointCursor;

    @Column(name = "checkpoint_sequence")
    private Long checkpointSequence;

    @Column(name = "batches_applied", nullable = false)
    private long batchesApplied;

    @Column(name = "items_applied", nullable = false)
    private long itemsApplied;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "weave_transfer_run_losses",
            joinColumns = @JoinColumn(name = "run_id", nullable = false),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_transfer_run_loss_field",
                    columnNames = {"run_id", "canonical_object_id", "field_key"}))
    private Set<TransferLossJpaValue> losses = new LinkedHashSet<>();

    @Column(name = "last_aggregate_digest", nullable = false, length = 128)
    private String lastAggregateDigest;

    @Column(name = "failure_reason", length = 4000)
    private String failureReason;

    @Column(name = "updated_at_utc", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "persistence_version", nullable = false)
    private long persistenceVersion;

    protected TransferRunJpaEntity() {
    }

    static TransferRunJpaEntity from(TransferRun run) {
        TransferRunJpaEntity entity = new TransferRunJpaEntity();
        entity.runId = run.id().value();
        entity.organizationRef = run.organizationRef();
        entity.canonicalModelVersion = run.canonicalModelVersion();
        entity.transferFormatVersion = run.transferFormatVersion().value();
        entity.apply(run);
        return entity;
    }

    void apply(TransferRun run) {
        Objects.requireNonNull(run, "run must not be null");
        if (runId != null && !runId.equals(run.id().value())) {
            throw new IllegalArgumentException("transfer run id cannot change");
        }
        if (organizationRef != null
                && (!organizationRef.equals(run.organizationRef())
                || !canonicalModelVersion.equals(run.canonicalModelVersion())
                || transferFormatVersion != run.transferFormatVersion().value())) {
            throw new IllegalArgumentException("transfer run coordinates cannot change");
        }
        stateRevision = run.stateRevision();
        status = run.status();
        TransferCheckpoint checkpoint = run.sourceCheckpoint();
        checkpointCursor = checkpoint == null ? null : checkpoint.cursor();
        checkpointSequence = checkpoint == null ? null : checkpoint.sequence();
        batchesApplied = run.batchesApplied();
        itemsApplied = run.itemsApplied();
        losses.clear();
        run.losses().stream()
                .map(TransferLossJpaValue::from)
                .forEach(losses::add);
        lastAggregateDigest = run.lastAggregateDigest();
        failureReason = run.failureReason();
        updatedAt = run.updatedAt()
                .truncatedTo(ChronoUnit.MICROS)
                .atOffset(ZoneOffset.UTC);
    }

    long stateRevision() {
        return stateRevision;
    }

    TransferRun toDomain() {
        TransferCheckpoint checkpoint = checkpointCursor == null
                ? null
                : new TransferCheckpoint(checkpointCursor, checkpointSequence);
        List<LossRecord> domainLosses = losses.stream()
                .map(TransferLossJpaValue::toDomain)
                .sorted(Comparator
                        .comparing((LossRecord loss) -> loss.objectId().value())
                        .thenComparing(LossRecord::field))
                .toList();
        return new TransferRun(
                new TransferRun.Id(runId),
                organizationRef,
                canonicalModelVersion,
                new TransferFormatVersion(transferFormatVersion),
                stateRevision,
                status,
                checkpoint,
                batchesApplied,
                itemsApplied,
                domainLosses,
                lastAggregateDigest,
                failureReason,
                updatedAt.toInstant());
    }
}

@Embeddable
class TransferLossJpaValue {

    @Column(name = "canonical_object_id", nullable = false, length = 255)
    private String canonicalObjectId;

    @Column(name = "field_key", nullable = false, length = 255)
    private String field;

    @Enumerated(EnumType.STRING)
    @Column(name = "loss_class", nullable = false, length = 32)
    private LossClass classification;

    @Column(name = "reason", nullable = false, length = 2000)
    private String reason;

    protected TransferLossJpaValue() {
    }

    static TransferLossJpaValue from(LossRecord loss) {
        TransferLossJpaValue value = new TransferLossJpaValue();
        value.canonicalObjectId = loss.objectId().value();
        value.field = loss.field();
        value.classification = loss.classification();
        value.reason = loss.reason();
        return value;
    }

    LossRecord toDomain() {
        return new LossRecord(
                new CanonicalObjectId(canonicalObjectId),
                field,
                classification,
                reason);
    }

    @Override
    public boolean equals(Object candidate) {
        return this == candidate
                || candidate instanceof TransferLossJpaValue other
                && Objects.equals(canonicalObjectId, other.canonicalObjectId)
                && Objects.equals(field, other.field)
                && classification == other.classification
                && Objects.equals(reason, other.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(canonicalObjectId, field, classification, reason);
    }
}

interface TransferRunJpaRepository extends JpaRepository<TransferRunJpaEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select run from TransferRunJpaEntity run where run.runId = :runId")
    Optional<TransferRunJpaEntity> lockById(@Param("runId") String runId);
}

package com.massimotter.weave.backend.operation.adapter;

import com.massimotter.weave.backend.operation.domain.OperationIntent;
import com.massimotter.weave.backend.operation.domain.OperationIntent.Actor;
import com.massimotter.weave.backend.operation.domain.OperationIntent.AdminApiProjection;
import com.massimotter.weave.backend.operation.domain.OperationIntent.HumanActor;
import com.massimotter.weave.backend.operation.domain.OperationIntent.InternalProjection;
import com.massimotter.weave.backend.operation.domain.OperationIntent.McpProjection;
import com.massimotter.weave.backend.operation.domain.OperationIntent.Projection;
import com.massimotter.weave.backend.operation.domain.OperationIntent.ProtocolProjection;
import com.massimotter.weave.backend.operation.domain.OperationIntent.Reconciliation;
import com.massimotter.weave.backend.operation.domain.OperationIntent.ReconciliationOutcome;
import com.massimotter.weave.backend.operation.domain.OperationIntent.State;
import com.massimotter.weave.backend.operation.domain.OperationIntent.WorkloadActor;
import com.massimotter.weave.backend.operation.domain.OperationOutboxEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Entity
@Table(name = "weave_operation_intents")
class OperationIntentJpaEntity {

    @Id
    @Column(name = "operation_ref", nullable = false, length = 255, updatable = false)
    private String operationRef;

    @Column(name = "intent_version", nullable = false, length = 64, updatable = false)
    private String intentVersion;

    @Column(name = "idempotency_key", nullable = false, length = 128, updatable = false)
    private String idempotencyKey;

    @Column(name = "organization_ref", nullable = false, length = 255, updatable = false)
    private String organizationRef;

    @Column(name = "actor_kind", nullable = false, length = 32, updatable = false)
    private String actorKind;

    @Column(name = "person_ref", nullable = false, length = 255, updatable = false)
    private String personRef;

    @Column(name = "subject_ref", length = 255, updatable = false)
    private String subjectRef;

    @Column(name = "cell_ref", length = 255, updatable = false)
    private String cellRef;

    @Column(name = "client_ref", length = 255, updatable = false)
    private String clientRef;

    @Column(name = "profile_revision", updatable = false)
    private Long profileRevision;

    @Column(name = "fencing_epoch", updatable = false)
    private Long fencingEpoch;

    @Column(name = "domain_key", nullable = false, length = 80, updatable = false)
    private String domain;

    @Column(name = "projection_kind", nullable = false, length = 32, updatable = false)
    private String projectionKind;

    @Column(name = "projection_value_1", nullable = false, length = 255, updatable = false)
    private String projectionValue1;

    @Column(name = "projection_value_2", nullable = false, length = 255, updatable = false)
    private String projectionValue2;

    @Column(name = "projection_value_3", length = 255, updatable = false)
    private String projectionValue3;

    @Column(name = "action_digest", nullable = false, length = 71, updatable = false)
    private String actionDigest;

    @Column(name = "canonical_arguments_digest", nullable = false, length = 71, updatable = false)
    private String canonicalArgumentsDigest;

    @Column(name = "object_refs_json", nullable = false, updatable = false)
    private String objectRefsJson;

    @Column(name = "policy_revision", nullable = false, length = 255, updatable = false)
    private String policyRevision;

    @Column(name = "entitlement_revision", nullable = false, length = 255, updatable = false)
    private String entitlementRevision;

    @Column(name = "provider_binding_revision", nullable = false, updatable = false)
    private long providerBindingRevision;

    @Column(name = "intent_state", nullable = false, length = 32)
    private String state;

    @Column(name = "initial_outbox_ref", nullable = false, length = 255, updatable = false)
    private String initialOutboxRef;

    @Column(name = "provider_correlation_hash", length = 71)
    private String providerCorrelationHash;

    @Column(name = "reconciliation_attempts", nullable = false)
    private int reconciliationAttempts;

    @Column(name = "reconciliation_max_attempts", nullable = false)
    private int reconciliationMaxAttempts;

    @Column(name = "reconciliation_outcome", length = 40)
    private String reconciliationOutcome;

    @Column(name = "reconciliation_last_attempt_at_utc")
    private OffsetDateTime reconciliationLastAttemptAt;

    @Column(name = "reconciliation_lease_until_utc")
    private OffsetDateTime reconciliationLeaseUntil;

    @Column(name = "reconciliation_result_digest", length = 71)
    private String reconciliationResultDigest;

    @Column(name = "result_digest", length = 71)
    private String resultDigest;

    @Column(name = "audit_ref", length = 255)
    private String auditRef;

    @Column(name = "created_at_utc", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at_utc", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected OperationIntentJpaEntity() {
    }

    static OperationIntentJpaEntity create(
            OperationIntent intent,
            String objectRefsJson) {
        OperationIntentJpaEntity entity = new OperationIntentJpaEntity();
        entity.operationRef = intent.operationRef();
        entity.intentVersion = OperationIntent.VERSION;
        entity.idempotencyKey = intent.idempotencyKey();
        entity.organizationRef = intent.organizationRef();
        entity.applyActor(intent.actor());
        entity.domain = intent.domain();
        entity.applyProjection(intent.projection());
        entity.actionDigest = intent.actionDigest();
        entity.canonicalArgumentsDigest = intent.canonicalArgumentsDigest();
        entity.objectRefsJson = objectRefsJson;
        entity.policyRevision = intent.policyRevision();
        entity.entitlementRevision = intent.entitlementRevision();
        entity.providerBindingRevision = intent.providerBindingRevision();
        entity.initialOutboxRef = intent.outboxRef();
        entity.createdAt = OperationPersistenceTime.utc(intent.createdAt());
        entity.reconciliationMaxAttempts = 5;
        entity.applyTransition(intent);
        return entity;
    }

    void applyTransition(OperationIntent intent) {
        state = intent.state().name();
        providerCorrelationHash = intent.providerCorrelationHash();
        Reconciliation reconciliation = intent.reconciliation();
        reconciliationAttempts = reconciliation == null
                ? 0
                : reconciliation.attempts();
        reconciliationOutcome = reconciliation == null
                ? null
                : reconciliation.outcome().name();
        reconciliationLastAttemptAt = reconciliation == null
                ? null
                : OperationPersistenceTime.utc(reconciliation.lastAttemptAt());
        reconciliationResultDigest = reconciliation == null
                ? null
                : reconciliation.resultDigest();
        resultDigest = intent.resultDigest();
        auditRef = intent.auditRef();
        updatedAt = OperationPersistenceTime.utc(intent.updatedAt());
    }

    void leaseForReconciliation(Instant now, Instant leaseUntil) {
        state = State.RECONCILING.name();
        reconciliationAttempts++;
        reconciliationOutcome = ReconciliationOutcome.PENDING.name();
        reconciliationLastAttemptAt = OperationPersistenceTime.utc(now);
        reconciliationLeaseUntil = OperationPersistenceTime.utc(leaseUntil);
        updatedAt = OperationPersistenceTime.utc(now);
    }

    Instant updatedAt() {
        return updatedAt.toInstant();
    }

    OperationIntent toDomain(Function<String, List<String>> objectRefsReader) {
        Reconciliation reconciliation = reconciliationOutcome == null
                ? null
                : new Reconciliation(
                        reconciliationAttempts,
                        ReconciliationOutcome.valueOf(reconciliationOutcome),
                        OperationPersistenceTime.instant(reconciliationLastAttemptAt),
                        reconciliationResultDigest);
        return new OperationIntent(
                operationRef,
                idempotencyKey,
                organizationRef,
                actor(),
                domain,
                projection(),
                actionDigest,
                canonicalArgumentsDigest,
                objectRefsReader.apply(objectRefsJson),
                policyRevision,
                entitlementRevision,
                providerBindingRevision,
                State.valueOf(state),
                initialOutboxRef,
                providerCorrelationHash,
                reconciliation,
                resultDigest,
                auditRef,
                createdAt.toInstant(),
                updatedAt.toInstant());
    }

    private void applyActor(Actor actor) {
        switch (actor) {
            case HumanActor human -> {
                actorKind = "human";
                personRef = human.personRef();
                subjectRef = human.subjectRef();
            }
            case WorkloadActor workload -> {
                actorKind = "weaver-workload";
                personRef = workload.personRef();
                cellRef = workload.cellRef();
                clientRef = workload.clientRef();
                profileRevision = workload.profileRevision();
                fencingEpoch = workload.fencingEpoch();
            }
        }
    }

    private Actor actor() {
        return switch (actorKind) {
            case "human" -> new HumanActor(personRef, subjectRef);
            case "weaver-workload" -> new WorkloadActor(
                    personRef,
                    cellRef,
                    clientRef,
                    profileRevision,
                    fencingEpoch);
            default -> throw new IllegalStateException("unknown operation actor kind");
        };
    }

    private void applyProjection(Projection projection) {
        projectionKind = projection.kind();
        switch (projection) {
            case ProtocolProjection protocol -> {
                projectionValue1 = protocol.protocol();
                projectionValue2 = protocol.operation();
                projectionValue3 = protocol.profileVersion();
            }
            case AdminApiProjection admin -> {
                projectionValue1 = admin.operationId();
                projectionValue2 = admin.contractVersion();
            }
            case McpProjection mcp -> {
                projectionValue1 = mcp.toolName();
                projectionValue2 = mcp.toolContractVersion();
            }
            case InternalProjection internal -> {
                projectionValue1 = internal.useCase();
                projectionValue2 = internal.contractVersion();
            }
        }
    }

    private Projection projection() {
        return switch (projectionKind) {
            case "protocol" -> new ProtocolProjection(
                    projectionValue1,
                    projectionValue2,
                    projectionValue3);
            case "admin-api" -> new AdminApiProjection(
                    projectionValue1,
                    projectionValue2);
            case "mcp" -> new McpProjection(
                    projectionValue1,
                    projectionValue2);
            case "internal" -> new InternalProjection(
                    projectionValue1,
                    projectionValue2);
            default -> throw new IllegalStateException(
                    "unknown operation projection kind");
        };
    }
}

interface OperationIntentJpaRepository
        extends JpaRepository<OperationIntentJpaEntity, String> {

    Optional<OperationIntentJpaEntity> findByOrganizationRefAndIdempotencyKey(
            String organizationRef,
            String idempotencyKey);

    /**
     * Selects reconciliation work through portable JPQL and holds write locks
     * until the surrounding lease transaction commits.
     *
     * <p>The page size is the bounded batch limit. Competing reconcilers may
     * wait for the current batch instead of using a vendor-specific
     * {@code SKIP LOCKED} extension; optimistic versions and the lease
     * predicate keep correctness database independent.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select intent
            from OperationIntentJpaEntity intent
            where intent.state in :states
              and (intent.reconciliationLeaseUntil is null
                   or intent.reconciliationLeaseUntil < :now)
              and intent.reconciliationAttempts < intent.reconciliationMaxAttempts
            order by intent.updatedAt, intent.operationRef
            """)
    List<OperationIntentJpaEntity> lockReconciliationCandidates(
            @Param("states") List<String> states,
            @Param("now") OffsetDateTime now,
            Pageable pageable);
}

@Entity
@Table(name = "weave_operation_outbox")
class OperationOutboxJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sequence_id", nullable = false, updatable = false)
    private Long sequenceId;

    @Column(name = "outbox_ref", nullable = false, length = 255, updatable = false)
    private String outboxRef;

    @Column(name = "operation_ref", nullable = false, length = 255, updatable = false)
    private String operationRef;

    @Column(name = "event_type", nullable = false, length = 120, updatable = false)
    private String eventType;

    @Column(name = "payload_json", nullable = false, updatable = false)
    private String payloadJson;

    @Column(name = "delivery_state", nullable = false, length = 32)
    private String deliveryState;

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

    protected OperationOutboxJpaEntity() {
    }

    static OperationOutboxJpaEntity create(OperationOutboxEvent event) {
        OperationOutboxJpaEntity entity = new OperationOutboxJpaEntity();
        entity.outboxRef = event.outboxRef();
        entity.operationRef = event.operationRef();
        entity.eventType = event.eventType();
        entity.payloadJson = event.payloadJson();
        entity.deliveryState = "PENDING";
        entity.attemptCount = 0;
        entity.createdAt = OperationPersistenceTime.utc(event.createdAt());
        return entity;
    }
}

interface OperationOutboxJpaRepository
        extends JpaRepository<OperationOutboxJpaEntity, Long> {
}

final class OperationPersistenceTime {

    private OperationPersistenceTime() {
    }

    static OffsetDateTime utc(Instant value) {
        return value == null
                ? null
                : value.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
    }

    static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}

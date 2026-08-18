package com.massimotter.weave.backend.agentruntime.adapter;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeAuditCorrelation;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementObservation;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementRef;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementState;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeMemberBinding;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeRevocation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

@Entity
@Table(name = "weave_agent_runtime_entitlements")
class RuntimeEntitlementJpaEntity {

    @Id
    @Column(name = "record_id", nullable = false, updatable = false)
    private UUID recordId;

    @Column(name = "entitlement_ref", nullable = false, length = 255, updatable = false)
    private String entitlementRef;

    @Column(name = "entitlement_revision", nullable = false, length = 71, updatable = false)
    private String entitlementRevision;

    @Column(name = "organization_ref", nullable = false, length = 255, updatable = false)
    private String organizationRef;

    @Column(name = "person_ref", nullable = false, length = 255, updatable = false)
    private String personRef;

    @Column(name = "member_issuer", nullable = false, length = 500, updatable = false)
    private String memberIssuer;

    @Column(name = "member_subject", nullable = false, length = 255, updatable = false)
    private String memberSubject;

    @Column(name = "source_provider", nullable = false, length = 64, updatable = false)
    private String sourceProvider;

    @Column(name = "source_group_ref", nullable = false, length = 71, updatable = false)
    private String sourceGroupRef;

    @Column(name = "capability_revision", nullable = false, length = 71, updatable = false)
    private String capabilityRevision;

    @Enumerated(EnumType.STRING)
    @Column(name = "entitlement_state", nullable = false, length = 32)
    private RuntimeEntitlementState state;

    @Column(name = "effective_at", nullable = false, updatable = false)
    private OffsetDateTime effectiveAt;

    @Column(name = "last_observed_at", nullable = false)
    private OffsetDateTime lastObservedAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "revocation_ref", length = 255)
    private String revocationRef;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "audit_ref", nullable = false, length = 255)
    private String auditRef;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected RuntimeEntitlementJpaEntity() {
    }

    static RuntimeEntitlementJpaEntity from(RuntimeEntitlementRef entitlement) {
        RuntimeEntitlementJpaEntity entity = new RuntimeEntitlementJpaEntity();
        entity.recordId = entitlement.recordId();
        entity.entitlementRef = entitlement.entitlementRef();
        entity.entitlementRevision = entitlement.entitlementRevision();
        entity.organizationRef = entitlement.organizationRef();
        entity.personRef = entitlement.personRef();
        entity.memberIssuer = entitlement.memberBinding().issuer();
        entity.memberSubject = entitlement.memberBinding().subject();
        entity.sourceProvider = entitlement.sourceProvider();
        entity.sourceGroupRef = entitlement.sourceGroupRef();
        entity.capabilityRevision = entitlement.capabilityRevision();
        entity.state = entitlement.state();
        entity.effectiveAt = RuntimePersistenceTime.utc(entitlement.effectiveAt());
        entity.lastObservedAt = RuntimePersistenceTime.utc(entitlement.lastObservedAt());
        entity.expiresAt = RuntimePersistenceTime.utc(entitlement.expiresAt());
        entity.revocationRef = entitlement.revocationRef();
        entity.revokedAt = RuntimePersistenceTime.utc(entitlement.revokedAt());
        entity.auditRef = entitlement.auditRef();
        entity.createdAt = RuntimePersistenceTime.utc(entitlement.createdAt());
        entity.updatedAt = RuntimePersistenceTime.utc(entitlement.updatedAt());
        return entity;
    }

    void observe(
            RuntimeEntitlementObservation observation,
            String nextAuditRef,
            Instant now) {
        if (state != RuntimeEntitlementState.ENTITLED) {
            throw new IllegalStateException(
                    "a revoked entitlement activation cannot be replayed");
        }
        if (observation.observedAt().isAfter(lastObservedAt.toInstant())) {
            lastObservedAt = RuntimePersistenceTime.utc(observation.observedAt());
        }
        if (observation.expiresAt().isAfter(expiresAt.toInstant())) {
            expiresAt = RuntimePersistenceTime.utc(observation.expiresAt());
        }
        auditRef = nextAuditRef;
        updatedAt = RuntimePersistenceTime.utc(now);
    }

    void revoke(RuntimeRevocation revocation, Instant now) {
        if (state == RuntimeEntitlementState.REVOKED) {
            if (!revocation.revocationRef().equals(revocationRef)) {
                throw new IllegalStateException(
                        "entitlement was revoked by different evidence");
            }
            return;
        }
        state = RuntimeEntitlementState.REVOKED;
        revocationRef = revocation.revocationRef();
        revokedAt = RuntimePersistenceTime.utc(revocation.effectiveAt());
        auditRef = revocation.auditCorrelationRef();
        updatedAt = RuntimePersistenceTime.utc(now);
    }

    RuntimeEntitlementRef toDomain() {
        return new RuntimeEntitlementRef(
                recordId,
                entitlementRef,
                entitlementRevision,
                organizationRef,
                personRef,
                new RuntimeMemberBinding(memberIssuer, memberSubject),
                sourceProvider,
                sourceGroupRef,
                capabilityRevision,
                state,
                effectiveAt.toInstant(),
                lastObservedAt.toInstant(),
                expiresAt.toInstant(),
                revocationRef,
                RuntimePersistenceTime.instant(revokedAt),
                auditRef,
                createdAt.toInstant(),
                updatedAt.toInstant());
    }
}

interface RuntimeEntitlementJpaRepository
        extends JpaRepository<RuntimeEntitlementJpaEntity, UUID> {

    Optional<RuntimeEntitlementJpaEntity> findByEntitlementRef(String entitlementRef);

    Optional<RuntimeEntitlementJpaEntity>
            findFirstByOrganizationRefAndPersonRefAndStateOrderByLastObservedAtDescCreatedAtDescEntitlementRefDesc(
                    String organizationRef,
                    String personRef,
                    RuntimeEntitlementState state);

    Optional<RuntimeEntitlementJpaEntity>
            findFirstByOrganizationRefAndPersonRefOrderByLastObservedAtDescCreatedAtDescEntitlementRefDesc(
                    String organizationRef,
                    String personRef);

    Optional<RuntimeEntitlementJpaEntity>
            findByOrganizationRefAndPersonRefAndEntitlementRevision(
                    String organizationRef,
                    String personRef,
                    String entitlementRevision);

    @Query("""
            select entitlement from RuntimeEntitlementJpaEntity entitlement
            where entitlement.organizationRef = :organizationRef
              and entitlement.personRef = :personRef
              and entitlement.entitlementRevision = :entitlementRevision
              and entitlement.state = :state
              and entitlement.effectiveAt <= :now
              and entitlement.expiresAt > :now
            """)
    Optional<RuntimeEntitlementJpaEntity> findEffectiveRevision(
            @Param("organizationRef") String organizationRef,
            @Param("personRef") String personRef,
            @Param("entitlementRevision") String entitlementRevision,
            @Param("state") RuntimeEntitlementState state,
            @Param("now") OffsetDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select entitlement from RuntimeEntitlementJpaEntity entitlement
            where entitlement.organizationRef = :organizationRef
              and entitlement.personRef = :personRef
              and entitlement.memberIssuer = :memberIssuer
              and entitlement.memberSubject = :memberSubject
              and entitlement.sourceProvider = :sourceProvider
              and entitlement.sourceGroupRef = :sourceGroupRef
              and entitlement.capabilityRevision = :capabilityRevision
              and entitlement.state = :state
            order by entitlement.lastObservedAt desc, entitlement.createdAt desc
            """)
    List<RuntimeEntitlementJpaEntity> lockReusable(
            @Param("organizationRef") String organizationRef,
            @Param("personRef") String personRef,
            @Param("memberIssuer") String memberIssuer,
            @Param("memberSubject") String memberSubject,
            @Param("sourceProvider") String sourceProvider,
            @Param("sourceGroupRef") String sourceGroupRef,
            @Param("capabilityRevision") String capabilityRevision,
            @Param("state") RuntimeEntitlementState state,
            Pageable page);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select entitlement from RuntimeEntitlementJpaEntity entitlement
            where entitlement.entitlementRef = :entitlementRef
            """)
    Optional<RuntimeEntitlementJpaEntity> lockByEntitlementRef(
            @Param("entitlementRef") String entitlementRef);
}

@Entity
@Table(name = "weave_agent_runtime_revocations")
class RuntimeRevocationJpaEntity {

    @Id
    @Column(name = "record_id", nullable = false, updatable = false)
    private UUID recordId;

    @Column(name = "revocation_ref", nullable = false, length = 255, updatable = false)
    private String revocationRef;

    @Column(name = "organization_ref", nullable = false, length = 255, updatable = false)
    private String organizationRef;

    @Column(name = "person_ref", nullable = false, length = 255, updatable = false)
    private String personRef;

    @Column(name = "reason_code", nullable = false, length = 100, updatable = false)
    private String reasonCode;

    @Column(name = "reason_ref_hash", nullable = false, length = 71, updatable = false)
    private String reasonRefHash;

    @Column(name = "actor_ref_hash", nullable = false, length = 71, updatable = false)
    private String actorRefHash;

    @Column(name = "effective_at", nullable = false, updatable = false)
    private OffsetDateTime effectiveAt;

    @Column(name = "entitlement_ref", nullable = false, length = 255, updatable = false)
    private String entitlementRef;

    @Column(name = "entitlement_revision", nullable = false, length = 71, updatable = false)
    private String entitlementRevision;

    @Column(name = "cell_ref", nullable = false, length = 255, updatable = false)
    private String cellRef;

    @Column(name = "profile_hash", length = 71, updatable = false)
    private String profileHash;

    @Column(name = "workload_ref_hash", nullable = false, length = 71, updatable = false)
    private String workloadRefHash;

    @Column(name = "audit_correlation_ref", nullable = false, length = 255, updatable = false)
    private String auditCorrelationRef;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected RuntimeRevocationJpaEntity() {
    }

    static RuntimeRevocationJpaEntity from(RuntimeRevocation revocation) {
        RuntimeRevocationJpaEntity entity = new RuntimeRevocationJpaEntity();
        entity.recordId = revocation.recordId();
        entity.revocationRef = revocation.revocationRef();
        entity.organizationRef = revocation.organizationRef();
        entity.personRef = revocation.personRef();
        entity.reasonCode = revocation.reasonCode();
        entity.reasonRefHash = revocation.reasonRefHash();
        entity.actorRefHash = revocation.actorRefHash();
        entity.effectiveAt = RuntimePersistenceTime.utc(revocation.effectiveAt());
        entity.entitlementRef = revocation.entitlementRef();
        entity.entitlementRevision = revocation.entitlementRevision();
        entity.cellRef = revocation.cellRef();
        entity.profileHash = revocation.profileHash();
        entity.workloadRefHash = revocation.workloadRefHash();
        entity.auditCorrelationRef = revocation.auditCorrelationRef();
        entity.createdAt = RuntimePersistenceTime.utc(revocation.createdAt());
        return entity;
    }

    RuntimeRevocation toDomain() {
        return new RuntimeRevocation(
                recordId,
                revocationRef,
                organizationRef,
                personRef,
                reasonCode,
                reasonRefHash,
                actorRefHash,
                effectiveAt.toInstant(),
                entitlementRef,
                entitlementRevision,
                cellRef,
                profileHash,
                workloadRefHash,
                auditCorrelationRef,
                createdAt.toInstant());
    }
}

interface RuntimeRevocationJpaRepository
        extends JpaRepository<RuntimeRevocationJpaEntity, UUID> {

    Optional<RuntimeRevocationJpaEntity> findByRevocationRef(String revocationRef);
}

@Entity
@Table(name = "weave_agent_runtime_audit_correlations")
class RuntimeAuditCorrelationJpaEntity {

    @Id
    @Column(name = "record_id", nullable = false, updatable = false)
    private UUID recordId;

    @Column(name = "correlation_ref", nullable = false, length = 255, updatable = false)
    private String correlationRef;

    @Column(name = "organization_ref_hash", nullable = false, length = 71, updatable = false)
    private String organizationRefHash;

    @Column(name = "person_ref_hash", nullable = false, length = 71, updatable = false)
    private String personRefHash;

    @Column(name = "keycloak_ref_hash", length = 71, updatable = false)
    private String keycloakRefHash;

    @Column(name = "orchestrator_ref_hash", length = 71, updatable = false)
    private String orchestratorRefHash;

    @Column(name = "openclaw_ref_hash", length = 71, updatable = false)
    private String openClawRefHash;

    @Column(name = "matrix_ref_hash", length = 71, updatable = false)
    private String matrixRefHash;

    @Column(name = "mcp_ref_hash", length = 71, updatable = false)
    private String mcpRefHash;

    @Column(name = "domain_audit_ref_hash", length = 71, updatable = false)
    private String domainAuditRefHash;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected RuntimeAuditCorrelationJpaEntity() {
    }

    static RuntimeAuditCorrelationJpaEntity from(
            RuntimeAuditCorrelation correlation) {
        RuntimeAuditCorrelationJpaEntity entity =
                new RuntimeAuditCorrelationJpaEntity();
        entity.recordId = correlation.recordId();
        entity.correlationRef = correlation.correlationRef();
        entity.organizationRefHash = correlation.organizationRefHash();
        entity.personRefHash = correlation.personRefHash();
        entity.keycloakRefHash = correlation.keycloakRefHash();
        entity.orchestratorRefHash = correlation.orchestratorRefHash();
        entity.openClawRefHash = correlation.openClawRefHash();
        entity.matrixRefHash = correlation.matrixRefHash();
        entity.mcpRefHash = correlation.mcpRefHash();
        entity.domainAuditRefHash = correlation.domainAuditRefHash();
        entity.occurredAt = RuntimePersistenceTime.utc(correlation.occurredAt());
        entity.createdAt = RuntimePersistenceTime.utc(correlation.createdAt());
        return entity;
    }

    RuntimeAuditCorrelation toDomain() {
        return new RuntimeAuditCorrelation(
                recordId,
                correlationRef,
                organizationRefHash,
                personRefHash,
                keycloakRefHash,
                orchestratorRefHash,
                openClawRefHash,
                matrixRefHash,
                mcpRefHash,
                domainAuditRefHash,
                occurredAt.toInstant(),
                createdAt.toInstant());
    }
}

interface RuntimeAuditCorrelationJpaRepository
        extends JpaRepository<RuntimeAuditCorrelationJpaEntity, UUID> {

    Optional<RuntimeAuditCorrelationJpaEntity> findByCorrelationRef(
            String correlationRef);
}

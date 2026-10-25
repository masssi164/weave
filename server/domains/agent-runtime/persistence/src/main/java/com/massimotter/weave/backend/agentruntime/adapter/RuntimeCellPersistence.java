package com.massimotter.weave.backend.agentruntime.adapter;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeCellState;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementState;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadBinding;
import com.massimotter.weave.backend.agentruntime.port.StaleRuntimeCellException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Entity
@Table(
        name = "weave_agent_runtime_cells",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "weave_agent_runtime_person_unique",
                        columnNames = {"organization_ref", "person_ref"}),
                @UniqueConstraint(
                        name = "weave_agent_runtime_workload_subject_unique",
                        columnNames = {"workload_issuer", "workload_subject"}),
                @UniqueConstraint(
                        name = "weave_agent_runtime_workload_client_unique",
                        columnNames = {"workload_issuer", "workload_client_id"})
        },
        indexes = @Index(
                name = "weave_agent_runtime_reconcile",
                columnList = "desired_state, observed_state, lease_expires_at"))
class RuntimeCellJpaEntity {

    @Id
    @Column(name = "record_id", nullable = false, updatable = false)
    private UUID recordId;

    @Column(name = "organization_ref", nullable = false, length = 255, updatable = false)
    private String organizationRef;

    @Column(name = "person_ref", nullable = false, length = 255, updatable = false)
    private String personRef;

    @Column(name = "member_issuer", nullable = false, length = 500, updatable = false)
    private String memberIssuer;

    @Column(name = "member_subject", nullable = false, length = 255, updatable = false)
    private String memberSubject;

    @Column(name = "cell_ref", nullable = false, length = 255, updatable = false)
    private String cellRef;

    @Column(name = "workload_issuer", nullable = false, length = 500, updatable = false)
    private String workloadIssuer;

    @Column(name = "workload_subject", nullable = false, length = 255, updatable = false)
    private String workloadSubject;

    @Column(name = "workload_client_id", nullable = false, length = 255, updatable = false)
    private String workloadClientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "workload_authentication_method", nullable = false, length = 64, updatable = false)
    private RuntimeWorkloadBinding.AuthenticationMethod workloadAuthenticationMethod;

    @Column(name = "workload_credential_ref", nullable = false, length = 1000, updatable = false)
    private String workloadCredentialRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "entitlement_state", nullable = false, length = 32)
    private RuntimeEntitlementState entitlementState;

    @Column(name = "entitlement_revision", nullable = false, length = 255)
    private String entitlementRevision;

    @Enumerated(EnumType.STRING)
    @Column(name = "desired_state", nullable = false, length = 32)
    private RuntimeCellState desiredState;

    @Enumerated(EnumType.STRING)
    @Column(name = "observed_state", nullable = false, length = 32)
    private RuntimeCellState observedState;

    @Column(name = "runtime_profile_id", length = 255)
    private String runtimeProfileId;

    @Column(name = "runtime_profile_hash", length = 71)
    private String runtimeProfileHash;

    @Column(name = "workspace_revision", nullable = false, length = 255)
    private String workspaceRevision;

    @Column(name = "workspace_manifest_ref", nullable = false, length = 1000)
    private String workspaceManifestRef;

    @Column(name = "runtime_state_store_ref", nullable = false, length = 1000, updatable = false)
    private String runtimeStateStoreRef;

    @Column(name = "fencing_epoch", nullable = false)
    private long fencingEpoch;

    @Column(name = "lease_id")
    private UUID leaseId;

    @Column(name = "lease_expires_at")
    private OffsetDateTime leaseExpiresAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "audit_ref", nullable = false, length = 255)
    private String auditRef;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected RuntimeCellJpaEntity() {
    }

    static RuntimeCellJpaEntity fromRow(
            RuntimeCellPersistenceMapper.RuntimeCellRow row) {
        RuntimeCellJpaEntity entity = new RuntimeCellJpaEntity();
        entity.recordId = row.recordId();
        entity.organizationRef = row.organizationRef();
        entity.personRef = row.personRef();
        entity.memberIssuer = row.memberIssuer();
        entity.memberSubject = row.memberSubject();
        entity.cellRef = row.cellRef();
        entity.workloadIssuer = row.workloadIssuer();
        entity.workloadSubject = row.workloadSubject();
        entity.workloadClientId = row.workloadClientId();
        entity.workloadAuthenticationMethod = row.workloadAuthenticationMethod();
        entity.workloadCredentialRef = row.workloadCredentialRef();
        entity.entitlementState = row.entitlementState();
        entity.entitlementRevision = row.entitlementRevision();
        entity.desiredState = row.desiredState();
        entity.observedState = row.observedState();
        entity.runtimeProfileId = row.runtimeProfileId();
        entity.runtimeProfileHash = row.runtimeProfileHash();
        entity.workspaceRevision = row.workspaceRevision();
        entity.workspaceManifestRef = row.workspaceManifestRef();
        entity.runtimeStateStoreRef = row.runtimeStateStoreRef();
        entity.fencingEpoch = row.fencingEpoch();
        entity.leaseId = row.leaseId();
        entity.leaseExpiresAt = RuntimePersistenceTime.utc(row.leaseExpiresAt());
        entity.version = row.version();
        entity.auditRef = row.auditRef();
        entity.createdAt = RuntimePersistenceTime.utc(row.createdAt());
        entity.updatedAt = RuntimePersistenceTime.utc(row.updatedAt());
        return entity;
    }

    boolean leaseAvailable(Instant now) {
        return leaseId == null || !leaseExpiresAt.toInstant().isAfter(now);
    }

    boolean sameCurrentLease(UUID requestedLeaseId, Instant now) {
        return Objects.equals(leaseId, requestedLeaseId)
                && leaseExpiresAt != null
                && leaseExpiresAt.toInstant().isAfter(now);
    }

    void acquireLease(UUID requestedLeaseId, Instant now, Instant expiresAt) {
        leaseId = requestedLeaseId;
        leaseExpiresAt = RuntimePersistenceTime.utc(expiresAt);
        fencingEpoch++;
        updatedAt = RuntimePersistenceTime.utc(now);
    }

    boolean renewLease(
            UUID requestedLeaseId,
            long requestedFencingEpoch,
            Instant now,
            Instant expiresAt) {
        if (!sameCurrentLease(requestedLeaseId, now)
                || fencingEpoch != requestedFencingEpoch) {
            return false;
        }
        leaseExpiresAt = RuntimePersistenceTime.utc(expiresAt);
        updatedAt = RuntimePersistenceTime.utc(now);
        return true;
    }

    boolean observe(
            UUID requestedLeaseId,
            long requestedFencingEpoch,
            RuntimeCellState nextObservedState,
            String nextAuditRef,
            Instant now) {
        if (entitlementState != RuntimeEntitlementState.ENTITLED
                || !sameCurrentLease(requestedLeaseId, now)
                || fencingEpoch != requestedFencingEpoch) {
            return false;
        }
        observedState = nextObservedState;
        auditRef = nextAuditRef;
        updatedAt = RuntimePersistenceTime.utc(now);
        return true;
    }

    boolean sameEntitlement(String revision) {
        return entitlementState == RuntimeEntitlementState.ENTITLED
                && Objects.equals(entitlementRevision, revision);
    }

    void bindEntitlement(String revision, String nextAuditRef, Instant now) {
        entitlementState = RuntimeEntitlementState.ENTITLED;
        entitlementRevision = revision;
        desiredState = RuntimeCellState.PROVISIONING;
        runtimeProfileId = null;
        runtimeProfileHash = null;
        clearLeaseAndFence();
        auditRef = nextAuditRef;
        updatedAt = RuntimePersistenceTime.utc(now);
    }

    void transitionDesiredState(
            RuntimeCellState next,
            String nextAuditRef,
            Instant now) {
        desiredState = next;
        auditRef = nextAuditRef;
        updatedAt = RuntimePersistenceTime.utc(now);
    }

    boolean sameRevocation(String revision) {
        return entitlementState == RuntimeEntitlementState.REVOKED
                && Objects.equals(entitlementRevision, revision);
    }

    void revoke(String revision, String nextAuditRef, Instant now) {
        entitlementState = RuntimeEntitlementState.REVOKED;
        entitlementRevision = revision;
        desiredState = RuntimeCellState.REVOKING;
        clearLeaseAndFence();
        auditRef = nextAuditRef;
        updatedAt = RuntimePersistenceTime.utc(now);
    }

    void bindProfile(
            long expectedVersion,
            String profileId,
            String profileHash,
            Instant now) {
        if (version != expectedVersion
                || entitlementState != RuntimeEntitlementState.ENTITLED) {
            throw new StaleRuntimeCellException(
                    "profile activation rejected by stale cell or entitlement");
        }
        runtimeProfileId = profileId;
        runtimeProfileHash = profileHash;
        updatedAt = RuntimePersistenceTime.utc(now);
    }

    boolean hasProfile(String profileId, String profileHash) {
        return Objects.equals(runtimeProfileId, profileId)
                && Objects.equals(runtimeProfileHash, profileHash);
    }

    RuntimeCellState desiredState() {
        return desiredState;
    }

    RuntimeEntitlementState entitlementState() {
        return entitlementState;
    }

    long version() {
        return version;
    }

    RuntimeCellPersistenceMapper.RuntimeCellRow toRow() {
        return new RuntimeCellPersistenceMapper.RuntimeCellRow(
                recordId,
                organizationRef,
                personRef,
                memberIssuer,
                memberSubject,
                cellRef,
                workloadIssuer,
                workloadSubject,
                workloadClientId,
                workloadAuthenticationMethod,
                workloadCredentialRef,
                entitlementState,
                entitlementRevision,
                desiredState,
                observedState,
                runtimeProfileId,
                runtimeProfileHash,
                workspaceRevision,
                workspaceManifestRef,
                runtimeStateStoreRef,
                fencingEpoch,
                leaseId,
                RuntimePersistenceTime.instant(leaseExpiresAt),
                version,
                auditRef,
                createdAt.toInstant(),
                updatedAt.toInstant());
    }

    private void clearLeaseAndFence() {
        leaseId = null;
        leaseExpiresAt = null;
        fencingEpoch++;
    }
}

interface RuntimeCellJpaRepository
        extends JpaRepository<RuntimeCellJpaEntity, UUID> {

    Optional<RuntimeCellJpaEntity> findByOrganizationRefAndPersonRef(
            String organizationRef,
            String personRef);

    Optional<RuntimeCellJpaEntity> findByCellRef(String cellRef);

    Optional<RuntimeCellJpaEntity> findByWorkloadIssuerAndWorkloadSubject(
            String workloadIssuer,
            String workloadSubject);

    List<RuntimeCellJpaEntity> findAllByOrderByCellRef();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select cell from RuntimeCellJpaEntity cell where cell.recordId = :recordId")
    Optional<RuntimeCellJpaEntity> lockByRecordId(
            @Param("recordId") UUID recordId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select cell from RuntimeCellJpaEntity cell where cell.cellRef = :cellRef")
    Optional<RuntimeCellJpaEntity> lockByCellRef(
            @Param("cellRef") String cellRef);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select cell from RuntimeCellJpaEntity cell
            where cell.organizationRef = :organizationRef
              and cell.personRef = :personRef
            """)
    Optional<RuntimeCellJpaEntity> lockByPerson(
            @Param("organizationRef") String organizationRef,
            @Param("personRef") String personRef);
}

final class RuntimePersistenceTime {

    private RuntimePersistenceTime() {
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

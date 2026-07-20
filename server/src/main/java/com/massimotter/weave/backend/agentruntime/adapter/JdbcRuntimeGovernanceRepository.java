package com.massimotter.weave.backend.agentruntime.adapter;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeAuditCorrelation;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementObservation;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementRef;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementState;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeMemberBinding;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeRevocation;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadOwnership;
import com.massimotter.weave.backend.agentruntime.port.RuntimeGovernanceRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** PostgreSQL-backed authority facts with idempotent activation and append-only revocation evidence. */
public final class JdbcRuntimeGovernanceRepository implements RuntimeGovernanceRepository {
    private static final String ENTITLEMENT_DOMAIN = "weave.agent-runtime.entitlement/v1";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public JdbcRuntimeGovernanceRepository(JdbcTemplate jdbc) {
        DataSource dataSource = jdbc == null ? null : jdbc.getDataSource();
        if (dataSource == null) {
            throw new IllegalArgumentException("JdbcRuntimeGovernanceRepository requires a DataSource");
        }
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Override
    public RuntimeEntitlementRef activate(
            RuntimeEntitlementObservation observation,
            String activationRef,
            String auditRef,
            Instant now) {
        Objects.requireNonNull(observation, "observation");
        requireText(activationRef, "activationRef");
        requireText(auditRef, "auditRef");
        Objects.requireNonNull(now, "now");
        if (observation.observedAt().isAfter(now.plusSeconds(1))) {
            throw new IllegalArgumentException("future entitlement observations are rejected");
        }
        return Objects.requireNonNull(transactions.execute(status -> activateTransaction(
                observation, activationRef, auditRef, now)));
    }

    @Override
    public Optional<RuntimeEntitlementRef> findCurrent(String organizationRef, String personRef) {
        requireText(organizationRef, "organizationRef");
        requireText(personRef, "personRef");
        List<RuntimeEntitlementRef> rows = jdbc.query("""
                select * from weave_agent_runtime_entitlements
                 where organization_ref=? and person_ref=?
                 order by case when entitlement_state='ENTITLED' then 0 else 1 end,
                          last_observed_at desc, created_at desc, entitlement_ref desc
                 limit 1
                """, this::mapEntitlement, organizationRef, personRef);
        return rows.stream().findFirst();
    }

    @Override
    public Optional<RuntimeEntitlementRef> findRevision(
            String organizationRef,
            String personRef,
            String entitlementRevision) {
        requireText(organizationRef, "organizationRef");
        requireText(personRef, "personRef");
        requireText(entitlementRevision, "entitlementRevision");
        List<RuntimeEntitlementRef> rows = jdbc.query("""
                select * from weave_agent_runtime_entitlements
                 where organization_ref=? and person_ref=? and entitlement_revision=?
                """, this::mapEntitlement, organizationRef, personRef, entitlementRevision);
        return rows.stream().findFirst();
    }

    @Override
    public Optional<RuntimeEntitlementRef> findEffectiveRevision(
            String organizationRef,
            String personRef,
            String entitlementRevision,
            Instant now) {
        requireText(organizationRef, "organizationRef");
        requireText(personRef, "personRef");
        requireText(entitlementRevision, "entitlementRevision");
        Objects.requireNonNull(now, "now");
        List<RuntimeEntitlementRef> rows = jdbc.query("""
                select * from weave_agent_runtime_entitlements
                 where organization_ref=? and person_ref=? and entitlement_revision=?
                   and entitlement_state='ENTITLED' and effective_at<=? and expires_at>?
                """, this::mapEntitlement, organizationRef, personRef, entitlementRevision, time(now), time(now));
        return rows.stream().findFirst();
    }

    @Override
    public RuntimeRevocation revoke(
            RuntimeEntitlementRef entitlement,
            String cellRef,
            String profileHash,
            String workloadRefHash,
            String reasonCode,
            String actorRefHash,
            String revocationRef,
            String auditCorrelationRef,
            Instant now) {
        Objects.requireNonNull(entitlement, "entitlement");
        RuntimeRevocation proposed = new RuntimeRevocation(
                UUID.randomUUID(), revocationRef, entitlement.organizationRef(), entitlement.personRef(),
                reasonCode, actorRefHash, now, entitlement.entitlementRef(), entitlement.entitlementRevision(),
                cellRef, profileHash, workloadRefHash, auditCorrelationRef, now);
        return Objects.requireNonNull(transactions.execute(status -> {
            int inserted;
            try {
                inserted = jdbc.update("""
                        insert into weave_agent_runtime_revocations
                          (record_id, revocation_ref, organization_ref, person_ref, reason_code, actor_ref_hash,
                           effective_at, entitlement_ref, entitlement_revision, cell_ref, profile_hash,
                           workload_ref_hash, audit_correlation_ref, created_at)
                        values (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                        """, proposed.recordId(), proposed.revocationRef(), proposed.organizationRef(),
                        proposed.personRef(), proposed.reasonCode(), proposed.actorRefHash(),
                        time(proposed.effectiveAt()), proposed.entitlementRef(), proposed.entitlementRevision(),
                        proposed.cellRef(), proposed.profileHash(), proposed.workloadRefHash(),
                        proposed.auditCorrelationRef(), time(proposed.createdAt()));
            } catch (DuplicateKeyException conflict) {
                inserted = 0;
            }
            RuntimeRevocation persisted = inserted == 1 ? proposed : findRevocation(revocationRef)
                    .orElseThrow(() -> new IllegalStateException("revocation conflict could not be resolved"));
            requireSameRevocation(proposed, persisted);
            int updated = jdbc.update("""
                    update weave_agent_runtime_entitlements
                       set entitlement_state='REVOKED', revocation_ref=?, revoked_at=?, audit_ref=?, updated_at=?
                     where entitlement_ref=? and entitlement_revision=? and entitlement_state='ENTITLED'
                    """, persisted.revocationRef(), time(persisted.effectiveAt()), persisted.auditCorrelationRef(),
                    time(now), persisted.entitlementRef(), persisted.entitlementRevision());
            if (updated == 0) {
                RuntimeEntitlementRef current = findByRef(persisted.entitlementRef())
                        .orElseThrow(() -> new IllegalStateException("revoked entitlement disappeared"));
                if (current.state() != RuntimeEntitlementState.REVOKED
                        || !persisted.revocationRef().equals(current.revocationRef())) {
                    throw new IllegalStateException("entitlement was revoked by different evidence");
                }
            }
            return persisted;
        }));
    }

    @Override
    public RuntimeAuditCorrelation appendCorrelation(RuntimeAuditCorrelation correlation) {
        Objects.requireNonNull(correlation, "correlation");
        int inserted;
        try {
            inserted = jdbc.update("""
                    insert into weave_agent_runtime_audit_correlations
                      (record_id, correlation_ref, organization_ref_hash, person_ref_hash, keycloak_ref_hash,
                       orchestrator_ref_hash, openclaw_ref_hash, matrix_ref_hash, mcp_ref_hash,
                       domain_audit_ref_hash, occurred_at, created_at)
                    values (?,?,?,?,?,?,?,?,?,?,?,?)
                    """, correlation.recordId(), correlation.correlationRef(), correlation.organizationRefHash(),
                    correlation.personRefHash(), correlation.keycloakRefHash(), correlation.orchestratorRefHash(),
                    correlation.openClawRefHash(), correlation.matrixRefHash(), correlation.mcpRefHash(),
                    correlation.domainAuditRefHash(), time(correlation.occurredAt()), time(correlation.createdAt()));
        } catch (DuplicateKeyException conflict) {
            inserted = 0;
        }
        if (inserted == 1) {
            return correlation;
        }
        RuntimeAuditCorrelation persisted = findCorrelation(correlation.correlationRef())
                .orElseThrow(() -> new IllegalStateException("audit correlation conflict could not be resolved"));
        if (!sameCorrelation(correlation, persisted)) {
            throw new IllegalStateException("audit correlation reference is bound to different evidence");
        }
        return persisted;
    }

    private RuntimeEntitlementRef activateTransaction(
            RuntimeEntitlementObservation observation,
            String activationRef,
            String auditRef,
            Instant now) {
        Optional<RuntimeEntitlementRef> reusable = findReusable(observation);
        if (reusable.isPresent()) {
            RuntimeEntitlementRef current = reusable.orElseThrow();
            Instant observedAt = current.lastObservedAt().isAfter(observation.observedAt())
                    ? current.lastObservedAt() : observation.observedAt();
            Instant expiresAt = current.expiresAt().isAfter(observation.expiresAt())
                    ? current.expiresAt() : observation.expiresAt();
            jdbc.update("""
                    update weave_agent_runtime_entitlements
                       set last_observed_at=?, expires_at=?, audit_ref=?, updated_at=?
                     where entitlement_ref=? and entitlement_state='ENTITLED'
                       and last_observed_at<=?
                    """, time(observedAt), time(expiresAt), auditRef, time(now), current.entitlementRef(),
                    time(observedAt));
            return findByRef(current.entitlementRef()).orElseThrow();
        }

        String identity = material(observation, activationRef);
        String entitlementRef = "entitlement:" + RuntimeWorkloadOwnership.fingerprint(identity).substring(7);
        String revision = RuntimeWorkloadOwnership.fingerprint(ENTITLEMENT_DOMAIN + "\u0000" + identity);
        RuntimeEntitlementRef proposed = new RuntimeEntitlementRef(
                UUID.randomUUID(), entitlementRef, revision, observation.organizationRef(), observation.personRef(),
                observation.memberBinding(), observation.sourceProvider(), observation.sourceGroupRef(),
                observation.capabilityRevision(), RuntimeEntitlementState.ENTITLED, observation.observedAt(),
                observation.observedAt(), observation.expiresAt(), null, null, auditRef, now, now);
        int inserted;
        try {
            inserted = jdbc.update("""
                    insert into weave_agent_runtime_entitlements
                      (record_id, entitlement_ref, entitlement_revision, organization_ref, person_ref,
                       member_issuer, member_subject, source_provider, source_group_ref, capability_revision,
                       entitlement_state, effective_at, last_observed_at, expires_at, revocation_ref,
                       revoked_at, audit_ref, created_at, updated_at)
                    values (?,?,?,?,?,?,?,?,?,?,'ENTITLED',?,?,?,?,?,?,?,?)
                    """, proposed.recordId(), proposed.entitlementRef(), proposed.entitlementRevision(),
                    proposed.organizationRef(), proposed.personRef(), proposed.memberBinding().issuer(),
                    proposed.memberBinding().subject(), proposed.sourceProvider(), proposed.sourceGroupRef(),
                    proposed.capabilityRevision(), time(proposed.effectiveAt()), time(proposed.lastObservedAt()),
                    time(proposed.expiresAt()), null, null, proposed.auditRef(), time(proposed.createdAt()),
                    time(proposed.updatedAt()));
        } catch (DuplicateKeyException conflict) {
            inserted = 0;
        }
        RuntimeEntitlementRef persisted = inserted == 1 ? proposed : findByRef(entitlementRef)
                .orElseThrow(() -> new IllegalStateException("entitlement activation conflicted with another fact"));
        requireSameEntitlement(proposed, persisted);
        if (persisted.state() != RuntimeEntitlementState.ENTITLED) {
            throw new IllegalStateException("a revoked entitlement activation cannot be replayed");
        }
        return persisted;
    }

    private Optional<RuntimeEntitlementRef> findReusable(RuntimeEntitlementObservation observation) {
        List<RuntimeEntitlementRef> rows = jdbc.query("""
                select * from weave_agent_runtime_entitlements
                 where organization_ref=? and person_ref=? and member_issuer=? and member_subject=?
                   and source_provider=? and source_group_ref=? and capability_revision=?
                   and entitlement_state='ENTITLED'
                 order by last_observed_at desc, created_at desc
                 limit 1
                """, this::mapEntitlement, observation.organizationRef(), observation.personRef(),
                observation.memberBinding().issuer(), observation.memberBinding().subject(),
                observation.sourceProvider(), observation.sourceGroupRef(), observation.capabilityRevision());
        return rows.stream().findFirst();
    }

    private Optional<RuntimeEntitlementRef> findByRef(String entitlementRef) {
        List<RuntimeEntitlementRef> rows = jdbc.query(
                "select * from weave_agent_runtime_entitlements where entitlement_ref=?",
                this::mapEntitlement, entitlementRef);
        return rows.stream().findFirst();
    }

    private Optional<RuntimeRevocation> findRevocation(String revocationRef) {
        List<RuntimeRevocation> rows = jdbc.query(
                "select * from weave_agent_runtime_revocations where revocation_ref=?",
                this::mapRevocation, revocationRef);
        return rows.stream().findFirst();
    }

    private Optional<RuntimeAuditCorrelation> findCorrelation(String correlationRef) {
        List<RuntimeAuditCorrelation> rows = jdbc.query(
                "select * from weave_agent_runtime_audit_correlations where correlation_ref=?",
                this::mapCorrelation, correlationRef);
        return rows.stream().findFirst();
    }

    private RuntimeEntitlementRef mapEntitlement(ResultSet result, int row) throws SQLException {
        return new RuntimeEntitlementRef(
                result.getObject("record_id", UUID.class), result.getString("entitlement_ref"),
                result.getString("entitlement_revision"), result.getString("organization_ref"),
                result.getString("person_ref"), new RuntimeMemberBinding(
                        result.getString("member_issuer"), result.getString("member_subject")),
                result.getString("source_provider"), result.getString("source_group_ref"),
                result.getString("capability_revision"),
                RuntimeEntitlementState.valueOf(result.getString("entitlement_state")),
                instant(result, "effective_at"), instant(result, "last_observed_at"),
                instant(result, "expires_at"), result.getString("revocation_ref"),
                nullableInstant(result, "revoked_at"), result.getString("audit_ref"),
                instant(result, "created_at"), instant(result, "updated_at"));
    }

    private RuntimeRevocation mapRevocation(ResultSet result, int row) throws SQLException {
        return new RuntimeRevocation(
                result.getObject("record_id", UUID.class), result.getString("revocation_ref"),
                result.getString("organization_ref"), result.getString("person_ref"),
                result.getString("reason_code"), result.getString("actor_ref_hash"),
                instant(result, "effective_at"), result.getString("entitlement_ref"),
                result.getString("entitlement_revision"), result.getString("cell_ref"),
                result.getString("profile_hash"), result.getString("workload_ref_hash"),
                result.getString("audit_correlation_ref"), instant(result, "created_at"));
    }

    private RuntimeAuditCorrelation mapCorrelation(ResultSet result, int row) throws SQLException {
        return new RuntimeAuditCorrelation(
                result.getObject("record_id", UUID.class), result.getString("correlation_ref"),
                result.getString("organization_ref_hash"), result.getString("person_ref_hash"),
                result.getString("keycloak_ref_hash"), result.getString("orchestrator_ref_hash"),
                result.getString("openclaw_ref_hash"), result.getString("matrix_ref_hash"),
                result.getString("mcp_ref_hash"), result.getString("domain_audit_ref_hash"),
                instant(result, "occurred_at"), instant(result, "created_at"));
    }

    private static String material(RuntimeEntitlementObservation observation, String activationRef) {
        return observation.organizationRef() + "\u0000" + observation.personRef() + "\u0000"
                + observation.memberBinding().issuer() + "\u0000" + observation.memberBinding().subject() + "\u0000"
                + observation.sourceProvider() + "\u0000" + observation.sourceGroupRef() + "\u0000"
                + observation.capabilityRevision() + "\u0000" + activationRef;
    }

    private static void requireSameEntitlement(RuntimeEntitlementRef expected, RuntimeEntitlementRef actual) {
        if (!expected.entitlementRef().equals(actual.entitlementRef())
                || !expected.entitlementRevision().equals(actual.entitlementRevision())
                || !expected.organizationRef().equals(actual.organizationRef())
                || !expected.personRef().equals(actual.personRef())
                || !expected.memberBinding().equals(actual.memberBinding())
                || !expected.sourceProvider().equals(actual.sourceProvider())
                || !expected.sourceGroupRef().equals(actual.sourceGroupRef())
                || !expected.capabilityRevision().equals(actual.capabilityRevision())) {
            throw new IllegalStateException("entitlement reference is bound to different authority evidence");
        }
    }

    private static void requireSameRevocation(RuntimeRevocation expected, RuntimeRevocation actual) {
        if (!expected.revocationRef().equals(actual.revocationRef())
                || !expected.organizationRef().equals(actual.organizationRef())
                || !expected.personRef().equals(actual.personRef())
                || !expected.reasonCode().equals(actual.reasonCode())
                || !expected.actorRefHash().equals(actual.actorRefHash())
                || !expected.entitlementRef().equals(actual.entitlementRef())
                || !expected.entitlementRevision().equals(actual.entitlementRevision())
                || !expected.cellRef().equals(actual.cellRef())
                || !Objects.equals(expected.profileHash(), actual.profileHash())
                || !expected.workloadRefHash().equals(actual.workloadRefHash())
                || !expected.auditCorrelationRef().equals(actual.auditCorrelationRef())) {
            throw new IllegalStateException("revocation reference is bound to different evidence");
        }
    }

    private static boolean sameCorrelation(RuntimeAuditCorrelation left, RuntimeAuditCorrelation right) {
        return left.correlationRef().equals(right.correlationRef())
                && left.organizationRefHash().equals(right.organizationRefHash())
                && left.personRefHash().equals(right.personRefHash())
                && Objects.equals(left.keycloakRefHash(), right.keycloakRefHash())
                && Objects.equals(left.orchestratorRefHash(), right.orchestratorRefHash())
                && Objects.equals(left.openClawRefHash(), right.openClawRefHash())
                && Objects.equals(left.matrixRefHash(), right.matrixRefHash())
                && Objects.equals(left.mcpRefHash(), right.mcpRefHash())
                && Objects.equals(left.domainAuditRefHash(), right.domainAuditRefHash());
    }

    private static OffsetDateTime time(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        return result.getObject(column, OffsetDateTime.class).toInstant();
    }

    private static Instant nullableInstant(ResultSet result, String column) throws SQLException {
        OffsetDateTime value = result.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 255) {
            throw new IllegalArgumentException(field + " is required and bounded");
        }
    }
}

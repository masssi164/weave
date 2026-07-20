package com.massimotter.weave.backend.agentruntime.adapter;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeCell;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeCellState;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementState;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeMemberBinding;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadBinding;
import com.massimotter.weave.backend.agentruntime.port.RuntimeCellRepository;
import com.massimotter.weave.backend.agentruntime.port.StaleRuntimeCellException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcRuntimeCellRepository implements RuntimeCellRepository {
    private final JdbcTemplate jdbc;

    public JdbcRuntimeCellRepository(JdbcTemplate jdbc) {
        if (jdbc == null || jdbc.getDataSource() == null) {
            throw new IllegalArgumentException("JdbcRuntimeCellRepository requires a JdbcTemplate with a DataSource");
        }
        this.jdbc = jdbc;
    }

    @Override
    public RuntimeCell insert(RuntimeCell cell) {
        jdbc.update("""
                insert into weave_agent_runtime_cells (
                  record_id, organization_ref, person_ref, member_issuer, member_subject, cell_ref,
                  workload_issuer, workload_subject, workload_client_id, workload_authentication_method,
                  workload_credential_ref, entitlement_state, entitlement_revision, desired_state, observed_state,
                  runtime_profile_id, runtime_profile_hash, workspace_revision, workspace_manifest_ref,
                  runtime_state_store_ref, fencing_epoch, lease_id, lease_expires_at, version, audit_ref,
                  created_at, updated_at
                ) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                cell.recordId(), cell.organizationRef(), cell.personRef(), cell.memberBinding().issuer(),
                cell.memberBinding().subject(), cell.cellRef(), cell.workloadBinding().issuer(),
                cell.workloadBinding().subject(), cell.workloadBinding().clientId(),
                cell.workloadBinding().authenticationMethod().name(), cell.workloadBinding().credentialRef(),
                cell.entitlementState().name(), cell.entitlementRevision(), cell.desiredState().name(),
                cell.observedState().name(), cell.runtimeProfileId(), cell.runtimeProfileHash(),
                cell.workspaceRevision(), cell.workspaceManifestRef(), cell.runtimeStateStoreRef(),
                cell.fencingEpoch(), cell.leaseId(), time(cell.leaseExpiresAt()), cell.version(), cell.auditRef(),
                time(cell.createdAt()), time(cell.updatedAt()));
        return cell;
    }

    @Override
    public Optional<RuntimeCell> findByPerson(String organizationRef, String personRef) {
        return one("organization_ref=? and person_ref=?", organizationRef, personRef);
    }

    @Override
    public Optional<RuntimeCell> findByCellRef(String cellRef) {
        return one("cell_ref=?", cellRef);
    }

    @Override
    public List<RuntimeCell> findAll() {
        return List.copyOf(jdbc.query(
                "select * from weave_agent_runtime_cells order by cell_ref",
                this::map));
    }

    @Override
    public RuntimeCell acquireLease(String cellRef, UUID leaseId, Instant now, Instant expiresAt) {
        requireLeaseWindow(now, expiresAt);
        int updated = jdbc.update("""
                update weave_agent_runtime_cells
                   set lease_id=?, lease_expires_at=?, fencing_epoch=fencing_epoch+1,
                       version=version+1, updated_at=?
                 where cell_ref=? and (lease_id is null or lease_expires_at<=?)
                """, leaseId, time(expiresAt), time(now), cellRef, time(now));
        RuntimeCell current = findByCellRef(cellRef)
                .orElseThrow(() -> new StaleRuntimeCellException("runtime cell does not exist"));
        if (updated == 1 || (leaseId.equals(current.leaseId()) && current.leaseExpiresAt().isAfter(now))) {
            return current;
        }
        throw new StaleRuntimeCellException("runtime cell already has a current lease");
    }

    @Override
    public RuntimeCell renewLease(String cellRef, UUID leaseId, long fencingEpoch, Instant now, Instant expiresAt) {
        requireLeaseWindow(now, expiresAt);
        int updated = jdbc.update("""
                update weave_agent_runtime_cells
                   set lease_expires_at=?, version=version+1, updated_at=?
                 where cell_ref=? and lease_id=? and fencing_epoch=? and lease_expires_at>?
                """, time(expiresAt), time(now), cellRef, leaseId, fencingEpoch, time(now));
        if (updated != 1) {
            throw new StaleRuntimeCellException("runtime cell lease is missing, expired, or fenced");
        }
        return findByCellRef(cellRef).orElseThrow();
    }

    @Override
    public RuntimeCell observe(String cellRef, UUID leaseId, long fencingEpoch, RuntimeCellState observedState,
            String auditRef, Instant now) {
        int updated = jdbc.update("""
                update weave_agent_runtime_cells
                   set observed_state=?, audit_ref=?, version=version+1, updated_at=?
                 where cell_ref=? and lease_id=? and fencing_epoch=? and lease_expires_at>?
                   and entitlement_state='ENTITLED'
                """, observedState.name(), auditRef, time(now), cellRef, leaseId, fencingEpoch, time(now));
        if (updated != 1) {
            throw new StaleRuntimeCellException("runtime cell observation rejected by lease, fence, or entitlement");
        }
        return findByCellRef(cellRef).orElseThrow();
    }

    @Override
    public RuntimeCell bindEntitlement(
            String organizationRef,
            String personRef,
            long expectedVersion,
            String entitlementRevision,
            String auditRef,
            Instant now) {
        int updated = jdbc.update("""
                update weave_agent_runtime_cells
                   set entitlement_state='ENTITLED', entitlement_revision=?, desired_state='PROVISIONING',
                       runtime_profile_id=null, runtime_profile_hash=null,
                       lease_id=null, lease_expires_at=null, fencing_epoch=fencing_epoch+1,
                       audit_ref=?, version=version+1, updated_at=?
                 where organization_ref=? and person_ref=? and version=?
                """, entitlementRevision, auditRef, time(now), organizationRef, personRef, expectedVersion);
        RuntimeCell current = findByPerson(organizationRef, personRef)
                .orElseThrow(() -> new StaleRuntimeCellException("runtime cell does not exist"));
        if (updated == 1 || (current.entitlementState() == RuntimeEntitlementState.ENTITLED
                && current.entitlementRevision().equals(entitlementRevision))) {
            return current;
        }
        throw new StaleRuntimeCellException("runtime cell entitlement changed concurrently");
    }

    @Override
    public RuntimeCell revoke(
            String organizationRef, String personRef, String entitlementRevision, String auditRef, Instant now) {
        int updated = jdbc.update("""
                update weave_agent_runtime_cells
                   set entitlement_state='REVOKED', entitlement_revision=?, desired_state='REVOKING',
                       lease_id=null, lease_expires_at=null, fencing_epoch=fencing_epoch+1,
                       audit_ref=?, version=version+1, updated_at=?
                 where organization_ref=? and person_ref=? and entitlement_state<>'REVOKED'
                """, entitlementRevision, auditRef, time(now), organizationRef, personRef);
        RuntimeCell current = findByPerson(organizationRef, personRef)
                .orElseThrow(() -> new StaleRuntimeCellException("runtime cell does not exist"));
        if (updated == 1 || (current.entitlementState() == RuntimeEntitlementState.REVOKED
                && current.entitlementRevision().equals(entitlementRevision))) {
            return current;
        }
        throw new StaleRuntimeCellException("runtime cell was revoked at another entitlement revision");
    }

    private Optional<RuntimeCell> one(String where, Object... arguments) {
        List<RuntimeCell> rows = jdbc.query(
                "select * from weave_agent_runtime_cells where " + where,
                this::map,
                arguments);
        if (rows.size() > 1) {
            throw new IllegalStateException("authoritative runtime binding is ambiguous");
        }
        return rows.stream().findFirst();
    }

    private RuntimeCell map(ResultSet result, int row) throws SQLException {
        return new RuntimeCell(
                result.getObject("record_id", UUID.class),
                result.getString("organization_ref"),
                result.getString("person_ref"),
                new RuntimeMemberBinding(result.getString("member_issuer"), result.getString("member_subject")),
                result.getString("cell_ref"),
                new RuntimeWorkloadBinding(
                        result.getString("workload_issuer"),
                        result.getString("workload_subject"),
                        result.getString("workload_client_id"),
                        RuntimeWorkloadBinding.AuthenticationMethod.valueOf(
                                result.getString("workload_authentication_method")),
                        result.getString("workload_credential_ref")),
                RuntimeEntitlementState.valueOf(result.getString("entitlement_state")),
                result.getString("entitlement_revision"),
                RuntimeCellState.valueOf(result.getString("desired_state")),
                RuntimeCellState.valueOf(result.getString("observed_state")),
                result.getString("runtime_profile_id"),
                result.getString("runtime_profile_hash"),
                result.getString("workspace_revision"),
                result.getString("workspace_manifest_ref"),
                result.getString("runtime_state_store_ref"),
                result.getLong("fencing_epoch"),
                result.getObject("lease_id", UUID.class),
                instant(result, "lease_expires_at"),
                result.getLong("version"),
                result.getString("audit_ref"),
                instant(result, "created_at"),
                instant(result, "updated_at"));
    }

    private static void requireLeaseWindow(Instant now, Instant expiresAt) {
        if (now == null || expiresAt == null || !expiresAt.isAfter(now)) {
            throw new IllegalArgumentException("lease expiry must be after the current time");
        }
    }

    private static OffsetDateTime time(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        OffsetDateTime value = result.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}

package com.massimotter.weave.backend.agentruntime.adapter;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeCell;
import com.massimotter.weave.backend.agentruntime.domain.SignedRuntimeProfile;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileRepository;
import com.massimotter.weave.backend.agentruntime.port.StaleRuntimeCellException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class JdbcRuntimeProfileRepository implements RuntimeProfileRepository {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public JdbcRuntimeProfileRepository(JdbcTemplate jdbc) {
        DataSource dataSource = jdbc == null ? null : jdbc.getDataSource();
        if (dataSource == null) {
            throw new IllegalArgumentException("JdbcRuntimeProfileRepository requires a DataSource");
        }
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Override
    public SignedRuntimeProfile activate(RuntimeCell expectedCell, SignedRuntimeProfile profile, Instant now) {
        if (expectedCell == null || profile == null || now == null
                || !expectedCell.cellRef().equals(profile.cellRef())) {
            throw new IllegalArgumentException("profile activation requires its exact runtime cell");
        }
        if (now.isBefore(profile.issuedAt()) || !now.isBefore(profile.expiresAt())) {
            throw new IllegalArgumentException("only a currently valid RuntimeProfile can be activated");
        }
        return transactions.execute(status -> {
            insertOrVerifyPayload(expectedCell, profile, now);
            insertOrVerifySignature(profile, now);
            int selected = jdbc.update("""
                    update weave_agent_runtime_profiles set selected_key_id=?
                     where profile_hash=? and revoked_at is null
                    """, profile.keyId(), profile.profileHash());
            if (selected != 1) {
                throw new IllegalStateException("revoked RuntimeProfile cannot select a signing key");
            }
            RuntimeCellBinding current = currentCell(expectedCell.cellRef());
            if (profile.profileHash().equals(current.profileHash())
                    && profile.profileId().equals(current.profileId())) {
                return profile;
            }
            int updated = jdbc.update("""
                    update weave_agent_runtime_cells
                       set runtime_profile_id=?, runtime_profile_hash=?, version=version+1, updated_at=?
                     where record_id=? and version=? and entitlement_state='ENTITLED'
                    """, profile.profileId(), profile.profileHash(), time(now),
                    expectedCell.recordId(), expectedCell.version());
            if (updated != 1) {
                throw new StaleRuntimeCellException("profile activation rejected by stale cell or entitlement");
            }
            return profile;
        });
    }

    @Override
    public Optional<SignedRuntimeProfile> findCurrentForWorkload(
            String profileHash,
            String workloadIssuer,
            String workloadSubject,
            String workloadClientId,
            Instant now) {
        List<SignedRuntimeProfile> rows = jdbc.query("""
                select p.*, s.key_id, s.protected_header, s.signature
                  from weave_agent_runtime_profiles p
                  join weave_agent_runtime_profile_signatures s
                    on s.profile_hash=p.profile_hash and s.key_id=p.selected_key_id
                  join weave_agent_runtime_cells c on c.cell_ref=p.cell_ref
                 where p.profile_hash=? and c.runtime_profile_hash=p.profile_hash
                   and c.runtime_profile_id=p.profile_id and c.entitlement_state='ENTITLED'
                   and p.revoked_at is null and p.issued_at<=? and p.expires_at>?
                   and c.workload_issuer=? and c.workload_subject=? and c.workload_client_id=?
                """, this::map, profileHash, time(now), time(now),
                workloadIssuer, workloadSubject, workloadClientId);
        return rows.stream().findFirst();
    }

    @Override
    public void revokeCurrent(String cellRef, String revocationCode, Instant now) {
        if (cellRef == null || cellRef.isBlank() || revocationCode == null || revocationCode.isBlank() || now == null) {
            throw new IllegalArgumentException("profile revocation metadata is required");
        }
        jdbc.update("""
                update weave_agent_runtime_profiles
                   set revoked_at=?, revocation_code=?
                 where profile_hash=(select runtime_profile_hash from weave_agent_runtime_cells where cell_ref=?)
                   and revoked_at is null
                """, time(now), revocationCode, cellRef);
    }

    private void insertOrVerifyPayload(RuntimeCell cell, SignedRuntimeProfile profile, Instant now) {
        int inserted = jdbc.update("""
                insert into weave_agent_runtime_profiles
                  (profile_hash, profile_id, cell_ref, organization_ref, person_ref, payload,
                   selected_key_id, issued_at, expires_at, revoked_at, revocation_code, created_at)
                values (?,?,?,?,?,?,?,?,?,null,null,?)
                on conflict do nothing
                """, profile.profileHash(), profile.profileId(), profile.cellRef(),
                cell.organizationRef(), cell.personRef(), profile.payload(), profile.keyId(),
                time(profile.issuedAt()), time(profile.expiresAt()), time(now));
        if (inserted == 0) {
            Integer exact = jdbc.queryForObject("""
                    select count(*) from weave_agent_runtime_profiles
                     where profile_hash=? and profile_id=? and cell_ref=? and organization_ref=?
                       and person_ref=? and payload=? and issued_at=? and expires_at=? and revoked_at is null
                    """, Integer.class, profile.profileHash(), profile.profileId(), profile.cellRef(),
                    cell.organizationRef(), cell.personRef(), profile.payload(),
                    time(profile.issuedAt()), time(profile.expiresAt()));
            if (exact == null || exact != 1) {
                throw new IllegalStateException("RuntimeProfile hash or id is already bound to other semantics");
            }
        }
    }

    private void insertOrVerifySignature(SignedRuntimeProfile profile, Instant now) {
        int inserted = jdbc.update("""
                insert into weave_agent_runtime_profile_signatures
                  (profile_hash, key_id, protected_header, signature, created_at)
                values (?,?,?,?,?)
                on conflict do nothing
                """, profile.profileHash(), profile.keyId(), profile.protectedHeader(),
                profile.signature(), time(now));
        if (inserted == 0) {
            Integer exact = jdbc.queryForObject("""
                    select count(*) from weave_agent_runtime_profile_signatures
                     where profile_hash=? and key_id=? and protected_header=? and signature=?
                    """, Integer.class, profile.profileHash(), profile.keyId(),
                    profile.protectedHeader(), profile.signature());
            if (exact == null || exact != 1) {
                throw new IllegalStateException("RuntimeProfile key id is already bound to another signature");
            }
        }
    }

    private RuntimeCellBinding currentCell(String cellRef) {
        return jdbc.queryForObject("""
                select runtime_profile_id, runtime_profile_hash
                  from weave_agent_runtime_cells where cell_ref=?
                """, (result, row) -> new RuntimeCellBinding(
                        result.getString("runtime_profile_id"), result.getString("runtime_profile_hash")), cellRef);
    }

    private SignedRuntimeProfile map(ResultSet result, int row) throws SQLException {
        return new SignedRuntimeProfile(
                result.getString("protected_header"),
                result.getString("payload"),
                result.getString("signature"),
                result.getString("profile_hash"),
                result.getString("profile_id"),
                result.getString("cell_ref"),
                result.getString("key_id"),
                instant(result, "issued_at"),
                instant(result, "expires_at"));
    }

    private static OffsetDateTime time(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        return result.getObject(column, OffsetDateTime.class).toInstant();
    }

    private record RuntimeCellBinding(String profileId, String profileHash) {
    }
}

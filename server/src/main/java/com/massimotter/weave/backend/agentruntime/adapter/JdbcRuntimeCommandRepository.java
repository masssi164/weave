package com.massimotter.weave.backend.agentruntime.adapter;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeCommandReceipt;
import com.massimotter.weave.backend.agentruntime.port.RuntimeCommandConflictException;
import com.massimotter.weave.backend.agentruntime.port.RuntimeCommandRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcRuntimeCommandRepository implements RuntimeCommandRepository {
    private final JdbcTemplate jdbc;

    public JdbcRuntimeCommandRepository(JdbcTemplate jdbc) {
        if (jdbc == null || jdbc.getDataSource() == null) {
            throw new IllegalArgumentException("JdbcRuntimeCommandRepository requires a JdbcTemplate with a DataSource");
        }
        this.jdbc = jdbc;
    }

    @Override
    public RuntimeCommandReceipt claim(
            String organizationRef,
            String personRef,
            String idempotencyKey,
            String command,
            String proposedCellRef,
            String auditRef,
            Instant now) {
        try {
            jdbc.update("""
                    insert into weave_agent_runtime_commands
                      (organization_ref, person_ref, idempotency_key, command, status, cell_ref,
                       runtime_version, audit_ref, failure_code, created_at, updated_at)
                    values (?,?,?,?,?,?,?,?,?,?,?)
                    """, organizationRef, personRef, idempotencyKey, command,
                    RuntimeCommandReceipt.Status.STARTED.name(), proposedCellRef, null, auditRef, null,
                    time(now), time(now));
        } catch (DuplicateKeyException ignored) {
            // The unique key is the idempotency boundary. Validate its immutable command below.
        }
        RuntimeCommandReceipt receipt = find(organizationRef, personRef, idempotencyKey);
        if (!receipt.command().equals(command) || !receipt.cellRef().equals(proposedCellRef)) {
            throw new RuntimeCommandConflictException("idempotency key is already bound to another command");
        }
        return receipt;
    }

    @Override
    public RuntimeCommandReceipt complete(RuntimeCommandReceipt receipt, long runtimeVersion, Instant now) {
        int updated = jdbc.update("""
                update weave_agent_runtime_commands
                   set status='COMPLETED', runtime_version=?, failure_code=null, updated_at=?
                 where organization_ref=? and person_ref=? and idempotency_key=?
                   and command=? and status<>'COMPLETED'
                """, runtimeVersion, time(now), receipt.organizationRef(), receipt.personRef(),
                receipt.idempotencyKey(), receipt.command());
        RuntimeCommandReceipt current = find(
                receipt.organizationRef(), receipt.personRef(), receipt.idempotencyKey());
        if (updated == 0 && (current.status() != RuntimeCommandReceipt.Status.COMPLETED
                || !Long.valueOf(runtimeVersion).equals(current.runtimeVersion()))) {
            throw new RuntimeCommandConflictException("command completion conflicts with the stored receipt");
        }
        return current;
    }

    @Override
    public RuntimeCommandReceipt fail(RuntimeCommandReceipt receipt, String failureCode, Instant now) {
        jdbc.update("""
                update weave_agent_runtime_commands
                   set status='FAILED', failure_code=?, updated_at=?
                 where organization_ref=? and person_ref=? and idempotency_key=? and status<>'COMPLETED'
                """, failureCode, time(now), receipt.organizationRef(), receipt.personRef(), receipt.idempotencyKey());
        return find(receipt.organizationRef(), receipt.personRef(), receipt.idempotencyKey());
    }

    private RuntimeCommandReceipt find(String organizationRef, String personRef, String idempotencyKey) {
        List<RuntimeCommandReceipt> rows = jdbc.query("""
                select * from weave_agent_runtime_commands
                 where organization_ref=? and person_ref=? and idempotency_key=?
                """, this::map, organizationRef, personRef, idempotencyKey);
        if (rows.size() != 1) {
            throw new IllegalStateException("runtime command receipt is missing or ambiguous");
        }
        return rows.get(0);
    }

    private RuntimeCommandReceipt map(ResultSet result, int row) throws SQLException {
        Long runtimeVersion = result.getObject("runtime_version") == null
                ? null
                : result.getLong("runtime_version");
        return new RuntimeCommandReceipt(
                result.getString("organization_ref"),
                result.getString("person_ref"),
                result.getString("idempotency_key"),
                result.getString("command"),
                RuntimeCommandReceipt.Status.valueOf(result.getString("status")),
                result.getString("cell_ref"),
                runtimeVersion,
                result.getString("audit_ref"),
                result.getString("failure_code"),
                instant(result, "created_at"),
                instant(result, "updated_at"));
    }

    private static OffsetDateTime time(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        return result.getObject(column, OffsetDateTime.class).toInstant();
    }
}

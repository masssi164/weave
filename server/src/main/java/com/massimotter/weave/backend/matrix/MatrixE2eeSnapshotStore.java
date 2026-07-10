package com.massimotter.weave.backend.matrix;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class MatrixE2eeSnapshotStore {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public MatrixE2eeSnapshotStore(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        this.transactionTemplate = jdbcTemplate == null || jdbcTemplate.getDataSource() == null
                ? null
                : new TransactionTemplate(new DataSourceTransactionManager(jdbcTemplate.getDataSource()));
    }

    public Optional<SnapshotDocument> load(String tenantId) {
        if (jdbcTemplate == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
                        "select sequence_value, payload_json from weave_matrix_e2ee_snapshots where tenant_id = ?",
                        (rs, rowNum) -> new SnapshotDocument(rs.getLong("sequence_value"), rs.getString("payload_json")),
                        tenantId)
                .stream()
                .findFirst();
    }

    public void save(String tenantId, long sequence, String payloadJson) {
        if (jdbcTemplate == null) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update("delete from weave_matrix_e2ee_snapshots where tenant_id = ?", tenantId);
            jdbcTemplate.update(
                    "insert into weave_matrix_e2ee_snapshots "
                            + "(tenant_id, sequence_value, payload_json, updated_at_utc) values (?, ?, ?, ?)",
                    tenantId,
                    sequence,
                    payloadJson,
                    OffsetDateTime.now(ZoneOffset.UTC));
        });
    }

    public boolean durable() {
        return jdbcTemplate != null;
    }

    public record SnapshotDocument(long sequence, String payloadJson) {
    }
}

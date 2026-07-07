package com.massimotter.weave.backend.service.migration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
@ConditionalOnProperty(name = "weave.migration.evidence.storage.mode", havingValue = "jdbc")
class JdbcMigrationRunEvidenceRepository implements MigrationRunEvidenceRepository {

    private static final TypeReference<Map<String, Integer>> OBJECT_COUNTS = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    JdbcMigrationRunEvidenceRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        DataSource dataSource = jdbcTemplate.getDataSource();
        if (dataSource == null) {
            throw new IllegalArgumentException(
                    "JdbcMigrationRunEvidenceRepository requires a JdbcTemplate with a DataSource.");
        }
        this.transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Override
    public void save(MigrationRunEvidence evidence) {
        if (evidence == null) {
            throw new IllegalArgumentException("Migration run evidence must not be null.");
        }
        try {
            transactionTemplate.executeWithoutResult(status -> {
                jdbcTemplate.update(
                        "delete from weave_migration_run_evidence where run_id = ? and domain_key = ?",
                        evidence.runId(),
                        evidence.domainKey());
                jdbcTemplate.update(
                        "insert into weave_migration_run_evidence "
                                + "(run_id, domain_key, lifecycle, object_counts_json, content_hashes_json, "
                                + "audit_refs_json, artifact_refs_json, provider_diagnostics_json, "
                                + "identity_mapping_complete, audit_sink_available, admin_approved, "
                                + "recorded_at_utc, expires_at_utc) "
                                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        evidence.runId(),
                        evidence.domainKey(),
                        evidence.lifecycle(),
                        json(evidence.objectCounts()),
                        json(evidence.contentHashes()),
                        json(evidence.auditRefs()),
                        json(evidence.artifactRefs()),
                        json(evidence.providerDiagnostics()),
                        evidence.identityMappingComplete(),
                        evidence.auditSinkAvailable(),
                        evidence.adminApproved(),
                        offset(evidence.recordedAt()),
                        offset(evidence.expiresAt()));
            });
        } catch (DataAccessException exception) {
            throw new MigrationRunEvidenceStoreException("Failed to persist durable migration run evidence.", exception);
        }
    }

    @Override
    public Optional<MigrationRunEvidence> findCurrent(String runId, String domainKey, Instant now) {
        try {
            return jdbcTemplate.query(
                            "select run_id, domain_key, lifecycle, object_counts_json, content_hashes_json, "
                                    + "audit_refs_json, artifact_refs_json, provider_diagnostics_json, "
                                    + "identity_mapping_complete, audit_sink_available, admin_approved, "
                                    + "recorded_at_utc, expires_at_utc "
                                    + "from weave_migration_run_evidence where run_id = ? and domain_key = ?",
                            (rs, rowNum) -> mapEvidence(rs),
                            runId,
                            domainKey)
                    .stream()
                    .filter(evidence -> !evidence.expired(now))
                    .findFirst();
        } catch (DataAccessException exception) {
            throw new MigrationRunEvidenceStoreException("Failed to load durable migration run evidence.", exception);
        }
    }

    String persistencePosture() {
        return "durable-relational-flyway";
    }

    private MigrationRunEvidence mapEvidence(ResultSet rs) throws SQLException {
        OffsetDateTime expiresAt = rs.getObject("expires_at_utc", OffsetDateTime.class);
        return new MigrationRunEvidence(
                rs.getString("run_id"),
                rs.getString("domain_key"),
                rs.getString("lifecycle"),
                read(rs.getString("object_counts_json"), OBJECT_COUNTS, Map.of()),
                read(rs.getString("content_hashes_json"), STRING_LIST, List.of()),
                read(rs.getString("audit_refs_json"), STRING_LIST, List.of()),
                read(rs.getString("artifact_refs_json"), STRING_MAP, Map.of()),
                read(rs.getString("provider_diagnostics_json"), STRING_LIST, List.of()),
                rs.getBoolean("identity_mapping_complete"),
                rs.getBoolean("audit_sink_available"),
                rs.getBoolean("admin_approved"),
                rs.getObject("recorded_at_utc", OffsetDateTime.class).toInstant(),
                expiresAt == null ? null : expiresAt.toInstant());
    }

    private OffsetDateTime offset(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new MigrationRunEvidenceStoreException("Failed to serialize durable migration run evidence.", exception);
        }
    }

    private <T> T read(String json, TypeReference<T> type, T fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new MigrationRunEvidenceStoreException("Failed to load durable migration run evidence.", exception);
        }
    }
}

package com.massimotter.weave.backend.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static java.util.Objects.requireNonNull;

/**
 * Durable append-only relational audit sink for support-safe control-plane evidence.
 */
public final class JdbcAuditEventPublisher implements AuditEventPublisher {

    private static final TypeReference<Map<String, Object>> AUDIT_PAYLOAD = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    public JdbcAuditEventPublisher(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, new ObjectMapper().findAndRegisterModules());
    }

    JdbcAuditEventPublisher(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        DataSource dataSource = jdbcTemplate.getDataSource();
        if (dataSource == null) {
            throw new IllegalArgumentException("JdbcAuditEventPublisher requires a JdbcTemplate with a DataSource.");
        }
        this.transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Override
    public void publish(AuditEvent event) {
        AuditEvent safeEvent = requireNonNull(event, "event must not be null");
        transactionTemplate.executeWithoutResult(status -> jdbcTemplate.update(
                "insert into weave_audit_events "
                        + "(tenant_id, context_id, actor_ref, source_ref, action, occurred_at_utc, "
                        + "idempotency_key, redaction_level, payload_json) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                safeEvent.tenantId(),
                safeEvent.contextId(),
                safeEvent.actorRef(),
                safeEvent.sourceRef(),
                safeEvent.action().name(),
                OffsetDateTime.ofInstant(safeEvent.occurredAt(), ZoneOffset.UTC),
                safeEvent.idempotencyKey(),
                safeEvent.redactionLevel().name(),
                payloadJson(safeEvent.payload())));
    }

    public List<AuditEvent> events() {
        return jdbcTemplate.query(
                "select tenant_id, context_id, actor_ref, source_ref, action, occurred_at_utc, "
                        + "idempotency_key, redaction_level, payload_json "
                        + "from weave_audit_events order by sequence_id",
                (rs, rowNum) -> mapEvent(rs));
    }

    public String persistencePosture() {
        return "durable-relational-flyway";
    }

    private AuditEvent mapEvent(ResultSet rs) throws SQLException {
        return new AuditEvent(
                rs.getString("tenant_id"),
                rs.getString("context_id"),
                rs.getString("actor_ref"),
                rs.getString("source_ref"),
                AuditAction.valueOf(rs.getString("action")),
                rs.getObject("occurred_at_utc", OffsetDateTime.class).toInstant(),
                rs.getString("idempotency_key"),
                AuditRedactionLevel.valueOf(rs.getString("redaction_level")),
                payload(rs.getString("payload_json")));
    }

    private String payloadJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new AuditRequiredException("Failed to serialize durable audit payload.", exception);
        }
    }

    private Map<String, Object> payload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payloadJson, AUDIT_PAYLOAD);
        } catch (JsonProcessingException exception) {
            throw new AuditRequiredException("Failed to load durable audit payload.", exception);
        }
    }
}


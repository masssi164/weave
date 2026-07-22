package com.massimotter.weave.backend.operation.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.massimotter.weave.backend.operation.port.OperationIntentRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public final class JdbcOperationIntentRepository implements OperationIntentRepository {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactions;

    public JdbcOperationIntentRepository(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this.jdbc = java.util.Objects.requireNonNull(jdbc, "jdbc must not be null");
        this.objectMapper = java.util.Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.transactions = new TransactionTemplate(
                java.util.Objects.requireNonNull(transactionManager, "transactionManager must not be null"));
    }

    @Override
    public Optional<OperationIntent> findByOperationRef(String operationRef) {
        return query("where operation_ref = ?", operationRef);
    }

    @Override
    public Optional<OperationIntent> findByIdempotencyKey(String organizationRef, String idempotencyKey) {
        return query("where organization_ref = ? and idempotency_key = ?", organizationRef, idempotencyKey);
    }

    @Override
    public CreateResult create(OperationIntent intent, OperationOutboxEvent event) {
        try {
            return transactions.execute(status -> {
                ProjectionColumns projection = ProjectionColumns.from(intent.projection());
                ActorColumns actor = ActorColumns.from(intent.actor());
                jdbc.update("""
                        insert into weave_operation_intents (
                          operation_ref, intent_version, idempotency_key, organization_ref,
                          actor_kind, person_ref, subject_ref, cell_ref, client_ref, profile_revision, fencing_epoch,
                          domain_key, projection_kind, projection_value_1, projection_value_2, projection_value_3,
                          action_digest, canonical_arguments_digest, object_refs_json, policy_revision,
                          entitlement_revision, provider_binding_revision, intent_state, initial_outbox_ref,
                          provider_correlation_hash, reconciliation_attempts, reconciliation_outcome,
                          reconciliation_last_attempt_at_utc, reconciliation_result_digest,
                          result_digest, audit_ref, created_at_utc, updated_at_utc)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                        intent.operationRef(), OperationIntent.VERSION, intent.idempotencyKey(), intent.organizationRef(),
                        actor.kind(), actor.personRef(), actor.subjectRef(), actor.cellRef(), actor.clientRef(),
                        actor.profileRevision(), actor.fencingEpoch(), intent.domain(), projection.kind(),
                        projection.value1(), projection.value2(), projection.value3(), intent.actionDigest(),
                        intent.canonicalArgumentsDigest(), json(intent.objectRefs()), intent.policyRevision(),
                        intent.entitlementRevision(), intent.providerBindingRevision(), intent.state().name(),
                        intent.outboxRef(), intent.providerCorrelationHash(), reconciliationAttempts(intent),
                        reconciliationOutcome(intent), reconciliationLastAttempt(intent), reconciliationResult(intent),
                        intent.resultDigest(), intent.auditRef(), timestamp(intent.createdAt()), timestamp(intent.updatedAt()));
                insertOutbox(event);
                return new CreateResult(findByOperationRef(intent.operationRef()).orElseThrow(), true);
            });
        } catch (DuplicateKeyException duplicate) {
            return new CreateResult(
                    findByIdempotencyKey(intent.organizationRef(), intent.idempotencyKey())
                            .orElseThrow(() -> duplicate),
                    false);
        }
    }

    @Override
    public OperationIntent update(OperationIntent expected, OperationIntent intent, OperationOutboxEvent event) {
        return transactions.execute(status -> {
            int updated = jdbc.update("""
                    update weave_operation_intents set intent_state = ?, provider_correlation_hash = ?,
                      reconciliation_attempts = ?, reconciliation_outcome = ?,
                      reconciliation_last_attempt_at_utc = ?, reconciliation_result_digest = ?,
                      result_digest = ?, audit_ref = ?, updated_at_utc = ?
                    where operation_ref = ? and updated_at_utc = ?
                    """,
                    intent.state().name(), intent.providerCorrelationHash(), reconciliationAttempts(intent),
                    reconciliationOutcome(intent), reconciliationLastAttempt(intent), reconciliationResult(intent),
                    intent.resultDigest(), intent.auditRef(), timestamp(intent.updatedAt()), intent.operationRef(),
                    timestamp(expected.updatedAt()));
            if (updated != 1) {
                throw new ConcurrentOperationUpdateException(intent.operationRef());
            }
            insertOutbox(event);
            return findByOperationRef(intent.operationRef()).orElseThrow();
        });
    }

    @Override
    public List<OperationIntent> leaseReconciliationBatch(Instant now, int limit, Instant leaseUntil) {
        int bounded = Math.max(1, Math.min(limit, 100));
        return transactions.execute(status -> {
            List<String> refs = jdbc.query("""
                    select operation_ref from weave_operation_intents
                    where intent_state in ('AMBIGUOUS', 'RECONCILING')
                      and (reconciliation_lease_until_utc is null or reconciliation_lease_until_utc < ?)
                      and reconciliation_attempts < reconciliation_max_attempts
                    order by updated_at_utc, operation_ref
                    for update skip locked limit ?
                    """, (rs, row) -> rs.getString(1), timestamp(now), bounded);
            for (String ref : refs) {
                jdbc.update("""
                        update weave_operation_intents set intent_state = 'RECONCILING',
                          reconciliation_attempts = reconciliation_attempts + 1,
                          reconciliation_outcome = 'PENDING', reconciliation_last_attempt_at_utc = ?,
                          reconciliation_lease_until_utc = ?, updated_at_utc = ? where operation_ref = ?
                        """, timestamp(now), timestamp(leaseUntil), timestamp(now), ref);
            }
            return refs.stream().map(ref -> findByOperationRef(ref).orElseThrow()).toList();
        });
    }

    private Optional<OperationIntent> query(String whereClause, Object... arguments) {
        return jdbc.query("select * from weave_operation_intents " + whereClause, this::map, arguments)
                .stream().findFirst();
    }

    private OperationIntent map(ResultSet rs, int row) throws SQLException {
        Reconciliation reconciliation = rs.getString("reconciliation_outcome") == null ? null : new Reconciliation(
                rs.getInt("reconciliation_attempts"),
                ReconciliationOutcome.valueOf(rs.getString("reconciliation_outcome")),
                instant(rs, "reconciliation_last_attempt_at_utc"),
                rs.getString("reconciliation_result_digest"));
        return new OperationIntent(
                rs.getString("operation_ref"), rs.getString("idempotency_key"), rs.getString("organization_ref"),
                actor(rs), rs.getString("domain_key"), projection(rs), rs.getString("action_digest"),
                rs.getString("canonical_arguments_digest"), stringList(rs.getString("object_refs_json")),
                rs.getString("policy_revision"), rs.getString("entitlement_revision"),
                rs.getLong("provider_binding_revision"), State.valueOf(rs.getString("intent_state")),
                rs.getString("initial_outbox_ref"), rs.getString("provider_correlation_hash"), reconciliation,
                rs.getString("result_digest"), rs.getString("audit_ref"), instant(rs, "created_at_utc"),
                instant(rs, "updated_at_utc"));
    }

    private Actor actor(ResultSet rs) throws SQLException {
        return switch (rs.getString("actor_kind")) {
            case "human" -> new HumanActor(rs.getString("person_ref"), rs.getString("subject_ref"));
            case "weaver-workload" -> new WorkloadActor(
                    rs.getString("person_ref"), rs.getString("cell_ref"), rs.getString("client_ref"),
                    rs.getLong("profile_revision"), rs.getLong("fencing_epoch"));
            default -> throw new SQLException("unknown operation actor kind");
        };
    }

    private Projection projection(ResultSet rs) throws SQLException {
        return switch (rs.getString("projection_kind")) {
            case "protocol" -> new ProtocolProjection(value(rs, 1), value(rs, 2), value(rs, 3));
            case "admin-api" -> new AdminApiProjection(value(rs, 1), value(rs, 2));
            case "mcp" -> new McpProjection(value(rs, 1), value(rs, 2));
            case "internal" -> new InternalProjection(value(rs, 1), value(rs, 2));
            default -> throw new SQLException("unknown operation projection kind");
        };
    }

    private String value(ResultSet rs, int index) throws SQLException {
        return rs.getString("projection_value_" + index);
    }

    private void insertOutbox(OperationOutboxEvent event) {
        jdbc.update("""
                insert into weave_operation_outbox
                  (outbox_ref, operation_ref, event_type, payload_json, delivery_state, attempt_count, created_at_utc)
                values (?, ?, ?, ?, 'PENDING', 0, ?)
                """, event.outboxRef(), event.operationRef(), event.eventType(), event.payloadJson(),
                timestamp(event.createdAt()));
    }

    private String json(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("operation object references are not serializable", exception);
        }
    }

    private List<String> stringList(String value) {
        try {
            return objectMapper.readValue(value, STRING_LIST);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("stored operation object references are invalid", exception);
        }
    }

    private Integer reconciliationAttempts(OperationIntent intent) {
        return intent.reconciliation() == null ? 0 : intent.reconciliation().attempts();
    }

    private String reconciliationOutcome(OperationIntent intent) {
        return intent.reconciliation() == null ? null : intent.reconciliation().outcome().name();
    }

    private OffsetDateTime reconciliationLastAttempt(OperationIntent intent) {
        return intent.reconciliation() == null || intent.reconciliation().lastAttemptAt() == null
                ? null : timestamp(intent.reconciliation().lastAttemptAt());
    }

    private String reconciliationResult(OperationIntent intent) {
        return intent.reconciliation() == null ? null : intent.reconciliation().resultDigest();
    }

    private OffsetDateTime timestamp(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private record ActorColumns(
            String kind, String personRef, String subjectRef, String cellRef, String clientRef,
            Long profileRevision, Long fencingEpoch) {
        static ActorColumns from(Actor actor) {
            return switch (actor) {
                case HumanActor human -> new ActorColumns(
                        "human", human.personRef(), human.subjectRef(), null, null, null, null);
                case WorkloadActor workload -> new ActorColumns(
                        "weaver-workload", workload.personRef(), null, workload.cellRef(), workload.clientRef(),
                        workload.profileRevision(), workload.fencingEpoch());
            };
        }
    }

    private record ProjectionColumns(String kind, String value1, String value2, String value3) {
        static ProjectionColumns from(Projection projection) {
            return switch (projection) {
                case ProtocolProjection protocol -> new ProjectionColumns(
                        protocol.kind(), protocol.protocol(), protocol.operation(), protocol.profileVersion());
                case AdminApiProjection admin -> new ProjectionColumns(
                        admin.kind(), admin.operationId(), admin.contractVersion(), null);
                case McpProjection mcp -> new ProjectionColumns(
                        mcp.kind(), mcp.toolName(), mcp.toolContractVersion(), null);
                case InternalProjection internal -> new ProjectionColumns(
                        internal.kind(), internal.useCase(), internal.contractVersion(), null);
            };
        }
    }

    public static final class ConcurrentOperationUpdateException extends RuntimeException {
        public ConcurrentOperationUpdateException(String operationRef) {
            super("operation intent changed concurrently: " + operationRef);
        }
    }
}

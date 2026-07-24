package com.massimotter.weave.backend.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.PersistenceException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;

import static java.util.Objects.requireNonNull;

/**
 * Durable append-only relational audit sink for support-safe control-plane evidence.
 *
 * <p>Retries first reconcile against the unique organization/idempotency tuple. A concurrent
 * winner is read after the failed insert transaction and must be byte-for-byte equivalent at
 * the domain boundary.
 */
@Repository
public class JpaAuditEventPublisher implements AuditEventPublisher {

    private static final TypeReference<Map<String, Object>> AUDIT_PAYLOAD =
            new TypeReference<>() {
            };

    private final AuditEventJpaRepository events;
    private final ObjectMapper objectMapper;

    public JpaAuditEventPublisher(
            AuditEventJpaRepository events,
            ObjectMapper objectMapper) {
        this.events = requireNonNull(events, "events");
        this.objectMapper = requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public void publish(AuditEvent event) {
        AuditEvent safeEvent = requireNonNull(event, "event must not be null");
        Optional<AuditEvent> existing = findForPublication(
                safeEvent.tenantId(),
                safeEvent.idempotencyKey());
        if (existing.isPresent()) {
            requireRetryEquivalent(existing.orElseThrow(), safeEvent);
            return;
        }
        try {
            events.saveAndFlush(AuditEventJpaEntity.from(
                    safeEvent,
                    payloadJson(safeEvent.payload())));
        } catch (DataAccessException | PersistenceException concurrentOrUnavailable) {
            try {
                AuditEvent winner = findByIdempotencyKey(
                                safeEvent.tenantId(),
                                safeEvent.idempotencyKey())
                        .orElseThrow(() -> new AuditRequiredException(
                                "durable audit publication failed",
                                concurrentOrUnavailable));
                requireRetryEquivalent(winner, safeEvent);
            } catch (DataAccessException | PersistenceException readFailure) {
                throw new AuditRequiredException("durable audit publication failed", readFailure);
            }
        }
    }

    public List<AuditEvent> events() {
        try {
            return events.findAllByOrderBySequenceIdAsc().stream()
                    .map(this::toDomain)
                    .toList();
        } catch (DataAccessException | PersistenceException exception) {
            throw new AuditRequiredException("durable audit read failed", exception);
        }
    }

    public String persistencePosture() {
        return "durable-relational-jpa-flyway";
    }

    private Optional<AuditEvent> findByIdempotencyKey(
            String tenantId,
            String idempotencyKey) {
        return events.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey)
                .map(this::toDomain);
    }

    private Optional<AuditEvent> findForPublication(
            String tenantId,
            String idempotencyKey) {
        try {
            return findByIdempotencyKey(tenantId, idempotencyKey);
        } catch (DataAccessException | PersistenceException exception) {
            throw new AuditRequiredException("durable audit publication failed", exception);
        }
    }

    private AuditEvent toDomain(AuditEventJpaEntity entity) {
        return new AuditEvent(
                entity.tenantId(),
                entity.contextId(),
                entity.actorRef(),
                entity.sourceRef(),
                AuditAction.valueOf(entity.action()),
                entity.occurredAt().toInstant(),
                entity.idempotencyKey(),
                AuditRedactionLevel.valueOf(entity.redactionLevel()),
                payload(entity.payloadJson()));
    }

    private void requireRetryEquivalent(AuditEvent existing, AuditEvent incoming) {
        if (!existing.equals(incoming)) {
            throw new AuditRequiredException(
                    "Conflicting durable audit event for idempotency key.");
        }
    }

    private String payloadJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new AuditRequiredException("durable audit publication failed", exception);
        }
    }

    private Map<String, Object> payload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payloadJson, AUDIT_PAYLOAD);
        } catch (JsonProcessingException exception) {
            throw new AuditRequiredException("durable audit read failed", exception);
        }
    }
}

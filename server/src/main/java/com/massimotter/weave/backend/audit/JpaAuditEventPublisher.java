package com.massimotter.weave.backend.audit;

import static java.util.Objects.requireNonNull;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.persistence.jpa.audit.AuditEventJpaEntity;
import com.massimotter.weave.backend.persistence.jpa.audit.AuditEventJpaRepository;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/** Durable, append-only and idempotent JPA audit sink. */
public class JpaAuditEventPublisher implements AuditEventPublisher {

    private static final TypeReference<Map<String, Object>> AUDIT_PAYLOAD = new TypeReference<>() {
    };

    private final AuditEventJpaRepository repository;
    private final ObjectMapper objectMapper;

    public JpaAuditEventPublisher(AuditEventJpaRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void publish(AuditEvent event) {
        AuditEvent safeEvent = requireNonNull(event, "event must not be null");
        try {
            var existing = repository.findByTenantIdAndIdempotencyKey(
                    safeEvent.tenantId(), safeEvent.idempotencyKey());
            if (existing.isPresent()) {
                requireRetryEquivalent(toDomain(existing.get()), safeEvent);
                return;
            }
            repository.saveAndFlush(toEntity(safeEvent));
        } catch (DataIntegrityViolationException conflict) {
            AuditEvent existing = repository.findByTenantIdAndIdempotencyKey(
                            safeEvent.tenantId(), safeEvent.idempotencyKey())
                    .map(this::toDomain)
                    .orElseThrow(() -> new AuditRequiredException(
                            "Durable audit idempotency conflict could not be reconciled.", conflict));
            requireRetryEquivalent(existing, safeEvent);
        } catch (DataAccessException failure) {
            throw new AuditRequiredException("durable audit publication failed", failure);
        }
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> events() {
        try {
            return repository.findAllByOrderBySequenceIdAsc().stream().map(this::toDomain).toList();
        } catch (DataAccessException failure) {
            throw new AuditRequiredException("durable audit read failed", failure);
        }
    }

    public String persistencePosture() {
        return "portable-jpa-hibernate-validated";
    }

    private AuditEventJpaEntity toEntity(AuditEvent event) {
        return new AuditEventJpaEntity(
                event.tenantId(),
                event.contextId(),
                event.actorRef(),
                event.sourceRef(),
                event.action().name(),
                event.occurredAt(),
                event.idempotencyKey(),
                event.redactionLevel().name(),
                payloadJson(event.payload()));
    }

    private AuditEvent toDomain(AuditEventJpaEntity entity) {
        return new AuditEvent(
                entity.tenantId(),
                entity.contextId(),
                entity.actorRef(),
                entity.sourceRef(),
                AuditAction.valueOf(entity.action()),
                entity.occurredAt(),
                entity.idempotencyKey(),
                AuditRedactionLevel.valueOf(entity.redactionLevel()),
                payload(entity.payloadJson()));
    }

    private void requireRetryEquivalent(AuditEvent existing, AuditEvent incoming) {
        if (!existing.equals(incoming)) {
            throw new AuditRequiredException("Conflicting durable audit event for idempotency key.");
        }
    }

    private String payloadJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException failure) {
            throw new AuditRequiredException("durable audit publication failed", failure);
        }
    }

    private Map<String, Object> payload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payloadJson, AUDIT_PAYLOAD);
        } catch (JacksonException failure) {
            throw new AuditRequiredException("durable audit read failed", failure);
        }
    }
}

package com.massimotter.weave.backend.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(
        name = "weave_audit_events",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_weave_audit_events_idempotency",
                columnNames = {"tenant_id", "idempotency_key"}))
class AuditEventJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sequence_id", nullable = false, updatable = false)
    private Long sequenceId;

    @Column(name = "tenant_id", nullable = false, length = 160, updatable = false)
    private String tenantId;

    @Column(name = "context_id", length = 255, updatable = false)
    private String contextId;

    @Column(name = "actor_ref", nullable = false, length = 255, updatable = false)
    private String actorRef;

    @Column(name = "source_ref", nullable = false, length = 255, updatable = false)
    private String sourceRef;

    @Column(name = "action", nullable = false, length = 120, updatable = false)
    private String action;

    @Column(name = "occurred_at_utc", nullable = false, updatable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "idempotency_key", nullable = false, length = 255, updatable = false)
    private String idempotencyKey;

    @Column(name = "redaction_level", nullable = false, length = 80, updatable = false)
    private String redactionLevel;

    @Column(name = "payload_json", nullable = false, updatable = false)
    private String payloadJson;

    protected AuditEventJpaEntity() {
    }

    static AuditEventJpaEntity from(AuditEvent event, String payloadJson) {
        AuditEventJpaEntity entity = new AuditEventJpaEntity();
        entity.tenantId = event.tenantId();
        entity.contextId = event.contextId();
        entity.actorRef = event.actorRef();
        entity.sourceRef = event.sourceRef();
        entity.action = event.action().name();
        entity.occurredAt = event.occurredAt().atOffset(ZoneOffset.UTC);
        entity.idempotencyKey = event.idempotencyKey();
        entity.redactionLevel = event.redactionLevel().name();
        entity.payloadJson = payloadJson;
        return entity;
    }

    Long sequenceId() {
        return sequenceId;
    }

    String tenantId() {
        return tenantId;
    }

    String contextId() {
        return contextId;
    }

    String actorRef() {
        return actorRef;
    }

    String sourceRef() {
        return sourceRef;
    }

    String action() {
        return action;
    }

    OffsetDateTime occurredAt() {
        return occurredAt;
    }

    String idempotencyKey() {
        return idempotencyKey;
    }

    String redactionLevel() {
        return redactionLevel;
    }

    String payloadJson() {
        return payloadJson;
    }
}

package com.massimotter.weave.backend.audit;

import java.time.Instant;
import java.util.Map;

/**
 * Shape future connector/assistant write attempts must emit before live writes are promoted.
 */
public record ConnectorWriteAuditEnvelope(
        String tenantId,
        String contextId,
        String actorRef,
        String sourceRef,
        String connectorRef,
        String targetRef,
        String commandType,
        Instant occurredAt,
        String idempotencyKey,
        Map<String, Object> payload) {

    public ConnectorWriteAuditEnvelope {
        tenantId = AuditEventContract.required("tenantId", tenantId);
        contextId = AuditEventContract.optionalContextId(contextId);
        actorRef = AuditEventContract.required("actorRef", actorRef);
        sourceRef = AuditEventContract.required("sourceRef", sourceRef);
        connectorRef = AuditEventContract.required("connectorRef", connectorRef);
        targetRef = AuditEventContract.required("targetRef", targetRef);
        commandType = AuditEventContract.required("commandType", commandType);
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt must not be null");
        }
        idempotencyKey = AuditEventContract.required("idempotencyKey", idempotencyKey);
        payload = Map.copyOf(payload == null ? Map.of() : payload);
    }

    public AuditEvent toAuditEvent() {
        return new AuditEvent(
                tenantId,
                contextId,
                actorRef,
                sourceRef,
                sourceRef.startsWith("assistant:") ? AuditAction.ASSISTANT_WRITE_ATTEMPTED : AuditAction.CONNECTOR_WRITE_ATTEMPTED,
                occurredAt,
                idempotencyKey,
                AuditRedactionLevel.SECRET_REDACTED,
                Map.of(
                        "connector_ref", connectorRef,
                        "target_ref", targetRef,
                        "command_type", commandType,
                        "write_enabled", false,
                        "payload", payload));
    }
}

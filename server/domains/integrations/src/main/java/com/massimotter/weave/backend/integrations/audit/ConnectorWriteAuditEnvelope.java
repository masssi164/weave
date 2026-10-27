package com.massimotter.weave.backend.integrations.audit;

import com.massimotter.weave.backend.audit.AuditEvent;
import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditRedactionLevel;

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
        tenantId = required("tenantId", tenantId);
        contextId = optionalContextId(contextId);
        actorRef = required("actorRef", actorRef);
        sourceRef = required("sourceRef", sourceRef);
        connectorRef = required("connectorRef", connectorRef);
        targetRef = required("targetRef", targetRef);
        commandType = required("commandType", commandType);
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt must not be null");
        }
        idempotencyKey = required("idempotencyKey", idempotencyKey);
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

    private static String required(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static String optionalContextId(String value) {
        if (value == null || value.isBlank()) return null;
        if (value.startsWith("provider:") || value.startsWith("provider_binding:")) {
            throw new IllegalArgumentException("contextId must be a Weave Context ID, not a provider binding");
        }
        return value;
    }
}

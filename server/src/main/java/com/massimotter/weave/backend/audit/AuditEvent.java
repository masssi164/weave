package com.massimotter.weave.backend.audit;

import java.time.Instant;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Append-only audit envelope for future connector and assistant writes.
 * Values intentionally use Weave tenant/context references, not raw provider ids.
 */
public record AuditEvent(
        String tenantId,
        String contextId,
        String actorRef,
        String sourceRef,
        AuditAction action,
        Instant occurredAt,
        String idempotencyKey,
        AuditRedactionLevel redactionLevel,
        Map<String, Object> payload) {

    public AuditEvent {
        tenantId = AuditEventContract.required("tenantId", tenantId);
        contextId = AuditEventContract.optionalContextId(contextId);
        actorRef = AuditEventContract.required("actorRef", actorRef);
        sourceRef = AuditEventContract.required("sourceRef", sourceRef);
        action = requireNonNull(action, "action must not be null");
        occurredAt = requireNonNull(occurredAt, "occurredAt must not be null");
        idempotencyKey = AuditEventContract.required("idempotencyKey", idempotencyKey);
        redactionLevel = requireNonNull(redactionLevel, "redactionLevel must not be null");
        payload = AuditPayloadRedactor.redact(requireNonNull(payload, "payload must not be null"));
    }
}

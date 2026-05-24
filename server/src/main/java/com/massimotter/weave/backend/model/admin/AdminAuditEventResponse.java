package com.massimotter.weave.backend.model.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

@Schema(description = "Support-safe admin audit event view.")
public record AdminAuditEventResponse(
        String tenantId,
        String actorRef,
        String sourceRef,
        String action,
        Instant occurredAt,
        String idempotencyKey,
        String redactionLevel,
        Map<String, Object> payload) {
    public AdminAuditEventResponse {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}

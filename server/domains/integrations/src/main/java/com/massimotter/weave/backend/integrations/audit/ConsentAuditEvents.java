package com.massimotter.weave.backend.integrations.audit;

import com.massimotter.weave.backend.audit.AuditEvent;
import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditRedactionLevel;

import java.time.Instant;
import java.util.Map;

public final class ConsentAuditEvents {

    private ConsentAuditEvents() {
    }

    public static AuditEvent granted(
            String tenantId,
            String contextId,
            String actorRef,
            String sourceRef,
            String consentRef,
            String connectorRef,
            Instant occurredAt,
            String idempotencyKey) {
        return consentEvent(
                tenantId,
                contextId,
                actorRef,
                sourceRef,
                AuditAction.CONSENT_GRANTED,
                consentRef,
                connectorRef,
                occurredAt,
                idempotencyKey);
    }

    public static AuditEvent revoked(
            String tenantId,
            String contextId,
            String actorRef,
            String sourceRef,
            String consentRef,
            String connectorRef,
            Instant occurredAt,
            String idempotencyKey) {
        return consentEvent(
                tenantId,
                contextId,
                actorRef,
                sourceRef,
                AuditAction.CONSENT_REVOKED,
                consentRef,
                connectorRef,
                occurredAt,
                idempotencyKey);
    }

    private static AuditEvent consentEvent(
            String tenantId,
            String contextId,
            String actorRef,
            String sourceRef,
            AuditAction action,
            String consentRef,
            String connectorRef,
            Instant occurredAt,
            String idempotencyKey) {
        return new AuditEvent(
                tenantId,
                contextId,
                actorRef,
                sourceRef,
                action,
                occurredAt,
                idempotencyKey,
                AuditRedactionLevel.SUPPORT_SAFE,
                Map.of(
                        "consent_ref", required("consentRef", consentRef),
                        "connector_ref", required("connectorRef", connectorRef)));
    }

    private static String required(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}

package com.massimotter.weave.backend.audit;

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
                        "consent_ref", AuditEventContract.required("consentRef", consentRef),
                        "connector_ref", AuditEventContract.required("connectorRef", connectorRef)));
    }
}

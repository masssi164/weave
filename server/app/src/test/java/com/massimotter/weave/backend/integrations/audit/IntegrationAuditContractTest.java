package com.massimotter.weave.backend.integrations.audit;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditRedactionLevel;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class IntegrationAuditContractTest {
    private static final Instant NOW = Instant.parse("2026-05-19T04:15:00Z");

    @Test
    void connectorWriteEnvelopeEmitsDisabledWriteAttemptWithoutEnablingProviderWrites() {
        var envelope = new ConnectorWriteAuditEnvelope(
                "tenant-a",
                "ctx-channel",
                "user:massimo",
                "connector:openproject",
                "connector-ref-openproject",
                "task:task-1",
                "boards.task.create",
                NOW,
                "connector-idem-1",
                Map.of(
                        "title", "Follow up",
                        "Authorization", "Bearer leaked-provider-token",
                        "rawProviderError", "OpenProject stack trace"));

        var event = envelope.toAuditEvent();

        assertThat(event.action()).isEqualTo(AuditAction.CONNECTOR_WRITE_ATTEMPTED);
        assertThat(event.redactionLevel()).isEqualTo(AuditRedactionLevel.SECRET_REDACTED);
        assertThat(event.idempotencyKey()).isEqualTo("connector-idem-1");
        assertThat(event.payload()).containsEntry("write_enabled", false);
        @SuppressWarnings("unchecked")
        Map<String, Object> writePayload = (Map<String, Object>) event.payload().get("payload");
        assertThat(writePayload)
                .containsEntry("Authorization", "[redacted]")
                .containsEntry("rawProviderError", "[redacted:provider-error]");
    }

    @Test
    void consentGrantAndRevocationUseDedicatedAuditActions() {
        var granted = ConsentAuditEvents.granted(
                "tenant-a",
                "ctx-team-product",
                "user:admin",
                "consent:center",
                "consent:openproject:read",
                "connector:openproject",
                NOW,
                "consent-grant-1");
        var revoked = ConsentAuditEvents.revoked(
                "tenant-a",
                "ctx-team-product",
                "user:admin",
                "consent:center",
                "consent:openproject:read",
                "connector:openproject",
                NOW.plusSeconds(60),
                "consent-revoke-1");

        assertThat(granted.action()).isEqualTo(AuditAction.CONSENT_GRANTED);
        assertThat(revoked.action()).isEqualTo(AuditAction.CONSENT_REVOKED);
        assertThat(List.of(granted.payload(), revoked.payload()))
                .allSatisfy(payload -> assertThat(payload).containsEntry("connector_ref", "connector:openproject"));
    }

}

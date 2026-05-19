package com.massimotter.weave.backend.audit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditConsentContractTest {

    private static final Instant NOW = Instant.parse("2026-05-19T04:15:00Z");

    @Test
    void auditEventPreservesTenantContextAndIdempotencyWhileRedactingSupportUnsafePayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operation", "openproject.work_package.create");
        payload.put("authorization", "Bearer provider-token");
        payload.put("api_token", "sk-openproject-provider-token");
        payload.put("raw_provider_error", "HTTP 500 body with private upstream details");
        payload.put("nested", Map.of("password", "secret", "safe_code", "rate_limited"));

        var event = new AuditEvent(
                "tenant-a",
                "ctx-channel-product-general",
                "user:massimo",
                "connector:openproject",
                AuditAction.CONNECTOR_WRITE_ATTEMPTED,
                NOW,
                "idem-123",
                AuditRedactionLevel.SECRET_REDACTED,
                payload);

        payload.put("operation", "mutated-after-event");

        assertThat(event.tenantId()).isEqualTo("tenant-a");
        assertThat(event.contextId()).isEqualTo("ctx-channel-product-general");
        assertThat(event.idempotencyKey()).isEqualTo("idem-123");
        assertThat(event.payload()).containsEntry("operation", "openproject.work_package.create");
        assertThat(event.payload()).containsEntry("authorization", "[redacted]");
        assertThat(event.payload()).containsEntry("api_token", "[redacted]");
        assertThat(event.payload()).containsEntry("raw_provider_error", "[redacted:provider-error]");
        @SuppressWarnings("unchecked")
        Map<String, Object> nestedPayload = (Map<String, Object>) event.payload().get("nested");
        assertThat(nestedPayload)
                .containsEntry("password", "[redacted]")
                .containsEntry("safe_code", "rate_limited");
    }

    @Test
    void auditEventsRejectProviderBindingContextIdsSoTenantScopedContextResolutionStaysExplicit() {
        assertThatThrownBy(() -> event("provider_binding:openproject:project-1", "idem-provider"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Context ID");
    }

    @Test
    void inMemoryPublisherIsAppendOnlyAndReturnsImmutableSnapshots() {
        var publisher = new InMemoryAuditEventPublisher();
        var first = event("ctx-workspace", "idem-1");
        var second = event("ctx-channel", "idem-2");

        publisher.publish(first);
        var snapshot = new ArrayList<>(publisher.events());
        snapshot.clear();
        publisher.publish(second);

        assertThat(publisher.events()).containsExactly(first, second);
        assertThatThrownBy(() -> publisher.events().add(first)).isInstanceOf(UnsupportedOperationException.class);
    }

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

    @Test
    void auditWriteGateFailsClosedWhenPublisherIsMissing() {
        var event = event("ctx-workspace", "idem-required");

        assertThatThrownBy(() -> AuditWriteGate.publishRequired(null, event))
                .isInstanceOf(AuditRequiredException.class)
                .hasMessageContaining("audit publisher is required");

        var publisher = new InMemoryAuditEventPublisher();
        AuditWriteGate.publishRequired(publisher, event);

        assertThat(publisher.events()).containsExactly(event);
    }

    private AuditEvent event(String contextId, String idempotencyKey) {
        return new AuditEvent(
                "tenant-a",
                contextId,
                "user:massimo",
                "connector:openproject",
                AuditAction.CONNECTOR_WRITE_ATTEMPTED,
                NOW,
                idempotencyKey,
                AuditRedactionLevel.SUPPORT_SAFE,
                Map.of("safe", true));
    }
}

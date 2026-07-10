package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.audit.AuditRequiredException;
import com.massimotter.weave.backend.audit.InMemoryAuditEventPublisher;
import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.config.WeaverRuntimeProperties;
import com.massimotter.weave.backend.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationDecision;
import com.massimotter.weave.backend.model.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.model.chat.DecisionLedgerCreateRequest;
import com.massimotter.weave.backend.model.chat.DecisionLedgerReferenceRequest;
import com.massimotter.weave.backend.model.chat.MeetingCapsuleCreateRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatFacadeServiceTest {

    @Test
    void createDecisionFailsClosedBeforeMutationWhenAuditPublisherIsMissing() {
        ChatFacadeService service = serviceWithMissingAuditPublisher();

        assertThat(service.decisions(jwt(), "channel-general").records()).isEmpty();
        assertThat(service.decisions(jwt(), "channel-general").evidencePosture().supportSafe()).isTrue();

        assertThatThrownBy(() -> service.createDecision(jwt(), "channel-general", decisionRequest()))
                .isInstanceOf(AuditRequiredException.class);

        assertThat(service.decisions(jwt(), "channel-general").records()).isEmpty();
    }

    @Test
    void decisionsExposeSupportSafeProvenanceAuditAndExportPosture() {
        ChatFacadeService service = service(new InMemoryAuditEventPublisher());

        var response = service.decisions(jwt(), "channel-general");

        assertThat(response.backgroundRoomReadingEnabled()).isFalse();
        assertThat(response.evidencePosture().provenance())
                .contains("Weave-owned provenance")
                .doesNotContain("token", "Bearer", "http://", "https://");
        assertThat(response.evidencePosture().auditRefs())
                .allMatch(ref -> ref.startsWith("audit://chat/decision"));
        assertThat(response.evidencePosture().exportPosture())
                .contains("Export decision records, source refs, and audit refs")
                .contains("raw provider secrets stay backend-only");
        assertThat(response.evidencePosture().supportSafe()).isTrue();
    }

    @Test
    void createMeetingCapsuleFailsClosedBeforeMutationWhenAuditPublisherIsMissing() {
        ChatFacadeService service = serviceWithMissingAuditPublisher();

        assertThat(service.meetingCapsules(jwt(), "channel-general").capsules()).isEmpty();

        assertThatThrownBy(() -> service.createMeetingCapsule(jwt(), "channel-general",
                new MeetingCapsuleCreateRequest("Sprint planning", List.of("Review scope"), List.of("decision:sprint"))))
                .isInstanceOf(AuditRequiredException.class);

        assertThat(service.meetingCapsules(jwt(), "channel-general").capsules()).isEmpty();
    }

    private ChatFacadeService serviceWithMissingAuditPublisher() {
        WorkspaceCapabilityProperties properties = workspaceCapabilityProperties();
        return new ChatFacadeService(
                properties,
                workspaceCapabilityService(properties, weaverRuntimeProperties(true)),
                request -> ContextAuthorizationDecision.allow("test allow"),
                new ContextAuthorizationProperties(null, null, null, null, null, null, null, null),
                null);
    }

    private ChatFacadeService service(InMemoryAuditEventPublisher auditPublisher) {
        WorkspaceCapabilityProperties properties = workspaceCapabilityProperties();
        return new ChatFacadeService(
                properties,
                workspaceCapabilityService(properties, weaverRuntimeProperties(true)),
                request -> ContextAuthorizationDecision.allow("test allow"),
                new ContextAuthorizationProperties(null, null, null, null, null, null, null, null),
                auditPublisher);
    }

    private WorkspaceCapabilityProperties workspaceCapabilityProperties() {
        return new WorkspaceCapabilityProperties(
                null,
                new WorkspaceCapabilityProperties.Capability(true, null, WorkspaceCapabilityReadiness.READY),
                null,
                null,
                null,
                null);
    }

    private WeaverRuntimeProperties weaverRuntimeProperties(boolean enabled) {
        return new WeaverRuntimeProperties(
                enabled,
                "weaver-governed-baseline",
                "ghcr.io/masssi164/weaver-openclaw:policy-generated",
                "/var/lib/weave/weaver/{userId}",
                ".weaver/agents",
                "weave-runtime-net",
                List.of("weave-weaver-runtime"),
                List.of("weaver.files_read", "weaver.exec_disabled"),
                List.of("weave-chat"),
                List.of("message.send"),
                false,
                false,
                true,
                true);
    }

    private WorkspaceCapabilityService workspaceCapabilityService(
            WorkspaceCapabilityProperties properties,
            WeaverRuntimeProperties weaverRuntimeProperties) {
        OAuth2ResourceServerProperties resourceServerProperties = new OAuth2ResourceServerProperties();
        resourceServerProperties.getJwt().setIssuerUri("https://auth.example.invalid/realms/acme");
        return new WorkspaceCapabilityService(
                resourceServerProperties,
                new WeaveSecurityProperties("weave-app", "weave-app"),
                properties,
                weaverRuntimeProperties);
    }

    private DecisionLedgerCreateRequest decisionRequest() {
        return new DecisionLedgerCreateRequest(
                "Accept support-safe channel records",
                "accepted",
                List.of(),
                List.of(),
                List.of(),
                List.of(new DecisionLedgerReferenceRequest(
                        "chat-message",
                        "message:msg-seed-welcome",
                        "Seed message",
                        "Provider details stay behind the backend facade.")));
    }

    private Jwt jwt() {
        return jwt(List.of("member"), List.of());
    }

    private Jwt jwt(List<String> roles, List<String> groups) {
        Instant now = Instant.parse("2026-05-25T12:00:00Z");
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-123")
                .issuer("https://auth.example.invalid/realms/acme")
                .claim("weave_tenant_id", "tenant-default")
                .claim("realm_access", Map.of("roles", roles))
                .claim("groups", groups)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
    }
}

package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.audit.AuditRequiredException;
import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationDecision;
import com.massimotter.weave.backend.model.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.model.chat.DecisionLedgerCreateRequest;
import com.massimotter.weave.backend.model.chat.DecisionLedgerReferenceRequest;
import com.massimotter.weave.backend.model.chat.MeetingCapsuleCreateRequest;
import java.time.Instant;
import java.util.List;
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

        assertThatThrownBy(() -> service.createDecision(jwt(), "channel-general", decisionRequest()))
                .isInstanceOf(AuditRequiredException.class);

        assertThat(service.decisions(jwt(), "channel-general").records()).isEmpty();
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
        WorkspaceCapabilityProperties properties = new WorkspaceCapabilityProperties(
                null,
                new WorkspaceCapabilityProperties.Capability(true, null, WorkspaceCapabilityReadiness.READY),
                null,
                null,
                null,
                null);
        return new ChatFacadeService(
                properties,
                workspaceCapabilityService(properties),
                request -> ContextAuthorizationDecision.allow("test allow"),
                new ContextAuthorizationProperties(null, null, null, null, null, null, null, null),
                null);
    }

    private WorkspaceCapabilityService workspaceCapabilityService(WorkspaceCapabilityProperties properties) {
        OAuth2ResourceServerProperties resourceServerProperties = new OAuth2ResourceServerProperties();
        resourceServerProperties.getJwt().setIssuerUri("https://auth.example.invalid/realms/acme");
        return new WorkspaceCapabilityService(
                resourceServerProperties,
                new WeaveSecurityProperties("weave-app", "weave-app"),
                properties);
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
        Instant now = Instant.parse("2026-05-25T12:00:00Z");
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-123")
                .issuer("https://auth.example.invalid/realms/acme")
                .claim("weave_tenant_id", "tenant-default")
                .claim("realm_access", java.util.Map.of("roles", java.util.List.of("member")))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
    }
}

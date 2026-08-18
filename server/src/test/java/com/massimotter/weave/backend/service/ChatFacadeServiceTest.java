package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.support.HumanJwtTestSupport;

import com.massimotter.weave.backend.audit.AuditRequiredException;
import com.massimotter.weave.backend.audit.InMemoryAuditEventPublisher;
import com.massimotter.weave.backend.chat.ChatDomainFacadeService;
import com.massimotter.weave.backend.chat.domain.ChatConversation;
import com.massimotter.weave.backend.chat.domain.ChatEncryptionState;
import com.massimotter.weave.backend.chat.domain.ChatHistoryPolicy;
import com.massimotter.weave.backend.chat.domain.ChatMemberState;
import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.config.AgentRuntimeEntitlementProperties;
import com.massimotter.weave.backend.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationDecision;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.model.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.model.chat.DecisionLedgerCreateRequest;
import com.massimotter.weave.backend.model.chat.DecisionLedgerReferenceRequest;
import com.massimotter.weave.backend.model.chat.MeetingCapsuleCreateRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatFacadeServiceTest {

    @Test
    void decisionsAcceptNativeOrganizationTokenWithoutLegacyTenantAliases() {
        java.util.concurrent.atomic.AtomicReference<com.massimotter.weave.backend.context.authz.ContextAuthorizationRequest> captured =
                new java.util.concurrent.atomic.AtomicReference<>();
        WorkspaceCapabilityProperties properties = workspaceCapabilityProperties();
        ChatFacadeService service = new ChatFacadeService(
                properties,
                workspaceCapabilityService(properties, runtimeEntitlementProperties(true)),
                request -> {
                    captured.set(request);
                    return ContextAuthorizationDecision.allow("native organization identity matched");
                },
                new ContextAuthorizationProperties(null, null, null, null, null, null, null, null),
                mock(ChatDomainFacadeService.class),
                new InMemoryAuditEventPublisher());

        service.decisions(nativeOrganizationJwt(), "channel-general");

        assertThat(captured.get().tenantId()).isEqualTo("tenant-default");
        assertThat(captured.get().principalRef()).isEqualTo("user:user-123");
    }

    @Test
    void decisionsTranslateMalformedOrganizationIdentityToStableUnauthorizedError() {
        ChatFacadeService service = service(new InMemoryAuditEventPublisher());

        assertThatThrownBy(() -> service.decisions(jwtWithoutIssuer(), "channel-general"))
                .isInstanceOfSatisfying(ApiErrorException.class, error -> {
                    assertThat(error.status()).isEqualTo(org.springframework.http.HttpStatus.UNAUTHORIZED);
                    assertThat(error.code()).isEqualTo("unauthorized");
                    assertThat(error.details())
                            .containsEntry("module", "chat")
                            .containsEntry("reason", "organization identity is missing or invalid");
                });
    }

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

    @Test
    void decisionsResolveEveryDynamicConversationThroughTheCanonicalChatFacade() {
        Jwt principal = jwt();
        ChatDomainFacadeService canonicalChat = mock(ChatDomainFacadeService.class);
        when(canonicalChat.conversation("room-dynamic", principal)).thenReturn(new ChatConversation(
                "room-dynamic",
                "Dynamic encrypted conversation",
                "channel",
                ChatMemberState.READY,
                null,
                Instant.parse("2026-05-25T12:00:00Z"),
                ChatEncryptionState.matrixMegolm(),
                new ChatHistoryPolicy("conversation_members", "organization_default_retention", false, true, List.of()),
                List.of(),
                List.of()));
        ChatFacadeService service = service(new InMemoryAuditEventPublisher(), canonicalChat);

        var created = service.createDecision(principal, "room-dynamic", decisionRequest());
        var snapshot = service.decisions(principal, "room-dynamic");

        assertThat(created.conversationId()).isEqualTo("room-dynamic");
        assertThat(created.contextId()).isEqualTo("workspace-default");
        assertThat(snapshot.records()).extracting(record -> record.id()).containsExactly(created.id());
        verify(canonicalChat, times(2)).conversation("room-dynamic", principal);
    }

    private ChatFacadeService serviceWithMissingAuditPublisher() {
        WorkspaceCapabilityProperties properties = workspaceCapabilityProperties();
        return new ChatFacadeService(
                properties,
                workspaceCapabilityService(properties, runtimeEntitlementProperties(true)),
                request -> ContextAuthorizationDecision.allow("test allow"),
                new ContextAuthorizationProperties(null, null, null, null, null, null, null, null),
                mock(ChatDomainFacadeService.class),
                null);
    }

    private ChatFacadeService service(InMemoryAuditEventPublisher auditPublisher) {
        return service(auditPublisher, mock(ChatDomainFacadeService.class));
    }

    private ChatFacadeService service(
            InMemoryAuditEventPublisher auditPublisher,
            ChatDomainFacadeService chatDomainFacadeService) {
        WorkspaceCapabilityProperties properties = workspaceCapabilityProperties();
        return new ChatFacadeService(
                properties,
                workspaceCapabilityService(properties, runtimeEntitlementProperties(true)),
                request -> ContextAuthorizationDecision.allow("test allow"),
                new ContextAuthorizationProperties(null, null, null, null, null, null, null, null),
                chatDomainFacadeService,
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

    private AgentRuntimeEntitlementProperties runtimeEntitlementProperties(boolean enabled) {
        return new AgentRuntimeEntitlementProperties(
                enabled,
                null,
                List.of("calendar.read"));
    }

    private WorkspaceCapabilityService workspaceCapabilityService(
            WorkspaceCapabilityProperties properties,
            AgentRuntimeEntitlementProperties runtimeEntitlementProperties) {
        OAuth2ResourceServerProperties resourceServerProperties = new OAuth2ResourceServerProperties();
        resourceServerProperties.getJwt().setIssuerUri("https://auth.example.invalid/realms/acme");
        return new WorkspaceCapabilityService(
                resourceServerProperties,
                new WeaveSecurityProperties("weave-app", "weave-app"),
                properties,
                runtimeEntitlementProperties);
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

    private Jwt nativeOrganizationJwt() {
        Instant now = Instant.parse("2026-05-25T12:00:00Z");
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-123")
                .issuer("https://auth.example.invalid/realms/acme")
                .claim("organization", HumanJwtTestSupport.organizationWithRole("member"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
    }

    private Jwt jwtWithoutIssuer() {
        Instant now = Instant.parse("2026-05-25T12:00:00Z");
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-123")
                .claim("organization", HumanJwtTestSupport.organizationWithRole("member"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
    }

    private Jwt jwt(List<String> roles, List<String> groups) {
        Instant now = Instant.parse("2026-05-25T12:00:00Z");
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-123")
                .issuer("https://auth.example.invalid/realms/acme")
                .claim("weave_tenant_id", "tenant-default")
                .claim(
                        "organization",
                        HumanJwtTestSupport
                                .organizationWithRolesAndGroups(roles, groups))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
    }
}

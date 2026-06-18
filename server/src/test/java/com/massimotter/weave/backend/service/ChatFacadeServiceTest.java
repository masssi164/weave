package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditRequiredException;
import com.massimotter.weave.backend.audit.InMemoryAuditEventPublisher;
import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.config.WeaverRuntimeProperties;
import com.massimotter.weave.backend.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationDecision;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.model.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.model.chat.ChatSendMessageRequest;
import com.massimotter.weave.backend.model.chat.DecisionLedgerCreateRequest;
import com.massimotter.weave.backend.model.chat.DecisionLedgerReferenceRequest;
import com.massimotter.weave.backend.model.chat.MeetingCapsuleCreateRequest;
import com.massimotter.weave.backend.provider.InMemoryProviderSelectionRepository;
import com.massimotter.weave.backend.provider.ProviderSelection;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatFacadeServiceTest {

    @Test
    void paWeaverConversationCompletesWeaveChatToLmStudioTurnWhenRuntimePolicyAllowsMember() {
        InMemoryAuditEventPublisher auditPublisher = new InMemoryAuditEventPublisher();
        ChatFacadeService service = service(auditPublisher);
        Jwt jwt = jwt(List.of("member"), List.of("weave-weaver-runtime"));

        var paConversation = service.conversations(jwt).conversations().stream()
                .filter(conversation -> conversation.id().equals("pa-weaver"))
                .findFirst()
                .orElseThrow();
        assertThat(paConversation.title()).isEqualTo("PA Weaver");
        assertThat(paConversation.kind()).isEqualTo("ai");
        assertThat(paConversation.availableActions()).contains("message-pa-weaver");

        var sent = service.sendMessage(jwt, "pa-weaver", new ChatSendMessageRequest(
                "Ping PA Weaver through Weave Chat",
                List.of()));
        var timeline = service.messages(jwt, "pa-weaver");

        assertThat(sent.deliveryEvidence())
                .containsEntry("channelId", "channels.weave-chat")
                .containsEntry("providerRef", "provider:chat:selected-by-admin")
                .containsEntry("weaverReceived", true)
                .containsEntry("lmStudioResponseReceived", true);
        assertThat(timeline.messages())
                .filteredOn(message -> message.senderRef().equals("weaver:pa"))
                .anySatisfy(message -> {
                    assertThat(message.text()).contains("LM Studio");
                    assertThat(message.deliveryEvidence())
                            .containsEntry("channelId", "channels.weave-chat")
                            .containsEntry("modelRef", "lmstudio/qwen/qwen3.5-9b")
                            .containsEntry("rawProviderDiagnosticsExposed", false);
                });
        assertThat(auditPublisher.events())
                .extracting(event -> event.action())
                .contains(AuditAction.CHAT_MESSAGE_SENT, AuditAction.WEAVER_PA_CHAT_TURN_COMPLETED);
        assertThat(auditPublisher.events().toString())
                .doesNotContain("Authorization", "Bearer ", "access_token", "refresh", "localhost");
    }

    @Test
    void paWeaverSendResponseReflectsActualCompletionEvidence() {
        ChatFacadeService service = service(
                new InMemoryAuditEventPublisher(),
                request -> new WeaverPaChatTurnResult(
                        false,
                        false,
                        "PA Weaver accepted the turn but the provider did not complete it.",
                        "lmstudio/qwen/custom-runtime-model",
                        "provider:model:custom-lmstudio",
                        "audit://weaver/pa-chat/partial-roundtrip",
                        Map.of(
                                "channelId", request.channelId(),
                                "modelRef", "lmstudio/qwen/custom-runtime-model",
                                "rawProviderDiagnosticsExposed", false,
                                "supportSafe", true)));

        var sent = service.sendMessage(
                jwt(List.of("member"), List.of("weave-weaver-runtime")),
                "pa-weaver",
                new ChatSendMessageRequest("show actual completion state", List.of()));
        var assistantMessage = service.messages(jwt(List.of("member"), List.of("weave-weaver-runtime")), "pa-weaver")
                .messages().stream()
                .filter(message -> message.senderRef().equals("weaver:pa"))
                .reduce((first, second) -> second)
                .orElseThrow();

        assertThat(assistantMessage.deliveryEvidence())
                .containsEntry("providerRef", "provider:chat:selected-by-admin")
                .containsEntry("weaverReceived", false)
                .containsEntry("lmStudioResponseReceived", false)
                .containsEntry("modelRef", "lmstudio/qwen/custom-runtime-model");
        assertThat(sent.deliveryEvidence())
                .containsEntry("providerRef", "provider:chat:selected-by-admin")
                .containsEntry("weaverReceived", false)
                .containsEntry("lmStudioResponseReceived", false)
                .containsEntry("modelRef", "lmstudio/qwen/custom-runtime-model")
                .containsEntry("assistantMessageId", assistantMessage.id());
    }

    @Test
    void paWeaverBridgeRequestUsesAdminSelectedModelProvider() {
        InMemoryProviderSelectionRepository selections = new InMemoryProviderSelectionRepository();
        selections.save(new ProviderSelection(
                "model",
                "custom-lmstudio",
                "recommended_self_hosted_default",
                "secretref://weave/provider/custom-lmstudio",
                "actor:admin",
                Instant.parse("2026-05-25T10:00:00Z"),
                true,
                true,
                false,
                List.of()));
        AtomicReference<WeaverPaChatTurnRequest> capturedRequest = new AtomicReference<>();
        WorkspaceCapabilityProperties properties = workspaceCapabilityProperties();
        ChatFacadeService service = new ChatFacadeService(
                properties,
                workspaceCapabilityService(properties, weaverRuntimeProperties(true)),
                request -> ContextAuthorizationDecision.allow("test allow"),
                new ContextAuthorizationProperties(null, null, null, null, null, null, null, null),
                new InMemoryAuditEventPublisher(),
                selections,
                request -> {
                    capturedRequest.set(request);
                    return new WeaverPaChatTurnResult(
                            true,
                            true,
                            "PA Weaver returned through the admin-selected model provider.",
                            request.modelRef(),
                            request.providerRef(),
                            "audit://weaver/pa-chat/test-roundtrip",
                            Map.of(
                                    "channelId", request.channelId(),
                                    "modelRef", request.modelRef(),
                                    "rawProviderDiagnosticsExposed", false,
                                    "supportSafe", true));
                });

        service.sendMessage(
                jwt(List.of("member"), List.of("weave-weaver-runtime")),
                "pa-weaver",
                new ChatSendMessageRequest("use selected provider", List.of()));

        assertThat(capturedRequest.get().providerRef()).isEqualTo("provider:model:custom-lmstudio");
        assertThat(capturedRequest.get().supportSafeContext())
                .containsEntry("chatProviderRef", "provider:chat:selected-by-admin")
                .containsEntry("rawProviderContentIncluded", false);
    }

    @Test
    void paWeaverChatFailsClosedWhenRuntimeBridgeIsNotConfigured() {
        ChatFacadeService service = serviceWithoutConfiguredWeaverBridge();

        assertThatThrownBy(() -> service.sendMessage(
                jwt(List.of("member"), List.of("weave-weaver-runtime")),
                "pa-weaver",
                new ChatSendMessageRequest("do not fake provider success", List.of())))
                .isInstanceOfSatisfying(ApiErrorException.class, exception -> {
                    assertThat(exception.status().value()).isEqualTo(503);
                    assertThat(exception.details())
                            .containsEntry("channelId", "channels.weave-chat")
                            .containsEntry("diagnosticsRedacted", true);
                });
    }

    @Test
    void paWeaverChatFailsClosedForMemberWithoutWeaverRuntimePolicy() {
        ChatFacadeService service = service(new InMemoryAuditEventPublisher());

        assertThatThrownBy(() -> service.sendMessage(
                jwt(List.of("member"), List.of()),
                "pa-weaver",
                new ChatSendMessageRequest("try provider bypass", List.of())))
                .isInstanceOfSatisfying(ApiErrorException.class, exception -> {
                    assertThat(exception.status().value()).isEqualTo(403);
                    assertThat(exception.details()).containsEntry("requiredCapability", "weaver.enabled");
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

    private ChatFacadeService serviceWithoutConfiguredWeaverBridge() {
        WorkspaceCapabilityProperties properties = workspaceCapabilityProperties();
        return new ChatFacadeService(
                properties,
                workspaceCapabilityService(properties, weaverRuntimeProperties(true)),
                request -> ContextAuthorizationDecision.allow("test allow"),
                new ContextAuthorizationProperties(null, null, null, null, null, null, null, null),
                new InMemoryAuditEventPublisher());
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
        return service(
                auditPublisher,
                request -> new WeaverPaChatTurnResult(
                        true,
                        true,
                        "PA Weaver received the chat turn and returned a test LM Studio answer through channels.weave-chat.",
                        request.modelRef(),
                        "provider:model:lmstudio",
                        "audit://weaver/pa-chat/test-roundtrip",
                        Map.of(
                                "channelId", request.channelId(),
                                "modelRef", request.modelRef(),
                                "rawProviderDiagnosticsExposed", false,
                                "supportSafe", true)));
    }

    private ChatFacadeService service(
            InMemoryAuditEventPublisher auditPublisher,
            WeaverPaChatClient weaverPaChatClient) {
        WorkspaceCapabilityProperties properties = workspaceCapabilityProperties();
        return new ChatFacadeService(
                properties,
                workspaceCapabilityService(properties, weaverRuntimeProperties(true)),
                request -> ContextAuthorizationDecision.allow("test allow"),
                new ContextAuthorizationProperties(null, null, null, null, null, null, null, null),
                auditPublisher,
                weaverPaChatClient);
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
                null,
                "weaver-runtime-profile-test-signing-key-32-bytes-minimum",
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

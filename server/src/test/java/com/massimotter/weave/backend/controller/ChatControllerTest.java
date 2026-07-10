package com.massimotter.weave.backend.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.chat.ChatDomainFacadeService;
import com.massimotter.weave.backend.chat.domain.ChatHistoryPolicy;
import com.massimotter.weave.backend.chat.domain.ChatMemberState;
import com.massimotter.weave.backend.chat.domain.ChatMigrationPreflightReport;
import com.massimotter.weave.backend.chat.domain.ChatReadiness;
import com.massimotter.weave.backend.config.ApiAccessDeniedHandler;
import com.massimotter.weave.backend.config.ApiAuthenticationEntryPoint;
import com.massimotter.weave.backend.config.ApiErrorResponseWriter;
import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.config.SecurityConfig;
import com.massimotter.weave.backend.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.config.WeaverRuntimeProperties;
import com.massimotter.weave.backend.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationDecision;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationPort;
import com.massimotter.weave.backend.context.authz.ContextPermission;
import com.massimotter.weave.backend.exception.ApiExceptionHandler;
import com.massimotter.weave.backend.service.ChatFacadeService;
import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = ChatController.class,
        excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@Import({
        SecurityConfig.class,
        ApiAuthenticationEntryPoint.class,
        ApiAccessDeniedHandler.class,
        ApiErrorResponseWriter.class,
        ApiExceptionHandler.class,
        WorkspaceCapabilityService.class,
        ChatFacadeService.class
})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.example.invalid/realms/weave",
        "weave.workspace.chat.readiness=ready",
        "weave.workspace.weaver.enabled=true",
        "weave.workspace.weaver.readiness=ready",
        "weave.weaver.runtime.enabled=true",
        "weave.context.authorization.principal-claim=preferred_username"
})
@EnableConfigurationProperties({
        ContextAuthorizationProperties.class,
        WorkspaceCapabilityProperties.class,
        WeaveSecurityProperties.class,
        WeaverRuntimeProperties.class,
        OAuth2ResourceServerProperties.class
})
class ChatControllerTest {

    // WEAVE_CHAT_DOMAIN_FACADE
    // V01_CHANNEL_WORKSPACE
    // V01_MEETING_CAPSULE
    // V01_DECISION_LEDGER
    // source-linked

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private ContextAuthorizationPort contextAuthorizationPort;

    @MockBean
    private AuditEventPublisher auditEventPublisher;

    @MockBean
    private ChatDomainFacadeService chatDomainFacadeService;

    @Test
    void memberReadinessExposesOnlyStableProductState() throws Exception {
        when(chatDomainFacadeService.memberReadiness(any())).thenReturn(memberReadiness());

        mockMvc.perform(get("/api/chat/readiness").with(memberJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractVersion").value("chat-domain-facade-v1"))
                .andExpect(jsonPath("$.domain").value("chat"))
                .andExpect(jsonPath("$.memberState").value("not_configured"))
                .andExpect(jsonPath("$.memberClientMayConfigureProvider").value(false))
                .andExpect(jsonPath("$.providerMapping").doesNotExist())
                .andExpect(content().string(not(containsString("secretref://"))))
                .andExpect(content().string(not(containsString("access_token"))))
                .andExpect(content().string(not(containsString("xoxb-"))));
    }

    @Test
    void adminChatReadinessRejectsNormalMembers() throws Exception {
        mockMvc.perform(get("/api/admin/chat/readiness").with(memberJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminMigrationPreflightReturnsAuditedDryRunReport() throws Exception {
        when(chatDomainFacadeService.preflight(any(), any())).thenReturn(new ChatMigrationPreflightReport(
                "chat-preflight-1",
                "dry-run",
                "slack",
                "microsoft-teams",
                ChatMemberState.DEGRADED,
                false,
                true,
                "audit-1",
                Map.of("conversations", 3),
                List.of("membership_identity_mapping"),
                List.of("provider-specific reactions need Weave annotations"),
                List.of("destructive_apply_not_available_in_chat_domain_facade_v1"),
                List.of("Dry-run only: no provider data is mutated."),
                Instant.parse("2026-05-25T08:00:00Z")));

        mockMvc.perform(post("/api/admin/chat/migration-preflights")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetProviderKey\":\"microsoft-teams\",\"dryRun\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("dry-run"))
                .andExpect(jsonPath("$.destructiveApplyAvailable").value(false))
                .andExpect(jsonPath("$.auditEventPublished").value(true))
                .andExpect(content().string(not(containsString("access_token"))))
                .andExpect(content().string(not(containsString("secretref://"))));
    }

    @Test
    void chatConversationsRequireAuthenticatedWorkspaceScope() throws Exception {
        mockMvc.perform(get("/api/chat/conversations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));
    }

    @Test
    void chatLegacyRestDataPlaneRoutesAreRemovedInFavorOfMatrixFacade() throws Exception {
        mockMvc.perform(get("/api/chat/conversations")
                        .with(workspaceJwt("member")))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/chat/conversations/channel-general/messages")
                        .with(workspaceJwt("member")))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/chat/conversations/channel-general/messages")
                        .with(workspaceJwt("member"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"REST chat data-plane is obsolete.\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(request(HttpMethod.POST, "/api/chat/conversations/pa-weaver/messages")
                        .with(workspaceJwt("member", List.of("weave-weaver-runtime", "weave-weaver-pilot")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"PA Weaver chat must enter through Matrix.\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void decisionLedgerCreateAndReadUseSourceLinkedLifecycleRecords() throws Exception {
        allowChatPermission(ContextPermission.EDIT);
        allowChatPermission(ContextPermission.VIEW);
        String payload = """
                {
                  "title":"Use governed channel workspace tabs for Sprint 4",
                  "status":"accepted",
                  "risks":["Keep background room reading disabled"],
                  "openQuestions":["When do we enable richer meeting media?"],
                  "followUpRefs":["task:release-evidence"],
                  "references":[{
                    "type":"chat-message",
                    "ref":"message:msg-seed-welcome",
                    "label":"Seed welcome message",
                    "excerpt":"Provider details stay behind the backend facade"
                  }]
                }
                """;

        mockMvc.perform(post("/api/v1/chat/conversations/channel-general/decisions")
                        .with(workspaceJwt("member"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value("channel-general"))
                .andExpect(jsonPath("$.contextId").value("workspace-default"))
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.authorRef").value("user:test"))
                .andExpect(jsonPath("$.references[0].ref").value("message:msg-seed-welcome"))
                .andExpect(jsonPath("$.risks[0]").value("Keep background room reading disabled"))
                .andExpect(jsonPath("$.supportSafe").value(true))
                .andExpect(jsonPath("$..roomId").doesNotExist())
                .andExpect(jsonPath("$..providerUrl").doesNotExist());

        mockMvc.perform(get("/api/v1/chat/conversations/channel-general/decisions")
                        .with(workspaceJwt("member")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backgroundRoomReadingEnabled").value(false))
                .andExpect(content().string(containsString("Use governed channel workspace tabs for Sprint 4")))
                .andExpect(content().string(containsString("Seed welcome message")));

        verify(auditEventPublisher).publish(argThat(event ->
                event != null
                        && event.action() == AuditAction.DECISION_LEDGER_RECORD_CREATED
                        && Boolean.TRUE.equals(event.payload().get("supportSafe"))
                        && Integer.valueOf(1).equals(event.payload().get("referenceCount"))));
    }

    @Test
    void meetingCapsuleCreateAndReadStayFailClosedWithoutProviderLeakage() throws Exception {
        allowChatPermission(ContextPermission.EDIT);
        allowChatPermission(ContextPermission.VIEW);
        String payload = """
                {
                  "title":"Sprint 4 evidence review",
                  "agendaItems":["Review decisions", "Confirm follow-up tasks"],
                  "followUpRefs":["decision:sprint-4-close"]
                }
                """;

        mockMvc.perform(post("/api/v1/chat/conversations/channel-general/meeting-capsules")
                        .with(workspaceJwt("member"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value("channel-general"))
                .andExpect(jsonPath("$.state").value("scheduled"))
                .andExpect(jsonPath("$.agendaItems[0]").value("Review decisions"))
                .andExpect(jsonPath("$.participants[0]").value("user:test:organizer"))
                .andExpect(jsonPath("$.disabledControls[0]").value("join"))
                .andExpect(jsonPath("$.disabledReason").value("meeting-backend-capability-unavailable"))
                .andExpect(jsonPath("$.liveKitProviderDetailsExposed").value(false))
                .andExpect(jsonPath("$.matrixE2eeClaimedForMedia").value(false))
                .andExpect(jsonPath("$.recordingEnabled").value(false))
                .andExpect(content().string(not(containsString("livekit://"))))
                .andExpect(content().string(not(containsString("access_token"))));

        mockMvc.perform(get("/api/v1/chat/conversations/channel-general/meeting-capsules")
                        .with(workspaceJwt("member")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failClosed").value(true))
                .andExpect(jsonPath("$.capsules[0].title").value("Sprint 4 evidence review"));

        verify(auditEventPublisher).publish(argThat(event ->
                event != null
                        && event.action() == AuditAction.MEETING_CAPSULE_CREATED
                        && Boolean.TRUE.equals(event.payload().get("failClosed"))
                        && Boolean.TRUE.equals(event.payload().get("supportSafe"))));
    }

    @Test
    void weaverScoutSummarizesAllowedContextAndBlocksWritesWithReceipts() throws Exception {
        allowChatPermission(ContextPermission.EDIT);
        allowChatPermission(ContextPermission.VIEW);
        mockMvc.perform(post("/api/v1/chat/conversations/channel-general/decisions")
                        .with(workspaceJwt("member"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"Keep Weaver read-only in Sprint 4",
                                  "references":[{
                                    "type":"chat-message",
                                    "ref":"message:msg-seed-welcome",
                                    "label":"Seed welcome message"
                                  }]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/chat/conversations/channel-general/weaver/scout/summaries")
                        .with(workspaceJwt("member"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question":"What is open in this channel?",
                                  "requestedAction":"Create a task from the summary"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value("channel-general"))
                .andExpect(jsonPath("$.readOnly").value(true))
                .andExpect(jsonPath("$.proposalOnly").value(true))
                .andExpect(jsonPath("$.backgroundRoomReadingEnabled").value(false))
                .andExpect(jsonPath("$.supportSafe").value(true))
                .andExpect(jsonPath("$.sources[0].kind").value("message"))
                .andExpect(content().string(containsString("\"kind\":\"decision\"")))
                .andExpect(jsonPath("$.approvalReceipts[0].actorRef").value("user:test"))
                .andExpect(jsonPath("$.approvalReceipts[0].requestedAction").value("Create a task from the summary"))
                .andExpect(jsonPath("$.approvalReceipts[0].approvedAction").value("none - Sprint 4 Weaver scout is read-only"))
                .andExpect(jsonPath("$.approvalReceipts[0].targetRef").value("conversation:channel-general"))
                .andExpect(jsonPath("$.approvalReceipts[0].resultCategory").value("blocked"))
                .andExpect(content().string(not(containsString("secretref://"))))
                .andExpect(content().string(not(containsString("access_token"))));

        verify(auditEventPublisher).publish(argThat(event ->
                event != null
                        && event.action() == AuditAction.WEAVER_SCOUT_SUMMARY_REQUESTED
                        && Boolean.TRUE.equals(event.payload().get("readOnly"))
                        && Boolean.TRUE.equals(event.payload().get("supportSafe"))));
    }

    @Test
    void chatProviderReplacementDryRunIsAdminOnlySupportSafeAndAudited() throws Exception {
        allowChatPermission(ContextPermission.ADMIN);
        String payload = """
                {
                  "sourceAdapter": "slack",
                  "targetAdapter": "synapse-homeserver",
                  "conversationCount": 3,
                  "messageCount": 42,
                  "attachmentCount": 2,
                  "encryptedRoomCount": 1,
                  "identityConflictCount": 1
                }
                """;

        mockMvc.perform(post("/api/admin/chat/provider-replacements/dry-run")
                        .with(workspaceJwt("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("chat"))
                .andExpect(jsonPath("$.status").value("requires-admin-review"))
                .andExpect(jsonPath("$.sourceAdapter").value("slack"))
                .andExpect(jsonPath("$.targetAdapter").value("synapse-homeserver"))
                .andExpect(jsonPath("$.inventory.conversations").value(3))
                .andExpect(jsonPath("$.supportSafe").value(true))
                .andExpect(jsonPath("$.providerDiagnosticsRedacted").value(true))
                .andExpect(jsonPath("$.preflight", everyItem(not(containsString("https://")))))
                .andExpect(jsonPath("$.lossyWarnings[0]").value(containsString("lossy canonical mapping")))
                .andExpect(jsonPath("$.conflicts[0]").value(containsString("Membership identity conflicts")));

        verify(auditEventPublisher).publish(argThat(event ->
                event != null
                        && event.action() == AuditAction.CHAT_PROVIDER_REPLACEMENT_DRY_RUN
                        && "weave:chat".equals(event.sourceRef())
                        && Boolean.TRUE.equals(event.payload().get("supportSafe"))));
    }

    private ChatReadiness memberReadiness() {
        return new ChatReadiness(
                "chat-domain-facade-v1",
                "chat",
                ChatMemberState.MISCONFIGURED,
                "Chat is not ready for members in this workspace. Ask an admin to review Workspace Health.",
                true,
                true,
                false,
                false,
                false,
                null,
                new ChatHistoryPolicy(
                        "conversation_members",
                        "organization_default_retention",
                        false,
                        true,
                        List.of()),
                Map.of("domain", "chat", "state", "not_configured", "diagnosticsExposed", false),
                Instant.parse("2026-05-25T08:00:00Z"));
    }

    private void allowChatPermission(ContextPermission permission) {
        when(contextAuthorizationPort.check(argThat(request ->
                request != null
                        && "tenant-default".equals(request.tenantId())
                        && "workspace-default".equals(request.contextId())
                        && "user:test".equals(request.principalRef())
                        && request.permission() == permission)))
                .thenReturn(ContextAuthorizationDecision.allow("test allow"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor workspaceJwt(String role) {
        return workspaceJwt(role, List.of());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor workspaceJwt(String role, List<String> groups) {
        return jwt().jwt(jwt -> jwt
                        .subject("user-123")
                        .claim("iss", "https://auth.example.invalid/realms/acme")
                        .claim("preferred_username", "test")
                        .claim("weave_tenant_id", "tenant-default")
                        .claim("realm_access", Map.of("roles", List.of(role)))
                        .claim("groups", groups)
                        .claim("aud", List.of("weave-app")))
                .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor memberJwt() {
        return jwt()
                .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"), new SimpleGrantedAuthority("ROLE_MEMBER"))
                .jwt(jwt -> jwt
                        .subject("member-123")
                        .claim("iss", "https://auth.example.invalid/realms/acme"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor adminJwt() {
        return jwt()
                .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"), new SimpleGrantedAuthority("ROLE_ADMIN"))
                .jwt(jwt -> jwt
                        .subject("admin-123")
                        .claim("iss", "https://auth.example.invalid/realms/acme"));
    }
}

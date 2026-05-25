package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private ContextAuthorizationPort contextAuthorizationPort;

    @MockBean
    private AuditEventPublisher auditEventPublisher;

    @Test
    void chatConversationsRequireAuthenticatedWorkspaceScope() throws Exception {
        mockMvc.perform(get("/api/chat/conversations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));
    }

    @Test
    void chatConversationsUseCanonicalWeaveVocabularyWithoutProviderDiagnostics() throws Exception {
        allowChatPermission(ContextPermission.VIEW);

        mockMvc.perform(get("/api/chat/conversations")
                        .with(workspaceJwt("member")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.domain").value("chat"))
                .andExpect(jsonPath("$.releaseStatus").value("canonical-domain-facade"))
                .andExpect(jsonPath("$.source").value("weave-chat-domain-facade"))
                .andExpect(jsonPath("$.readiness.impactState").value("usable"))
                .andExpect(jsonPath("$.readiness.diagnosticsRedacted").value(true))
                .andExpect(jsonPath("$.readiness.grantedCapabilities[0]").value("chat.read"))
                .andExpect(jsonPath("$.conversations[0].id").value("channel-general"))
                .andExpect(jsonPath("$.conversations[0].kind").value("channel"))
                .andExpect(jsonPath("$.conversations[0].membership.principalRef").value("user:test"))
                .andExpect(jsonPath("$.conversations[0].historyPolicy.policyKey").value("workspace-default-history"))
                .andExpect(jsonPath("$.conversations[0].attachmentPolicy.rawProviderMediaUrlsExposed").value(false))
                .andExpect(jsonPath("$..matrix").doesNotExist())
                .andExpect(jsonPath("$..roomId").doesNotExist())
                .andExpect(jsonPath("$..providerUrl").doesNotExist());
    }

    @Test
    void chatReadIsDeniedByCapabilityPolicyBeforeContextOrProviderAccess() throws Exception {
        mockMvc.perform(get("/api/chat/conversations")
                        .with(workspaceJwt("guest")))
                .andExpect(status().isForbidden())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").value("capability-policy-blocked"))
                .andExpect(jsonPath("$.details.module").value("chat"))
                .andExpect(jsonPath("$.details.requiredCapability").value("chat.read"))
                .andExpect(jsonPath("$.details.diagnosticsRedacted").value(true));

        verifyNoInteractions(contextAuthorizationPort);
    }

    @Test
    void chatSendRequiresCapabilityAndContextPermissionThenPublishesSupportSafeAudit() throws Exception {
        allowChatPermission(ContextPermission.EDIT);

        mockMvc.perform(post("/api/chat/conversations/channel-general/messages")
                        .with(workspaceJwt("member"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Release notes draft is ready.\",\"attachmentRefs\":[\"weave-file:release-notes\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value("channel-general"))
                .andExpect(jsonPath("$.senderRef").value("user:test"))
                .andExpect(jsonPath("$.text").value("Release notes draft is ready."))
                .andExpect(jsonPath("$.attachmentRefs[0]").value("weave-file:release-notes"))
                .andExpect(jsonPath("$.encryptedProviderContentRedacted").value(false))
                .andExpect(jsonPath("$..eventId").doesNotExist())
                .andExpect(jsonPath("$..roomId").doesNotExist());

        verify(auditEventPublisher).publish(argThat(event ->
                event != null
                        && event.action() == AuditAction.CHAT_MESSAGE_SENT
                        && "weave:chat".equals(event.sourceRef())
                        && Boolean.TRUE.equals(event.payload().get("supportSafe"))
                        && Boolean.TRUE.equals(event.payload().get("diagnosticsRedacted"))));
    }

    @Test
    void chatSendRejectsRawProviderAttachmentUrlsSupportSafely() throws Exception {
        allowChatPermission(ContextPermission.EDIT);

        mockMvc.perform(post("/api/chat/conversations/channel-general/messages")
                        .with(workspaceJwt("member"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"bad attachment\",\"attachmentRefs\":[\"https://matrix.example.invalid/media/token\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("chat-validation"))
                .andExpect(jsonPath("$.details.field").value("attachmentRefs"))
                .andExpect(jsonPath("$.details.diagnosticsRedacted").value(true));
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
        return jwt().jwt(jwt -> jwt
                        .subject("user-123")
                        .claim("preferred_username", "test")
                        .claim("weave_tenant_id", "tenant-default")
                        .claim("realm_access", java.util.Map.of("roles", java.util.List.of(role)))
                        .claim("aud", java.util.List.of("weave-app")))
                .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"));
    }
}

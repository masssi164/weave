package com.massimotter.weave.backend.controller;

import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.massimotter.weave.backend.chat.ChatDomainFacadeService;
import com.massimotter.weave.backend.chat.domain.ChatHistoryPolicy;
import com.massimotter.weave.backend.chat.domain.ChatMemberState;
import com.massimotter.weave.backend.chat.domain.ChatMigrationPreflightReport;
import com.massimotter.weave.backend.chat.domain.ChatReadiness;
import com.massimotter.weave.backend.config.ApiAccessDeniedHandler;
import com.massimotter.weave.backend.config.ApiAuthenticationEntryPoint;
import com.massimotter.weave.backend.config.ApiErrorResponseWriter;
import com.massimotter.weave.backend.config.SecurityConfig;
import com.massimotter.weave.backend.exception.ApiExceptionHandler;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
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
        ApiExceptionHandler.class
})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.example.invalid/realms/weave"
})
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private ChatDomainFacadeService chatDomainFacadeService;

    @Test
    void memberReadinessExposesOnlyStableProductState() throws Exception {
        when(chatDomainFacadeService.memberReadiness(any())).thenReturn(memberReadiness());

        mockMvc.perform(get("/api/chat/readiness").with(memberJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractVersion").value("chat-domain-facade-v1"))
                .andExpect(jsonPath("$.domain").value("chat"))
                .andExpect(jsonPath("$.memberState").value("misconfigured"))
                .andExpect(jsonPath("$.memberClientMayConfigureProvider").value(false))
                .andExpect(jsonPath("$.providerMapping").doesNotExist())
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("secretref://"))))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("access_token"))))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("xoxb-"))));
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
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("access_token"))))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("secretref://"))));
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
                Map.of("domain", "chat", "state", "misconfigured", "diagnosticsExposed", false),
                Instant.parse("2026-05-25T08:00:00Z"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor memberJwt() {
        return jwt()
                .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"), new SimpleGrantedAuthority("ROLE_MEMBER"))
                .jwt(jwt -> jwt.subject("member-123"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor adminJwt() {
        return jwt()
                .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"), new SimpleGrantedAuthority("ROLE_ADMIN"))
                .jwt(jwt -> jwt.subject("admin-123"));
    }
}

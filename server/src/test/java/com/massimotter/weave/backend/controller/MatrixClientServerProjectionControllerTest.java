package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.chat.ChatDomainFacadeService;
import com.massimotter.weave.backend.chat.domain.ChatConversation;
import com.massimotter.weave.backend.chat.domain.ChatConversations;
import com.massimotter.weave.backend.chat.domain.ChatHistoryPolicy;
import com.massimotter.weave.backend.chat.domain.ChatEncryptionState;
import com.massimotter.weave.backend.chat.domain.ChatMemberState;
import com.massimotter.weave.backend.chat.domain.ChatMessage;
import com.massimotter.weave.backend.chat.domain.ChatMessages;
import com.massimotter.weave.backend.chat.domain.ChatReadiness;
import com.massimotter.weave.backend.config.ApiAccessDeniedHandler;
import com.massimotter.weave.backend.config.ApiAuthenticationEntryPoint;
import com.massimotter.weave.backend.config.ApiErrorResponseWriter;
import com.massimotter.weave.backend.config.SecurityConfig;
import com.massimotter.weave.backend.exception.ApiExceptionHandler;
import com.massimotter.weave.backend.matrix.MatrixProtocolCoreService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = MatrixClientServerProjectionController.class,
        excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@Import({
        SecurityConfig.class,
        ApiAuthenticationEntryPoint.class,
        ApiAccessDeniedHandler.class,
        ApiErrorResponseWriter.class,
        ApiExceptionHandler.class,
        MatrixProtocolCoreService.class
})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.example.invalid/realms/weave",
        "weave.matrix.facade.server-name=api.weave.test"
})
class MatrixClientServerProjectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private ChatDomainFacadeService chatDomainFacadeService;

    @Test
    void matrixClientServerProjectionRequiresWorkspaceToken() throws Exception {
        mockMvc.perform(get("/_matrix/client/v3/sync"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/_matrix/client/v3/sync").with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void matrixClientServerProjectionAdvertisesReservedMethods() throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf("OPTIONS"), "/_matrix/client/v3/sync")
                        .with(workspaceJwt()))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.ALLOW, "OPTIONS, GET, POST, PUT"))
                .andExpect(header().string("X-Weave-Projection", "matrix-client-server"))
                .andExpect(header().string("X-Weave-Matrix-Core", "rust-ruma-jni"));
    }

    @Test
    void versionsIsSerializedByTheLinkedRustRumaCore() throws Exception {
        mockMvc.perform(get("/_matrix/client/versions").with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versions[0]").value("v1.18"))
                .andExpect(jsonPath("$.matrixCore.protocolSurface").value("matrix-client-server-facade"))
                .andExpect(jsonPath("$.matrixCore.oidcGatekeeper").value("spring-boot-resource-server"))
                .andExpect(jsonPath("$.matrixCore.northboundHomeserverDependency").value(false))
                .andExpect(jsonPath("$.matrixCore.nativeLinked").value(true))
                .andExpect(jsonPath("$.matrixCore.serverName").value("api.weave.test"))
                .andExpect(jsonPath("$.matrixCore.supportedEndpoints[2]").value(containsString("/sync")))
                .andExpect(content().string(not(containsString("Synapse"))))
                .andExpect(content().string(not(containsString("access_token"))));
    }

    @Test
    void whoamiUsesRumaValidatedIdentityDerivedFromOidcSubject() throws Exception {
        mockMvc.perform(get("/_matrix/client/v3/account/whoami").with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user_id").value("@user_example.com:api.weave.test"))
                .andExpect(jsonPath("$.device_id").value("weave-oidc"))
                .andExpect(jsonPath("$.is_guest").value(false));
    }

    @Test
    void syncProjectsCanonicalChatThroughRustWithStableWeaveCursor() throws Exception {
        stubConversation();

        mockMvc.perform(get("/_matrix/client/v3/sync").with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.next_batch").value("weave.s1.636861742d7265766973696f6e2d37"))
                .andExpect(jsonPath("$.rooms.join['!channel-general:api.weave.test'].state.events[0].content.name")
                        .value("General"))
                .andExpect(jsonPath("$.rooms.join['!channel-general:api.weave.test'].timeline.events[0].content.body")
                        .value("Hello from Weave Chat"))
                .andExpect(jsonPath("$.rooms.join['!channel-general:api.weave.test'].timeline.events[0].content.providerDataPlaneExposed")
                        .value(false))
                .andExpect(content().string(not(containsString("providerAccessToken"))))
                .andExpect(content().string(not(containsString("homeserver"))));
    }

    @Test
    void invalidProviderShapedSinceTokenFailsBeforeDomainRead() throws Exception {
        mockMvc.perform(get("/_matrix/client/v3/sync?since=provider-token").with(workspaceJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errcode").value("M_BAD_JSON"))
                .andExpect(jsonPath("$.supportSafe").value(true));
    }

    @Test
    void sendParsesInRustAndForwardsTransactionForCanonicalIdempotency() throws Exception {
        when(chatDomainFacadeService.sendMessage(
                eq("channel-general"),
                eq("txn-1"),
                eq("Sent through Matrix"),
                any()))
                .thenReturn(message("msg-sent", "Sent through Matrix"));

        mockMvc.perform(put("/_matrix/client/v3/rooms/!channel-general:api.weave.test/send/m.room.message/txn-1")
                        .with(workspaceJwt())
                        .contentType("application/json")
                        .content("""
                                {"msgtype":"m.text","body":"Sent through Matrix"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event_id").value("$msg-sent:api.weave.test"));

        verify(chatDomainFacadeService).sendMessage(
                eq("channel-general"),
                eq("txn-1"),
                eq("Sent through Matrix"),
                any());
    }

    @Test
    void unsupportedMessageTypeFailsClosedBeforeCanonicalSend() throws Exception {
        mockMvc.perform(put("/_matrix/client/v3/rooms/!channel-general:api.weave.test/send/m.room.message/txn-2")
                        .with(workspaceJwt())
                        .contentType("application/json")
                        .content("""
                                {"msgtype":"m.image","body":"not supported"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errcode").value("M_UNSUPPORTED"));

        verify(chatDomainFacadeService, never()).sendMessage(any(), any(), any(), any());
    }

    @Test
    void joinedRoomsAndMessagesUseCanonicalIdentifiers() throws Exception {
        stubConversation();

        mockMvc.perform(get("/_matrix/client/v3/joined_rooms").with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.joined_rooms[0]").value("!channel-general:api.weave.test"));

        mockMvc.perform(get("/_matrix/client/v3/rooms/!channel-general:api.weave.test/messages")
                        .with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chunk[0].event_id").value("$msg-1:api.weave.test"))
                .andExpect(jsonPath("$.chunk[0].content.body").value("Hello from Weave Chat"));
    }

    private void stubConversation() {
        when(chatDomainFacadeService.conversations(any())).thenReturn(conversations());
        when(chatDomainFacadeService.messages(eq("channel-general"), any())).thenReturn(messages());
        when(chatDomainFacadeService.syncCursor(any())).thenReturn("chat-revision-7");
    }

    private ChatConversations conversations() {
        return new ChatConversations(readiness(), List.of(new ChatConversation(
                "channel-general",
                "General",
                "channel",
                ChatMemberState.READY,
                "Chat is available.",
                Instant.parse("2026-07-08T10:00:00Z"),
                ChatEncryptionState.unencrypted(),
                historyPolicy(),
                List.of(),
                List.of())));
    }

    private ChatMessages messages() {
        return new ChatMessages(readiness(), "channel-general", List.of(message("msg-1", "Hello from Weave Chat")));
    }

    private ChatMessage message(String messageId, String body) {
        return new ChatMessage(
                messageId,
                "channel-general",
                "user:alice",
                Instant.parse("2026-07-08T10:00:00Z"),
                body,
                "sent",
                List.of());
    }

    private ChatReadiness readiness() {
        return new ChatReadiness(
                "chat-domain-facade-v1",
                "chat",
                ChatMemberState.READY,
                "Chat is available.",
                false,
                true,
                false,
                false,
                false,
                null,
                historyPolicy(),
                Map.of(),
                Instant.parse("2026-07-08T10:00:00Z"));
    }

    private ChatHistoryPolicy historyPolicy() {
        return new ChatHistoryPolicy("conversation_members", "organization_default_retention", false, true, List.of());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor workspaceJwt() {
        return jwt().jwt(jwt -> jwt
                        .subject("user@example.com")
                        .claim("iss", "https://auth.example.invalid/realms/acme")
                        .claim("aud", List.of("weave-app"))
                        .claim("weave_tenant_id", "tenant-default")
                        .claim("realm_access", Map.of("roles", List.of("member"))))
                .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"));
    }
}

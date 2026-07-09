package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.config.ApiAccessDeniedHandler;
import com.massimotter.weave.backend.config.ApiAuthenticationEntryPoint;
import com.massimotter.weave.backend.config.ApiErrorResponseWriter;
import com.massimotter.weave.backend.config.SecurityConfig;
import com.massimotter.weave.backend.exception.ApiExceptionHandler;
import com.massimotter.weave.backend.model.chat.ChatAttachmentPolicyResponse;
import com.massimotter.weave.backend.model.chat.ChatConversationResponse;
import com.massimotter.weave.backend.model.chat.ChatConversationsResponse;
import com.massimotter.weave.backend.model.chat.ChatHistoryPolicyResponse;
import com.massimotter.weave.backend.model.chat.ChatMembershipResponse;
import com.massimotter.weave.backend.model.chat.ChatMessageResponse;
import com.massimotter.weave.backend.model.chat.ChatMessagesResponse;
import com.massimotter.weave.backend.model.chat.ChatReadinessResponse;
import com.massimotter.weave.backend.model.chat.ChatSendMessageRequest;
import com.massimotter.weave.backend.matrix.MatrixProtocolCoreService;
import com.massimotter.weave.backend.service.ChatFacadeService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.example.invalid/realms/weave"
})
class MatrixClientServerProjectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private ChatFacadeService chatFacadeService;

    @Test
    void matrixClientServerProjectionRequiresWorkspaceToken() throws Exception {
        mockMvc.perform(get("/_matrix/client/v3/sync"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/_matrix/client/v3/sync")
                        .with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void matrixClientServerProjectionAdvertisesReservedMethods() throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf("OPTIONS"), "/_matrix/client/v3/sync")
                        .with(workspaceJwt()))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.ALLOW, "OPTIONS, GET, POST, PUT"))
                .andExpect(header().string("X-Weave-Projection", "matrix-client-server"))
                .andExpect(header().string("X-Weave-Matrix-Core", "rust-ruma-jni-target"));
    }

    @Test
    void matrixClientServerProjectionVersionsAdvertisesOidcGatedRustCoreFacade() throws Exception {
        mockMvc.perform(get("/_matrix/client/versions")
                        .with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Weave-Projection", "matrix-client-server"))
                .andExpect(header().string("X-Weave-Matrix-Core", "rust-ruma-jni-target"))
                .andExpect(jsonPath("$.versions[0]").value("v1.18"))
                .andExpect(jsonPath("$.weaveBoundary").value("northbound-matrix-client-server"))
                .andExpect(jsonPath("$.canonicalDomain").value("chat"))
                .andExpect(jsonPath("$.providerDataPlaneExposed").value(false))
                .andExpect(jsonPath("$.matrixCore.protocolSurface").value("matrix-client-server-facade"))
                .andExpect(jsonPath("$.matrixCore.oidcGatekeeper").value("spring-boot-resource-server"))
                .andExpect(jsonPath("$.matrixCore.northboundHomeserverDependency").value(false))
                .andExpect(jsonPath("$.matrixCore.rustProtocolCore").value("ruma-serde-serde_json-thiserror-tracing"))
                .andExpect(jsonPath("$.matrixCore.serverJniBoundary").value("server-jni-wrapper"))
                .andExpect(jsonPath("$.matrixCore.flutterBridgeBoundary").value("flutter-rust-bridge"))
                .andExpect(jsonPath("$.matrixCore.nativeLibrary").value("weave_matrix_core"))
                .andExpect(content().string(not(containsString("Synapse"))))
                .andExpect(content().string(not(containsString("providerAccessToken"))))
                .andExpect(content().string(not(containsString("access_token"))));
    }

    @Test
    void matrixClientServerProjectionWhoamiDerivesMatrixIdentityFromOidcPrincipal() throws Exception {
        mockMvc.perform(get("/_matrix/client/v3/account/whoami")
                        .with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Weave-Projection", "matrix-client-server"))
                .andExpect(jsonPath("$.user_id").value("@user_example.com:weave.local"))
                .andExpect(jsonPath("$.device_id").value("weave-oidc"))
                .andExpect(jsonPath("$.is_guest").value(false))
                .andExpect(jsonPath("$.providerDataPlaneExposed").value(false))
                .andExpect(jsonPath("$.matrixCore.oidcGatekeeper").value("spring-boot-resource-server"));
    }

    @Test
    void matrixClientServerProjectionSyncsCanonicalChatAsMatrixRoomsWithoutProviderPayloads() throws Exception {
        when(chatFacadeService.conversations(any())).thenReturn(conversations());
        when(chatFacadeService.messages(any(), eq("channel-general"))).thenReturn(messages());

        mockMvc.perform(get("/_matrix/client/v3/sync")
                        .with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Weave-Projection", "matrix-client-server"))
                .andExpect(jsonPath("$.weaveBoundary").value("northbound-matrix-client-server"))
                .andExpect(jsonPath("$.canonicalDomain").value("chat"))
                .andExpect(jsonPath("$.providerDataPlaneExposed").value(false))
                .andExpect(jsonPath("$.matrixCore.oidcGatekeeper").value("spring-boot-resource-server"))
                .andExpect(jsonPath("$.matrixCore.northboundHomeserverDependency").value(false))
                .andExpect(jsonPath("$.matrixCore.nativeMethod").value("matrixFacadeDescriptorJson"))
                .andExpect(jsonPath("$.rooms.join['!channel-general:weave.local'].state.events[0].type")
                        .value("m.room.name"))
                .andExpect(jsonPath("$.rooms.join['!channel-general:weave.local'].state.events[0].content.name")
                        .value("General"))
                .andExpect(jsonPath("$.rooms.join['!channel-general:weave.local'].timeline.events[0].type")
                        .value("m.room.message"))
                .andExpect(jsonPath("$.rooms.join['!channel-general:weave.local'].timeline.events[0].content.body")
                        .value("Hello from Weave Chat"))
                .andExpect(jsonPath("$.rooms.join['!channel-general:weave.local'].timeline.events[0].content.providerDataPlaneExposed")
                        .value(false))
                .andExpect(content().string(not(containsString("BridgeAdapter"))))
                .andExpect(content().string(not(containsString("providerAccessToken"))))
                .andExpect(content().string(not(containsString("access_token"))))
                .andExpect(content().string(not(containsString("homeserver"))));
    }

    @Test
    void matrixClientServerProjectionSendsViaCanonicalChatFacade() throws Exception {
        when(chatFacadeService.sendMessage(any(), eq("channel-general"), any(ChatSendMessageRequest.class)))
                .thenReturn(new ChatMessageResponse(
                        "msg-sent",
                        "channel-general",
                        "user:alice",
                        "Sent through Matrix",
                        List.of(),
                        true,
                        false,
                        Instant.parse("2026-07-08T10:05:00Z"),
                        Map.of("supportSafe", true)));

        mockMvc.perform(put("/_matrix/client/v3/rooms/!channel-general:weave.local/send/m.room.message/txn-1")
                        .with(workspaceJwt())
                        .contentType("application/json")
                        .content("""
                                {"msgtype":"m.text","body":"Sent through Matrix"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Weave-Projection", "matrix-client-server"))
                .andExpect(header().string("X-Weave-Matrix-Core", "rust-ruma-jni-target"))
                .andExpect(jsonPath("$.event_id").value("$msg-sent:weave.local"));

        verify(chatFacadeService).sendMessage(any(), eq("channel-general"), any(ChatSendMessageRequest.class));
    }

    @Test
    void matrixClientServerProjectionListsJoinedRoomsAndRoomMessages() throws Exception {
        when(chatFacadeService.conversations(any())).thenReturn(conversations());
        when(chatFacadeService.messages(any(), eq("channel-general"))).thenReturn(messages());

        mockMvc.perform(get("/_matrix/client/v3/joined_rooms")
                        .with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.joined_rooms[0]").value("!channel-general:weave.local"));

        mockMvc.perform(get("/_matrix/client/v3/rooms/!channel-general:weave.local/messages")
                        .with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chunk[0].event_id").value("$msg-1:weave.local"))
                .andExpect(jsonPath("$.chunk[0].content.body").value("Hello from Weave Chat"));
    }

    private ChatConversationsResponse conversations() {
        return new ChatConversationsResponse(
                "chat",
                "canonical-domain-facade",
                "weave-chat-domain-facade",
                readiness(),
                List.of(new ChatConversationResponse(
                        "channel-general",
                        "workspace-default",
                        "channel",
                        "General",
                        new ChatMembershipResponse("user:alice", "joined", "member"),
                        new ChatHistoryPolicyResponse("workspace-default-history", "joined-members", true, true),
                        new ChatAttachmentPolicyResponse(true, 8, false),
                        List.of("send-message"),
                        Instant.parse("2026-07-08T10:00:00Z"))));
    }

    private ChatMessagesResponse messages() {
        return new ChatMessagesResponse(
                "channel-general",
                readiness(),
                List.of(new ChatMessageResponse(
                        "msg-1",
                        "channel-general",
                        "user:alice",
                        "Hello from Weave Chat",
                        List.of(),
                        true,
                        false,
                        Instant.parse("2026-07-08T10:00:00Z"),
                        Map.of("supportSafe", true))));
    }

    private ChatReadinessResponse readiness() {
        return new ChatReadinessResponse(
                "available",
                "Weave Chat is available through the workspace Chat domain.",
                List.of("chat.read", "chat.send"),
                true);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor workspaceJwt() {
        return jwt().jwt(jwt -> jwt
                        .subject("user@example.com")
                        .claim("iss", "https://auth.example.invalid/realms/acme")
                        .claim("aud", java.util.List.of("weave-app"))
                        .claim("weave_tenant_id", "tenant-default")
                        .claim("realm_access", java.util.Map.of("roles", java.util.List.of("member"))))
                .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"));
    }
}

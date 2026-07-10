package com.massimotter.weave.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.chat.ChatDomainFacadeService;
import com.massimotter.weave.backend.chat.domain.ChatConversation;
import com.massimotter.weave.backend.chat.domain.ChatConversations;
import com.massimotter.weave.backend.chat.domain.ChatEncryptionState;
import com.massimotter.weave.backend.chat.domain.ChatEventContent;
import com.massimotter.weave.backend.chat.domain.ChatEventKind;
import com.massimotter.weave.backend.chat.domain.ChatHistoryPolicy;
import com.massimotter.weave.backend.chat.domain.ChatMemberState;
import com.massimotter.weave.backend.chat.domain.ChatMembership;
import com.massimotter.weave.backend.chat.domain.ChatReadiness;
import com.massimotter.weave.backend.chat.domain.ChatRelation;
import com.massimotter.weave.backend.chat.domain.ChatTimeline;
import com.massimotter.weave.backend.chat.domain.ChatTimelineEvent;
import com.massimotter.weave.backend.config.ApiAccessDeniedHandler;
import com.massimotter.weave.backend.config.ApiAuthenticationEntryPoint;
import com.massimotter.weave.backend.config.ApiErrorResponseWriter;
import com.massimotter.weave.backend.config.SecurityConfig;
import com.massimotter.weave.backend.exception.ApiExceptionHandler;
import com.massimotter.weave.backend.matrix.MatrixFacadeClientStateService;
import com.massimotter.weave.backend.matrix.MatrixProtocolCoreService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
        MatrixProtocolCoreService.class,
        MatrixFacadeClientStateService.class
})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.example.invalid/realms/weave",
        "weave.matrix.facade.server-name=api.weave.test",
        "weave.matrix.facade.base-url=https://api.weave.test"
})
class MatrixClientServerProjectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    void matrixDiscoveryIsPublicAndPointsAtTheWeaveFacade() throws Exception {
        mockMvc.perform(get("/.well-known/matrix/client"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['m.homeserver'].base_url").value("https://api.weave.test"));
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
    void openClawStartupCanLoadPushRulesCreateAFilterAndSyncAccountData() throws Exception {
        stubConversation();

        mockMvc.perform(get("/_matrix/client/v3/pushrules/").with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.global.override").isArray());

        String filterResponse = mockMvc.perform(post("/_matrix/client/v3/user/@user_example.com:api.weave.test/filter")
                        .with(workspaceJwt())
                        .contentType("application/json")
                        .content("""
                                {"room":{"timeline":{"limit":20}}}
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String filterId = objectMapper.readTree(filterResponse).path("filter_id").asText();

        mockMvc.perform(get("/_matrix/client/v3/user/@user_example.com:api.weave.test/filter/" + filterId)
                        .with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.room.timeline.limit").value(20));

        mockMvc.perform(put("/_matrix/client/v3/user/@user_example.com:api.weave.test/account_data/m.direct")
                        .with(workspaceJwt())
                        .contentType("application/json")
                        .content("""
                                {"@assistant:api.weave.test":["!channel-general:api.weave.test"]}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/_matrix/client/v3/sync?filter=" + filterId).with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account_data.events[0].type").value("m.direct"));
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
        when(chatDomainFacadeService.sendEvent(
                eq("channel-general"),
                eq("txn-1"),
                any(ChatEventContent.class),
                any()))
                .thenReturn(event("msg-sent", ChatEventContent.text("Sent through Matrix")));

        mockMvc.perform(put("/_matrix/client/v3/rooms/!channel-general:api.weave.test/send/m.room.message/txn-1")
                        .with(workspaceJwt())
                        .contentType("application/json")
                        .content("""
                                {"msgtype":"m.text","body":"Sent through Matrix"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event_id").value("$msg-sent:api.weave.test"));

        ArgumentCaptor<ChatEventContent> content = ArgumentCaptor.forClass(ChatEventContent.class);
        verify(chatDomainFacadeService).sendEvent(
                eq("channel-general"),
                eq("txn-1"),
                content.capture(),
                any());
        assertThat(content.getValue().body()).isEqualTo("Sent through Matrix");
    }

    @Test
    void approvalMetadataAndReactionUseCanonicalTimelineEvents() throws Exception {
        when(chatDomainFacadeService.sendEvent(eq("channel-general"), eq("approval-1"), any(), any()))
                .thenReturn(event("approval-event", ChatEventContent.text("Approve calendar creation")));
        when(chatDomainFacadeService.sendEvent(eq("channel-general"), eq("reaction-1"), any(), any()))
                .thenReturn(event("reaction-event", new ChatEventContent(
                        ChatEventKind.REACTION,
                        null,
                        null,
                        null,
                        null,
                        new ChatRelation("reaction", "approval-event", null),
                        "✅",
                        Map.of())));

        mockMvc.perform(put("/_matrix/client/v3/rooms/!channel-general:api.weave.test/send/m.room.message/approval-1")
                        .with(workspaceJwt())
                        .contentType("application/json")
                        .content("""
                                {
                                  "msgtype":"m.text",
                                  "body":"Approve calendar creation",
                                  "com.openclaw.approval":{"version":1,"kind":"plugin","state":"pending"}
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/_matrix/client/v3/rooms/!channel-general:api.weave.test/send/m.reaction/reaction-1")
                        .with(workspaceJwt())
                        .contentType("application/json")
                        .content("""
                                {"m.relates_to":{"rel_type":"m.annotation","event_id":"$approval-event:api.weave.test","key":"✅"}}
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<ChatEventContent> content = ArgumentCaptor.forClass(ChatEventContent.class);
        verify(chatDomainFacadeService, times(2))
                .sendEvent(eq("channel-general"), any(), content.capture(), any());
        assertThat(content.getAllValues().get(0).presentationExtensions())
                .containsKey("com.openclaw.approval");
        assertThat(content.getAllValues().get(1).kind()).isEqualTo(ChatEventKind.REACTION);
        assertThat(content.getAllValues().get(1).relation().targetEventId()).isEqualTo("approval-event");
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

        verify(chatDomainFacadeService, never()).sendEvent(any(), any(), any(), any());
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

    @Test
    void stockOpenClawMemberReceiptAndTypingCallsStayOnCanonicalChat() throws Exception {
        stubConversation();

        mockMvc.perform(get("/_matrix/client/v3/rooms/!channel-general:api.weave.test/joined_members")
                        .with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.joined['@alice:api.weave.test'].display_name").value("alice"));

        mockMvc.perform(post("/_matrix/client/v3/rooms/!channel-general:api.weave.test/receipt/m.read/$msg-1:api.weave.test")
                        .with(workspaceJwt())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/_matrix/client/v3/rooms/!channel-general:api.weave.test/typing/@user_example.com:api.weave.test")
                        .with(workspaceJwt())
                        .contentType("application/json")
                        .content("""
                                {"typing":true,"timeout":15000}
                                """))
                .andExpect(status().isOk());

        verify(chatDomainFacadeService).markRead(eq("channel-general"), eq("msg-1"), any());
        verify(chatDomainFacadeService).setTyping(eq("channel-general"), eq(true), eq(15_000), any());
    }

    @Test
    void roomLifecycleStateAndProfileProjectCanonicalChat() throws Exception {
        stubConversation();
        when(chatDomainFacadeService.createConversation(any(), eq("General"), eq("channel"), eq(List.of()), any()))
                .thenReturn(conversations().conversations().getFirst());

        mockMvc.perform(post("/_matrix/client/v3/createRoom")
                        .with(workspaceJwt())
                        .contentType("application/json")
                        .content("""
                                {"name":"General"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.room_id").value("!channel-general:api.weave.test"));

        mockMvc.perform(post("/_matrix/client/v3/join/!channel-general:api.weave.test")
                        .with(workspaceJwt())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.room_id").value("!channel-general:api.weave.test"));

        mockMvc.perform(get("/_matrix/client/v3/rooms/!channel-general:api.weave.test/state/m.room.name/")
                        .with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("General"));

        mockMvc.perform(get("/_matrix/client/v3/profile/@user_example.com:api.weave.test")
                        .with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayname").value("user_example.com"));

        mockMvc.perform(post("/_matrix/client/v3/rooms/!channel-general:api.weave.test/leave")
                        .with(workspaceJwt())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk());

        verify(chatDomainFacadeService).joinConversation(eq("channel-general"), any());
        verify(chatDomainFacadeService).leaveConversation(eq("channel-general"), any());
    }

    @Test
    void logoutRevokesThePresentedMatrixToken() throws Exception {
        var token = workspaceJwt("runtime-token-to-revoke");

        mockMvc.perform(post("/_matrix/client/v3/logout").with(token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/_matrix/client/v3/account/whoami").with(workspaceJwt("runtime-token-to-revoke")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errcode").value("M_UNKNOWN_TOKEN"));
    }

    private void stubConversation() {
        when(chatDomainFacadeService.conversations(any())).thenReturn(conversations());
        when(chatDomainFacadeService.conversation(eq("channel-general"), any()))
                .thenReturn(conversations().conversations().getFirst());
        when(chatDomainFacadeService.timeline(eq("channel-general"), any(), anyInt()))
                .thenReturn(timeline());
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
                List.of(new ChatMembership(
                        "membership-channel-general-user-alice",
                        "channel-general",
                        "user:alice",
                        "member",
                        "joined",
                        Instant.parse("2026-07-08T09:00:00Z"),
                        List.of("chat.read", "chat.send"))),
                List.of())));
    }

    private ChatTimeline timeline() {
        return new ChatTimeline("channel-general", List.of(event(
                "msg-1",
                ChatEventContent.text("Hello from Weave Chat"))));
    }

    private ChatTimelineEvent event(String eventId, ChatEventContent content) {
        return new ChatTimelineEvent(
                eventId,
                "channel-general",
                "user:alice",
                Instant.parse("2026-07-08T10:00:00Z"),
                content,
                "sent",
                false);
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
        return workspaceJwt(UUID.randomUUID().toString());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor workspaceJwt(String tokenValue) {
        return jwt().jwt(jwt -> jwt
                        .tokenValue(tokenValue)
                        .subject("user@example.com")
                        .claim("jti", tokenValue)
                        .claim("iss", "https://auth.example.invalid/realms/acme")
                        .claim("aud", List.of("weave-app"))
                        .claim("weave_tenant_id", "tenant-default")
                        .claim("realm_access", Map.of("roles", List.of("member"))))
                .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"));
    }
}

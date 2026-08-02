package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.support.HumanJwtTestSupport;

import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.chat.ChatDomainFacadeService;
import com.massimotter.weave.backend.chat.domain.ChatConversation;
import com.massimotter.weave.backend.chat.domain.ChatConversations;
import com.massimotter.weave.backend.chat.domain.ChatEncryptedEnvelope;
import com.massimotter.weave.backend.chat.domain.ChatEncryptionState;
import com.massimotter.weave.backend.chat.domain.ChatEventContent;
import com.massimotter.weave.backend.chat.domain.ChatEventKind;
import com.massimotter.weave.backend.chat.domain.ChatHistoryPolicy;
import com.massimotter.weave.backend.chat.domain.ChatMemberState;
import com.massimotter.weave.backend.chat.domain.ChatMembership;
import com.massimotter.weave.backend.chat.domain.ChatProviderUnavailableException;
import com.massimotter.weave.backend.chat.domain.ChatReadiness;
import com.massimotter.weave.backend.chat.domain.ChatRedactionReceipt;
import com.massimotter.weave.backend.chat.domain.ChatRelation;
import com.massimotter.weave.backend.chat.domain.ChatTimeline;
import com.massimotter.weave.backend.chat.domain.ChatTimelineEvent;
import com.massimotter.weave.backend.config.ApiAccessDeniedHandler;
import com.massimotter.weave.backend.config.ApiAuthenticationEntryPoint;
import com.massimotter.weave.backend.config.ApiErrorResponseWriter;
import com.massimotter.weave.backend.config.SecurityConfig;
import com.massimotter.weave.backend.exception.ApiExceptionHandler;
import com.massimotter.weave.backend.matrix.MatrixFacadeClientStateService;
import com.massimotter.weave.backend.matrix.MatrixFacadeClientStateStore;
import com.massimotter.weave.backend.matrix.MatrixE2eeStateService;
import com.massimotter.weave.backend.matrix.MatrixProtocolCoreService;
import com.massimotter.weave.backend.service.OrganizationIdentityContextResolver;
import com.massimotter.weave.backend.testing.InMemoryMatrixFacadeClientStateStore;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
        MatrixFacadeClientStateService.class,
        OrganizationIdentityContextResolver.class,
        InMemoryMatrixFacadeClientStateStore.class,
        MatrixE2eeStateService.class
})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.example.invalid/realms/weave",
        "weave.matrix.facade.server-name=api.weave.test",
        "weave.matrix.facade.base-url=https://api.weave.test"
})
class MatrixClientServerProjectionControllerTest {

    // WEAVE_CHAT_DOMAIN_FACADE

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ChatDomainFacadeService chatDomainFacadeService;

    @MockitoBean
    private MatrixFacadeClientStateStore stateStore;

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
                .andExpect(header().string(HttpHeaders.ALLOW, "OPTIONS, GET, POST, PUT, DELETE"))
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
        mockMvc.perform(get("/_matrix/client/v3/account/whoami")
                        .header(MatrixFacadeClientStateService.DEVICE_ID_HEADER, "WEAVE0123456789abcdef0123456789abcdef0123")
                        .with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user_id").value("@user_example.com:api.weave.test"))
                .andExpect(jsonPath("$.device_id").value("WEAVE0123456789abcdef0123456789abcdef0123"))
                .andExpect(jsonPath("$.is_guest").value(false));
    }

    @Test
    void malformedOidcIdentityRemainsAStableMatrixAuthorizationFailure() throws Exception {
        var endpoint = get("/_matrix/client/v3/account/whoami")
                .header(MatrixFacadeClientStateService.DEVICE_ID_HEADER, "WEAVEDEVICEINVALIDIDENTITY");

        mockMvc.perform(endpoint.with(workspaceJwtWithoutIssuer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errcode").value("M_FORBIDDEN"))
                .andExpect(content().string(not(containsString("missing-issuer"))));

        mockMvc.perform(get("/_matrix/client/v3/account/whoami")
                        .header(MatrixFacadeClientStateService.DEVICE_ID_HEADER, "WEAVEDEVICEINVALIDSUBJECT")
                        .with(workspaceJwtWithSubject("invalid subject")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errcode").value("M_FORBIDDEN"))
                .andExpect(content().string(not(containsString("invalid-identity-claim"))));
    }

    @Test
    void oidcSessionCannotRenameItselfToBypassDeviceRevocation() throws Exception {
        String token = "stable-oidc-device-session";

        mockMvc.perform(get("/_matrix/client/v3/account/whoami")
                        .header(MatrixFacadeClientStateService.DEVICE_ID_HEADER, "WEAVEDEVICEBOUNDONE")
                        .with(workspaceJwt(token)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/_matrix/client/v3/account/whoami")
                        .header(MatrixFacadeClientStateService.DEVICE_ID_HEADER, "WEAVEDEVICEBOUNDOTHER")
                        .with(workspaceJwt(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errcode").value("M_UNKNOWN_TOKEN"));
    }

    @Test
    void syncProjectsCanonicalChatThroughRustWithStableWeaveCursor() throws Exception {
        stubConversation();

        mockMvc.perform(get("/_matrix/client/v3/sync").with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.next_batch")
                        .value("weave.s1.636861742d7265766973696f6e2d377c653265653a30"))
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
    void syncProjectsCanonicalEncryptionStateForColdClients() throws Exception {
        ChatConversations encrypted = conversations(ChatEncryptionState.matrixMegolm());
        when(chatDomainFacadeService.conversations(any())).thenReturn(encrypted);
        when(chatDomainFacadeService.timeline(eq("channel-general"), any(), anyInt()))
                .thenReturn(timeline());
        when(chatDomainFacadeService.syncCursor(any())).thenReturn("chat-revision-7");

        mockMvc.perform(get("/_matrix/client/v3/sync").with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rooms.join['!channel-general:api.weave.test'].state.events[1].type")
                        .value("m.room.encryption"))
                .andExpect(jsonPath("$.rooms.join['!channel-general:api.weave.test'].state.events[1].state_key")
                        .value(""))
                .andExpect(jsonPath("$.rooms.join['!channel-general:api.weave.test'].state.events[1].content.algorithm")
                        .value(ChatEncryptedEnvelope.MEGOLM_V1));
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
        String filterId = objectMapper.readTree(filterResponse).path("filter_id").asString();

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

        mockMvc.perform(get("/_matrix/client/v3/user/@user_example.com:api.weave.test/account_data/m.direct")
                        .with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['@assistant:api.weave.test'][0]")
                        .value("!channel-general:api.weave.test"));

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
    void redactionProjectsItsOwnEventIdInsteadOfReusingTheTargetId() throws Exception {
        when(chatDomainFacadeService.redactEvent(
                eq("channel-general"),
                eq("msg-target"),
                eq("redaction-txn-1"),
                any()))
                .thenReturn(new ChatRedactionReceipt(
                        "redaction-event-1",
                        "msg-target",
                        "channel-general",
                        "user:member-1",
                        Instant.parse("2026-07-15T10:00:00Z")));

        mockMvc.perform(put("/_matrix/client/v3/rooms/!channel-general:api.weave.test/redact/"
                        + "$msg-target:api.weave.test/redaction-txn-1")
                        .with(workspaceJwt())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event_id").value("$redaction-event-1:api.weave.test"))
                .andExpect(content().string(not(containsString("$msg-target:api.weave.test"))));
    }

    @Test
    void providerThrottleBecomesAStableMatrixRetryWithoutLeakingDownstreamDetails() throws Exception {
        when(chatDomainFacadeService.sendEvent(
                eq("channel-general"),
                eq("txn-throttled"),
                any(ChatEventContent.class),
                any()))
                .thenThrow(new ChatProviderUnavailableException(
                        "chat-provider-throttled",
                        Instant.now().plusSeconds(121)));

        mockMvc.perform(put("/_matrix/client/v3/rooms/!channel-general:api.weave.test/send/m.room.message/txn-throttled")
                        .with(workspaceJwt())
                        .contentType("application/json")
                        .content("""
                                {"msgtype":"m.text","body":"never committed"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.errcode").value("M_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.retry_after_ms").isNumber())
                .andExpect(content().string(not(containsString("chat-provider-throttled"))))
                .andExpect(content().string(not(containsString("Synapse"))))
                .andExpect(content().string(not(containsString("never committed"))));
    }

    @Test
    void encryptedRoomRejectsPlaintextAndProjectsOpaqueCiphertext() throws Exception {
        // MATRIX_E2EE_CIPHERTEXT_ONLY
        when(chatDomainFacadeService.enableEncryption(
                eq("channel-general"),
                eq("m.megolm.v1.aes-sha2"),
                any()))
                .thenReturn(encryptedConversation());
        when(chatDomainFacadeService.sendEvent(
                eq("channel-general"),
                eq("txn-encrypted"),
                any(ChatEventContent.class),
                any()))
                .thenAnswer(invocation -> event("msg-encrypted", invocation.getArgument(2)));

        mockMvc.perform(put("/_matrix/client/v3/rooms/!channel-general:api.weave.test/state/m.room.encryption/")
                        .with(workspaceJwt())
                        .contentType("application/json")
                        .content("""
                                {"algorithm":"m.megolm.v1.aes-sha2"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/_matrix/client/v3/rooms/!channel-general:api.weave.test/send/m.room.encrypted/txn-encrypted")
                        .with(workspaceJwt())
                        .contentType("application/json")
                        .content("""
                                {
                                  "algorithm":"m.megolm.v1.aes-sha2",
                                  "ciphertext":"opaque-ciphertext",
                                  "sender_key":"curve25519:alice",
                                  "session_id":"megolm-session-1",
                                  "device_id":"WEAVEDEVICEALICE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event_id").value("$msg-encrypted:api.weave.test"))
                .andExpect(content().string(not(containsString("plaintext"))));

        ArgumentCaptor<ChatEventContent> content = ArgumentCaptor.forClass(ChatEventContent.class);
        verify(chatDomainFacadeService).sendEvent(
                eq("channel-general"),
                eq("txn-encrypted"),
                content.capture(),
                any());
        assertThat(content.getValue().body()).isNull();
        assertThat(content.getValue().encryptedEnvelope().content())
                .containsEntry("ciphertext", "opaque-ciphertext")
                .doesNotContainKey("body");
    }

    @Test
    void syncAfterEncryptedSendProjectsCiphertextWithoutPlaintextFields() throws Exception {
        stubConversation();
        when(chatDomainFacadeService.timeline(eq("channel-general"), any(), anyInt()))
                .thenReturn(new ChatTimeline("channel-general", List.of(event(
                        "msg-encrypted",
                        ChatEventContent.encrypted(Map.of(
                                "algorithm", "m.megolm.v1.aes-sha2",
                                "ciphertext", "opaque-ciphertext",
                                "sender_key", "curve25519:alice",
                                "session_id", "megolm-session-1",
                                "device_id", "WEAVEDEVICEALICE"))))));

        mockMvc.perform(get("/_matrix/client/v3/sync").with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rooms.join['!channel-general:api.weave.test'].timeline.events[0].type")
                        .value("m.room.encrypted"))
                .andExpect(jsonPath("$.rooms.join['!channel-general:api.weave.test'].timeline.events[0].content.ciphertext")
                        .value("opaque-ciphertext"))
                .andExpect(jsonPath("$.rooms.join['!channel-general:api.weave.test'].timeline.events[0].content.body")
                        .doesNotExist())
                .andExpect(content().string(not(containsString("providerAccessToken"))));
    }

    @Test
    void keyLifecycleToDeviceSyncAndLostDeviceRevocationAreDeviceScoped() throws Exception {
        stubConversation();
        String userId = "@user_example.com:api.weave.test";
        String trustedDevice = "WEAVETRUSTEDDEVICE";
        String secondDevice = "WEAVESECONDDEVICE";

        uploadDeviceKeys(trustedDevice, "trusted-key", null);
        uploadDeviceKeys(secondDevice, "second-key", "one-time-key-value");

        mockMvc.perform(post("/_matrix/client/v3/keys/query")
                        .header(MatrixFacadeClientStateService.DEVICE_ID_HEADER, trustedDevice)
                        .with(workspaceJwt("trusted-session"))
                        .contentType("application/json")
                        .content("""
                                {"device_keys":{"%s":[]}}
                                """.formatted(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.device_keys['%s'].%s.keys['ed25519:%s']"
                                .formatted(userId, secondDevice, secondDevice))
                        .value("second-key"));

        mockMvc.perform(post("/_matrix/client/v3/keys/claim")
                        .header(MatrixFacadeClientStateService.DEVICE_ID_HEADER, trustedDevice)
                        .with(workspaceJwt("trusted-session"))
                        .contentType("application/json")
                        .content("""
                                {"one_time_keys":{"%s":{"%s":"signed_curve25519"}}}
                                """.formatted(userId, secondDevice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.one_time_keys['%s'].%s['signed_curve25519:AAAA'].key"
                                .formatted(userId, secondDevice))
                        .value("one-time-key-value"));

        mockMvc.perform(put("/_matrix/client/v3/sendToDevice/m.room_key_request/txn-device-1")
                        .header(MatrixFacadeClientStateService.DEVICE_ID_HEADER, trustedDevice)
                        .with(workspaceJwt("trusted-session"))
                        .contentType("application/json")
                        .content("""
                                {"messages":{"%s":{"%s":{"request_id":"request-1"}}}}
                                """.formatted(userId, secondDevice)))
                .andExpect(status().isOk());

        String firstSync = mockMvc.perform(get("/_matrix/client/v3/sync")
                        .header(MatrixFacadeClientStateService.DEVICE_ID_HEADER, secondDevice)
                        .with(workspaceJwt("second-session")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.to_device.events[0].type").value("m.room_key_request"))
                .andExpect(jsonPath("$.to_device.events[0].content.request_id").value("request-1"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String nextBatch = objectMapper.readTree(firstSync).path("next_batch").asString();

        mockMvc.perform(get("/_matrix/client/v3/sync")
                        .queryParam("since", nextBatch)
                        .header(MatrixFacadeClientStateService.DEVICE_ID_HEADER, secondDevice)
                        .with(workspaceJwt("second-session")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.to_device.events").isEmpty());

        mockMvc.perform(delete("/_matrix/client/v3/devices/{deviceId}", secondDevice)
                        .header(MatrixFacadeClientStateService.DEVICE_ID_HEADER, trustedDevice)
                        .with(workspaceJwt("trusted-session"))
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk());

        // MATRIX_E2EE_LOST_DEVICE_REVOKED
        mockMvc.perform(get("/_matrix/client/v3/sync")
                        .header(MatrixFacadeClientStateService.DEVICE_ID_HEADER, secondDevice)
                        .with(workspaceJwt("second-session")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errcode").value("M_UNKNOWN_TOKEN"));
    }

    @Test
    void signatureUploadPreservesDeviceSelfSignatureAndIdentityKeys() throws Exception {
        String userId = "@user_example.com:api.weave.test";
        String deviceId = "WEAVESIGNEDDEVICE";
        String sessionId = "signed-device-session";

        mockMvc.perform(post("/_matrix/client/v3/keys/upload")
                        .header(MatrixFacadeClientStateService.DEVICE_ID_HEADER, deviceId)
                        .with(workspaceJwt(sessionId))
                        .contentType("application/json")
                        .content("""
                                {
                                  "device_keys":{
                                    "user_id":"%s",
                                    "device_id":"%s",
                                    "algorithms":[
                                      "m.olm.v1.curve25519-aes-sha2",
                                      "m.megolm.v1.aes-sha2"
                                    ],
                                    "keys":{
                                      "curve25519:%s":"curve25519-public-key",
                                      "ed25519:%s":"ed25519-public-key"
                                    },
                                    "signatures":{
                                      "%s":{"ed25519:%s":"device-self-signature"}
                                    }
                                  }
                                }
                                """.formatted(userId, deviceId, deviceId, deviceId, userId, deviceId)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/_matrix/client/v3/keys/signatures/upload")
                        .header(MatrixFacadeClientStateService.DEVICE_ID_HEADER, deviceId)
                        .with(workspaceJwt(sessionId))
                        .contentType("application/json")
                        .content("""
                                {
                                  "%s":{
                                    "%s":{
                                      "user_id":"%s",
                                      "device_id":"%s",
                                      "algorithms":[
                                        "m.olm.v1.curve25519-aes-sha2",
                                        "m.megolm.v1.aes-sha2"
                                      ],
                                      "keys":{
                                        "curve25519:%s":"curve25519-public-key",
                                        "ed25519:%s":"ed25519-public-key"
                                      },
                                      "signatures":{
                                        "%s":{"ed25519:self-signing-public-key":"cross-signing-signature"}
                                      }
                                    }
                                  }
                                }
                                """.formatted(
                                        userId,
                                        deviceId,
                                        userId,
                                        deviceId,
                                        deviceId,
                                        deviceId,
                                        userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failures").isEmpty());

        mockMvc.perform(post("/_matrix/client/v3/keys/query")
                        .header(MatrixFacadeClientStateService.DEVICE_ID_HEADER, deviceId)
                        .with(workspaceJwt(sessionId))
                        .contentType("application/json")
                        .content("""
                                {"device_keys":{"%s":[]}}
                                """.formatted(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.device_keys['%s'].%s.keys['curve25519:%s']"
                                .formatted(userId, deviceId, deviceId))
                        .value("curve25519-public-key"))
                .andExpect(jsonPath("$.device_keys['%s'].%s.keys['ed25519:%s']"
                                .formatted(userId, deviceId, deviceId))
                        .value("ed25519-public-key"))
                .andExpect(jsonPath("$.device_keys['%s'].%s.signatures['%s']['ed25519:%s']"
                                .formatted(userId, deviceId, userId, deviceId))
                        .value("device-self-signature"))
                .andExpect(jsonPath("$.device_keys['%s'].%s.signatures['%s']['ed25519:self-signing-public-key']"
                                .formatted(userId, deviceId, userId))
                        .value("cross-signing-signature"));
    }

    @Test
    void fallbackKeyBootstrapsOlmWhenOneTimeKeyPoolIsEmpty() throws Exception {
        stubConversation();
        String userId = "@user_example.com:api.weave.test";
        String targetDevice = "WEAVEFALLBACKDEVICE";
        String claimantDevice = "WEAVEFALLBACKCLAIMANT";

        mockMvc.perform(post("/_matrix/client/v3/keys/upload")
                        .header(MatrixFacadeClientStateService.DEVICE_ID_HEADER, targetDevice)
                        .with(workspaceJwt("fallback-target-session"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "device_keys":{
                                    "user_id":"%s",
                                    "device_id":"%s",
                                    "algorithms":["m.olm.v1.curve25519-aes-sha2"],
                                    "keys":{"ed25519:%s":"fallback-signing-key"},
                                    "signatures":{}
                                  },
                                  "fallback_keys":{
                                    "signed_curve25519:FALLBACK":{
                                      "key":"fallback-public-key",
                                      "fallback":true,
                                      "signatures":{}
                                    }
                                  }
                                }
                                """.formatted(userId, targetDevice, targetDevice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.one_time_key_counts").isEmpty());

        mockMvc.perform(get("/_matrix/client/v3/sync")
                        .header(MatrixFacadeClientStateService.DEVICE_ID_HEADER, targetDevice)
                        .with(workspaceJwt("fallback-target-session")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.device_unused_fallback_key_types[0]")
                        .value("signed_curve25519"));

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/_matrix/client/v3/keys/claim")
                            .header(MatrixFacadeClientStateService.DEVICE_ID_HEADER, claimantDevice)
                            .with(workspaceJwt("fallback-claimant-session"))
                            .contentType("application/json")
                            .content("""
                                    {"one_time_keys":{"%s":{"%s":"signed_curve25519"}}}
                                    """.formatted(userId, targetDevice)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.one_time_keys['%s'].%s['signed_curve25519:FALLBACK'].key"
                                    .formatted(userId, targetDevice))
                            .value("fallback-public-key"));
        }

        mockMvc.perform(get("/_matrix/client/v3/sync")
                        .header(MatrixFacadeClientStateService.DEVICE_ID_HEADER, targetDevice)
                        .with(workspaceJwt("fallback-target-session")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.device_unused_fallback_key_types").isEmpty());
    }

    @Test
    void roomKeyBackupStoresOnlyOpaqueRecoveryPayloads() throws Exception {
        String deviceId = "WEAVEBACKUPDEVICE";
        String created = mockMvc.perform(post("/_matrix/client/v3/room_keys/version")
                        .header(MatrixFacadeClientStateService.DEVICE_ID_HEADER, deviceId)
                        .with(workspaceJwt("backup-session"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "algorithm":"m.megolm_backup.v1.curve25519-aes-sha2",
                                  "auth_data":{"public_key":"curve25519-public","signatures":{}}
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String version = objectMapper.readTree(created).path("version").asString();

        mockMvc.perform(put("/_matrix/client/v3/room_keys/keys/{roomId}/{sessionId}",
                        "!channel-general:api.weave.test", "session-1")
                        .queryParam("version", version)
                        .header(MatrixFacadeClientStateService.DEVICE_ID_HEADER, deviceId)
                        .with(workspaceJwt("backup-session"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "first_message_index":0,
                                  "forwarded_count":0,
                                  "is_verified":true,
                                  "session_data":{"ephemeral":"public","mac":"opaque-mac","ciphertext":"opaque-backup"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));

        mockMvc.perform(get("/_matrix/client/v3/room_keys/keys/{roomId}/{sessionId}",
                        "!channel-general:api.weave.test", "session-1")
                        .queryParam("version", version)
                        .header(MatrixFacadeClientStateService.DEVICE_ID_HEADER, deviceId)
                        .with(workspaceJwt("backup-session")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session_data.ciphertext").value("opaque-backup"))
                .andExpect(content().string(not(containsString("recoveryKey"))))
                .andExpect(content().string(not(containsString("plaintext"))));

        mockMvc.perform(get("/_matrix/client/v3/room_keys/version/{version}", version)
                        .header(MatrixFacadeClientStateService.DEVICE_ID_HEADER, deviceId)
                        .with(workspaceJwt("backup-session")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.algorithm").value("m.megolm_backup.v1.curve25519-aes-sha2"));

        mockMvc.perform(delete("/_matrix/client/v3/room_keys/version/{version}", version)
                        .header(MatrixFacadeClientStateService.DEVICE_ID_HEADER, deviceId)
                        .with(workspaceJwt("backup-session")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/_matrix/client/v3/room_keys/version/{version}", version)
                        .header(MatrixFacadeClientStateService.DEVICE_ID_HEADER, deviceId)
                        .with(workspaceJwt("backup-session")))
                .andExpect(status().isNotFound());
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
                .andExpect(jsonPath("$.chunk[0].content.body").value("Hello from Weave Chat"))
                .andExpect(jsonPath("$.chunk[0].room_id").value("!channel-general:api.weave.test"))
                .andExpect(jsonPath("$.chunk[0].unsigned").isMap())
                .andExpect(content().string(not(containsString("providerAccessToken"))));
    }

    @Test
    void encryptedSendMemberPreflightUsesCanonicalRumaEvents() throws Exception {
        stubConversation();

        mockMvc.perform(get("/_matrix/client/v3/rooms/!channel-general:api.weave.test/members")
                        .with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chunk[0].type").value("m.room.member"))
                .andExpect(jsonPath("$.chunk[0].state_key").value("@alice:api.weave.test"))
                .andExpect(jsonPath("$.chunk[0].room_id").value("!channel-general:api.weave.test"))
                .andExpect(jsonPath("$.chunk[0].content.membership").value("join"))
                .andExpect(jsonPath("$.chunk[0].unsigned").isMap())
                .andExpect(content().string(not(containsString("providerAccessToken"))));
    }

    @Test
    void degradedRoomMemberPreflightExposesOnlyStableQuarantineReason() throws Exception {
        when(chatDomainFacadeService.conversation(eq("channel-general"), any()))
                .thenThrow(new ChatProviderUnavailableException(
                        "chat-conversation-mapping-degraded-provider-redaction-echo-mismatch"));

        mockMvc.perform(get("/_matrix/client/v3/rooms/!channel-general:api.weave.test/members")
                        .with(workspaceJwt()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errcode")
                        .value("M_WEAVE_CHAT_DEGRADED_PROVIDER_REDACTION_ECHO_MISMATCH"))
                .andExpect(content().string(not(containsString("chat-conversation-mapping"))))
                .andExpect(content().string(not(containsString("Synapse"))));
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
        when(chatDomainFacadeService.createConversation(
                any(),
                eq("General"),
                eq("channel"),
                eq(List.of()),
                eq(ChatEncryptionState.unencrypted()),
                any()))
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
    void encryptedRoomCreationCarriesEncryptionAsAnInitialInvariant() throws Exception {
        when(chatDomainFacadeService.createConversation(
                any(),
                eq("Encrypted"),
                eq("channel"),
                eq(List.of()),
                eq(ChatEncryptionState.matrixMegolm()),
                any()))
                .thenReturn(encryptedConversation());

        mockMvc.perform(post("/_matrix/client/v3/createRoom")
                        .with(workspaceJwt())
                        .contentType("application/json")
                        .content("""
                                {
                                  "name":"Encrypted",
                                  "initial_state":[{
                                    "type":"m.room.encryption",
                                    "state_key":"",
                                    "content":{"algorithm":"m.megolm.v1.aes-sha2"}
                                  }]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.room_id").value("!channel-general:api.weave.test"));

        verify(chatDomainFacadeService).createConversation(
                any(),
                eq("Encrypted"),
                eq("channel"),
                eq(List.of()),
                eq(ChatEncryptionState.matrixMegolm()),
                any());
        verify(chatDomainFacadeService, never()).enableEncryption(any(), any(), any());
    }

    @Test
    void logoutRevokesThePresentedMatrixToken() throws Exception {
        // MATRIX_TOKEN_REVOCATION_FACADE
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
        return conversations(ChatEncryptionState.unencrypted());
    }

    private ChatConversations conversations(ChatEncryptionState encryptionState) {
        return new ChatConversations(readiness(), List.of(new ChatConversation(
                "channel-general",
                "General",
                "channel",
                ChatMemberState.READY,
                "Chat is available.",
                Instant.parse("2026-07-08T10:00:00Z"),
                encryptionState,
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

    private ChatConversation encryptedConversation() {
        ChatConversation conversation = conversations().conversations().getFirst();
        return new ChatConversation(
                conversation.conversationId(),
                conversation.title(),
                conversation.kind(),
                conversation.state(),
                conversation.memberImpact(),
                conversation.updatedAt(),
                ChatEncryptionState.matrixMegolm(),
                conversation.historyPolicy(),
                conversation.memberships(),
                conversation.recentAttachments());
    }

    private void uploadDeviceKeys(String deviceId, String signingKey, String oneTimeKey) throws Exception {
        String userId = "@user_example.com:api.weave.test";
        String oneTimeKeys = oneTimeKey == null
                ? "{}"
                : """
                  {"signed_curve25519:AAAA":{"key":"%s","signatures":{}}}
                  """.formatted(oneTimeKey).trim();
        mockMvc.perform(post("/_matrix/client/v3/keys/upload")
                        .header(MatrixFacadeClientStateService.DEVICE_ID_HEADER, deviceId)
                        .with(workspaceJwt(deviceId + "-session"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "device_keys":{
                                    "user_id":"%s",
                                    "device_id":"%s",
                                    "algorithms":["m.megolm.v1.aes-sha2"],
                                    "keys":{"ed25519:%s":"%s"},
                                    "signatures":{}
                                  },
                                  "one_time_keys":%s
                                }
                                """.formatted(userId, deviceId, deviceId, signingKey, oneTimeKeys)))
                .andExpect(status().isOk());
    }

    private ChatHistoryPolicy historyPolicy() {
        return new ChatHistoryPolicy("conversation_members", "organization_default_retention", false, true, List.of());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor workspaceJwt() {
        return workspaceJwt(UUID.randomUUID().toString());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor workspaceJwt(String tokenValue) {
        return workspaceJwtWithSubject(tokenValue, "user@example.com", true);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor workspaceJwtWithoutIssuer() {
        return workspaceJwtWithSubject("missing-issuer-token", "user@example.com", false);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor workspaceJwtWithSubject(String subject) {
        return workspaceJwtWithSubject("invalid-subject-token", subject, true);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor workspaceJwtWithSubject(
            String tokenValue,
            String subject,
            boolean includeIssuer) {
        return jwt().jwt(jwt -> jwt
                        .tokenValue(tokenValue)
                        .subject(subject)
                        .claim("jti", tokenValue)
                        .claim("sid", "weave-test-session-" + tokenValue)
                        .claim("aud", List.of("weave-app"))
                        .claim("organization", HumanJwtTestSupport.organizationWithRole("member"))
                        .claims(claims -> {
                            if (includeIssuer) {
                                claims.put("iss", "https://auth.example.invalid/realms/acme");
                            } else {
                                claims.remove("iss");
                            }
                        }))
                .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"));
    }
}

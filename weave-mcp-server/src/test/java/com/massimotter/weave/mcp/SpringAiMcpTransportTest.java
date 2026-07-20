package com.massimotter.weave.mcp;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.massimotter.weave.contract.mcp.MemberMcpDomainDefinition;
import com.massimotter.weave.contract.mcp.MemberMcpToolCatalog;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.BridgeDiscoveryResponse;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.BridgeInvocationResponse;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.RuntimeInvocationContext;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.ToolInvocationStatus;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.WeaveMcpContentBlock;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.WeaveMcpRef;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.WeaveMcpToolCatalog;
import java.util.List;
import java.util.Map;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.weave.test/realms/weave",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://auth.weave.test/realms/weave/protocol/openid-connect/certs",
        "weave.oidc.token-uri=https://auth.weave.test/realms/weave/protocol/openid-connect/token",
        "weave.oidc.resource=https://api.weave.test/mcp",
        "weave.oidc.mcp-client-secret-file=src/test/resources/weave-mcp-client-secret.test"
})
@AutoConfigureMockMvc
class SpringAiMcpTransportTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private WeaveServerClient client;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void validOidcToken() {
        when(jwtDecoder.decode(org.mockito.ArgumentMatchers.anyString())).thenAnswer(invocation -> token(invocation.getArgument(0)));
    }

    private Jwt token(String tokenValue) {
        Jwt.Builder builder = Jwt.withTokenValue(tokenValue)
                .header("alg", "none")
                .subject("member@example.invalid")
                .audience(List.of("weave-mcp-server", "https://api.weave.test/mcp"))
                .claim("azp", "weave-app")
                .claim("scope", "weave:mcp")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        switch (tokenValue) {
            case "wrong-audience" -> builder.audience(List.of("weave-backend"));
            case "wrong-azp" -> builder.claim("azp", "other-client");
            case "missing-scope" -> builder.claim("scope", "openid");
            case "overbroad-scope" -> builder.claim("scope", "weave:mcp weave:mcp-backend");
            case "service-account" -> builder.subject("2f802c16-24a5-471c-9312-7f5ace77dd04")
                    .claim("preferred_username", "service-account-weave-mcp-server")
                    .claim("azp", "weave-mcp-server");
            default -> { }
        }
        return builder.build();
    }

    @Test
    void oidcIsTheGatekeeperForSpringAiMcpTransport() throws Exception {
        mvc.perform(post("/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                        .content(initializeRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                        HttpHeaders.WWW_AUTHENTICATE,
                        "Bearer resource_metadata=\"https://api.weave.test/.well-known/oauth-protected-resource/mcp\""));
    }

    @Test
    void publishesProtectedResourceMetadataForTheExactMcpResource() throws Exception {
        mvc.perform(get(McpOAuthResourceMetadata.METADATA_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resource").value("https://api.weave.test/mcp"))
                .andExpect(jsonPath("$.authorization_servers[0]").value("https://auth.weave.test/realms/weave"))
                .andExpect(jsonPath("$.bearer_methods_supported[0]").value("header"))
                .andExpect(jsonPath("$.scopes_supported[0]").value("weave:mcp"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "wrong-audience", "wrong-azp", "missing-scope", "overbroad-scope", "service-account"
    })
    void rejectsTokensThatDoNotRepresentAnAudienceBoundMemberRuntime(String tokenValue) throws Exception {
        mvc.perform(post("/mcp")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenValue)
                        .header("X-Weave-Runtime-Profile", "sha256:test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                        .content(initializeRequest()))
                .andExpect(status().isForbidden());
    }

    @Test
    void springAiInitializesAndAdvertisesOnlyCanonicalDomainToolNames() throws Exception {
        MvcResult initialized = mvc.perform(authorizedPost(initializeRequest()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("weave-domain-tools")))
                .andExpect(content().string(containsString("protocolVersion")))
                .andReturn();
        String sessionId = initialized.getResponse().getHeader("Mcp-Session-Id");
        org.assertj.core.api.Assertions.assertThat(sessionId).isNotBlank();

        mvc.perform(authorizedPost("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}")
                        .header("Mcp-Session-Id", sessionId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("files.search")))
                .andExpect(content().string(containsString("calendar.search_events")))
                .andExpect(content().string(containsString("chat.send_message")))
                .andExpect(content().string(not(containsString("nextcloud."))))
                .andExpect(content().string(not(containsString("synapse."))))
                .andExpect(content().string(not(containsString("slack."))))
                .andExpect(content().string(not(containsString("teams."))));
    }

    @Test
    void approvedToolsResourceIsResolvedThroughTheOidcBoundBackendProfile() throws Exception {
        when(client.discover(eq("sha256:test"), any(RuntimeHeaders.class))).thenReturn(discovery());
        String sessionId = initializeSession();

        mvc.perform(authorizedPost("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"resources/read\",\"params\":{\"uri\":\"weave://runtime/approved-tools\"}}")
                        .header("Mcp-Session-Id", sessionId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("files.search")))
                .andExpect(content().string(containsString("supportSafe")))
                .andExpect(content().string(not(containsString("credentialref://"))))
                .andExpect(content().string(not(containsString("Bearer runtime-token"))));
    }

    @Test
    void statefulRequestsRejectUnknownSessions() throws Exception {
        mvc.perform(authorizedPost("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/list\",\"params\":{}}")
                        .header("Mcp-Session-Id", "unknown-session"))
                .andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authorizedPost(String body) {
        return post("/mcp")
                .header(HttpHeaders.AUTHORIZATION, "Bearer runtime-token")
                .header("X-Weave-Runtime-Profile", "sha256:test")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .content(body);
    }

    private String initializeRequest() {
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{" +
                "\"protocolVersion\":\"2025-06-18\",\"capabilities\":{\"elicitation\":{\"form\":{}}}," +
                "\"clientInfo\":{\"name\":\"weave-test\",\"version\":\"1.0\"}}}";
    }

    private String initializeSession() throws Exception {
        return mvc.perform(authorizedPost(initializeRequest()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getHeader("Mcp-Session-Id");
    }

    private BridgeDiscoveryResponse discovery() {
        RuntimeInvocationContext runtime = new RuntimeInvocationContext(
                new WeaveMcpRef("org:workspace"),
                new WeaveMcpRef("user:member"),
                new WeaveMcpRef("weave-runtime-profile://sha256:test"),
                "sha256:test",
                new WeaveMcpRef("credentialref://weave/runtime/short-lived/test"),
                "audit://mcp/discovery/test",
                List.of("files.read"),
                List.of("files.search"));
        return new BridgeDiscoveryResponse(
                runtime,
                new WeaveMcpToolCatalog(
                        MemberMcpToolCatalog.SERVER_NAMESPACE,
                        MemberMcpDomainDefinition.CONTRACT_VERSION,
                        List.of(MemberMcpToolCatalog.byName().get("files.search").asBridgeDefinition())));
    }
}

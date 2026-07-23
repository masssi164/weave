package com.massimotter.weave.mcp;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.weave.test/realms/weave",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://auth.weave.test/realms/weave/protocol/openid-connect/certs",
        "weave.mcp.exchange-client-key-file=/tmp/weave-mcp-test-private-jwk.json"
})
@AutoConfigureMockMvc
class SpringAiMcpTransportTest {
    // V01_MCP_WORKLOAD_BOUNDARY
    private static final String RESOURCE = "https://api.weave.test/mcp";
    private static final String EDGE = "weave-mcp-server";
    private static final String CELL = "weaver-cell-test";
    private static final String SUBJECT = "service-account-cell-subject";
    private static final Set<String> DOMAIN_SCOPES = Set.of("calendar.read");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private McpBackendTokenExchange exchange;

    @MockitoBean
    private McpBackendContextResolver contexts;

    private ExchangedAccessToken exchanged;

    @BeforeEach
    void authorizeBoundWorkload() {
        when(jwtDecoder.decode(anyString())).thenAnswer(invocation -> token(invocation.getArgument(0)));
        Instant now = Instant.now();
        exchanged = new ExchangedAccessToken(
                "backend-token",
                SUBJECT,
                EDGE,
                Set.of("https://api.weave.test/api"),
                DOMAIN_SCOPES,
                now,
                now.plusSeconds(30));
        when(exchange.exchange(any(), anyString(), eq(DOMAIN_SCOPES))).thenReturn(exchanged);
        when(contexts.resolve(any(), eq(exchanged))).thenReturn(new McpBackendContext(
                "mcp-authz:" + "a".repeat(64),
                "org:test",
                "cell:test",
                CELL,
                "sha256:" + "b".repeat(64),
                "rp_test",
                "sha256:" + "c".repeat(64),
                "sha256:" + "d".repeat(64),
                now.plusSeconds(30),
                DOMAIN_SCOPES,
                DOMAIN_SCOPES));
    }

    @Test
    void publishesProtectedResourceMetadataWithoutAuthentication() throws Exception {
        mvc.perform(get(McpSecurityConfiguration.PROTECTED_RESOURCE_METADATA_PATH))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.resource", is(RESOURCE)))
                .andExpect(jsonPath("$.authorization_servers[0]", is("https://auth.weave.test/realms/weave")))
                .andExpect(jsonPath("$.scopes_supported[0]", is("mcp.tools")))
                .andExpect(jsonPath("$.scopes_supported[1]", is("calendar.read")));
    }

    @Test
    void missingBearerReceivesDiscoverableRfc9728Challenge() throws Exception {
        mvc.perform(mcpInitialize(null, true))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                        HttpHeaders.WWW_AUTHENTICATE,
                        containsString("resource_metadata=\"https://api.weave.test/.well-known/oauth-protected-resource/mcp\"")));
        verify(exchange, never()).exchange(any(), anyString(), any());
    }

    @Test
    void humanBearerCannotDiscoverTheMcpCatalog() throws Exception {
        mvc.perform(mcpInitialize("human", true))
                .andExpect(status().isForbidden());
        verify(exchange, never()).exchange(any(), anyString(), any());
    }

    @Test
    void workloadBearerWithWrongOrAdditionalAudienceCannotDiscoverTheCatalog() throws Exception {
        mvc.perform(mcpInitialize("wrong-audience", true))
                .andExpect(status().isForbidden());
        mvc.perform(mcpInitialize("multiple-audiences", true))
                .andExpect(status().isForbidden());
        verify(exchange, never()).exchange(any(), anyString(), any());
    }

    @Test
    void insufficientToolScopesReceiveAnOAuthScopeChallenge() throws Exception {
        mvc.perform(mcpInitialize("insufficient", true))
                .andExpect(status().isForbidden())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("error=\"insufficient_scope\"")))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("scope=\"mcp.tools calendar.read\"")));
        verify(exchange, never()).exchange(any(), anyString(), any());
    }

    @Test
    void extensionNegotiationIsMandatoryForWorkloadClientCredentials() throws Exception {
        mvc.perform(mcpInitialize("valid", false))
                .andExpect(status().isBadRequest());
        verify(exchange, never()).exchange(any(), anyString(), any());
    }

    @Test
    void boundCellIsExchangedAndDispatchedThroughTheFrameworkTransport() throws Exception {
        mvc.perform(mcpInitialize("valid", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$['result']['capabilities']['extensions']['io.modelcontextprotocol/oauth-client-credentials']",
                        is(Map.of())));

        verify(exchange).exchange(any(McpCellWorkloadPrincipal.class), eq("valid"), eq(DOMAIN_SCOPES));
        verify(contexts).resolve(any(McpCellWorkloadPrincipal.class), eq(exchanged));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder mcpInitialize(
            String bearer,
            boolean extension) {
        var request = post("/mcp")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .content(initializeRequest(extension));
        return bearer == null
                ? request
                : request.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
    }

    private Jwt token(String tokenValue) {
        Instant now = Instant.now().minusSeconds(1);
        boolean human = "human".equals(tokenValue);
        boolean insufficient = "insufficient".equals(tokenValue);
        boolean wrongAudience = "wrong-audience".equals(tokenValue);
        boolean multipleAudiences = "multiple-audiences".equals(tokenValue);
        String clientId = human ? "weave-app" : CELL;
        String scope = insufficient ? "mcp.tools" : "mcp.tools calendar.read";
        List<String> audience = human || wrongAudience
                ? List.of("https://api.weave.test/api")
                : multipleAudiences ? List.of(RESOURCE, EDGE) : List.of(RESOURCE);
        return Jwt.withTokenValue(tokenValue)
                .header("alg", "RS256")
                .header("typ", "at+jwt")
                .issuer("https://auth.weave.test/realms/weave")
                .subject(human ? "member-subject" : SUBJECT)
                .audience(audience)
                .claim("client_id", clientId)
                .claim("azp", clientId)
                .claim("scope", scope)
                .claim("realm_access", human
                        ? Map.of("roles", List.of("member"))
                        : Map.of("roles", List.of("weaver-runtime")))
                .claim("resource_access", Map.of())
                .jti("jti-" + tokenValue)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(45))
                .build();
    }

    private String initializeRequest(boolean extension) {
        String extensions = extension
                ? "\"extensions\":{\"io.modelcontextprotocol/oauth-client-credentials\":{}}"
                : "\"extensions\":{}";
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{"
                + "\"protocolVersion\":\"2025-11-25\",\"capabilities\":{" + extensions + "},"
                + "\"clientInfo\":{\"name\":\"weave-cell-test\",\"version\":\"1.0\"}}}";
    }
}

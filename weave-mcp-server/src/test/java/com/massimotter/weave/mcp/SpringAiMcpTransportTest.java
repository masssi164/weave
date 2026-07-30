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

@SpringBootTest(
    properties = {
      "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.weave.test/realms/weave",
      "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://auth.weave.test/realms/weave/protocol/openid-connect/certs",
      "weave.mcp.exchange-client-jwk-file=/tmp/weave-mcp-test-private.jwk"
    })
@AutoConfigureMockMvc
class SpringAiMcpTransportTest {
  // V01_MCP_WORKLOAD_BOUNDARY
  private static final String RESOURCE = "https://api.weave.test/mcp";
  private static final String EDGE = "weave-mcp-server";
  private static final String CELL = "weaver-cell-test";
  private static final String SUBJECT = "service-account-cell-subject";
  private static final Set<String> DOMAIN_SCOPES = Set.of("files.read");

  @Autowired private MockMvc mvc;

  @MockitoBean private JwtDecoder jwtDecoder;

  @MockitoBean private McpBackendTokenExchange exchange;

  private ExchangedAccessToken exchanged;

  @BeforeEach
  void authorizeBoundWorkload() {
    when(jwtDecoder.decode(anyString())).thenAnswer(invocation -> token(invocation.getArgument(0)));
    Instant now = Instant.now();
    exchanged =
        new ExchangedAccessToken(
            "backend-token",
            SUBJECT,
            EDGE,
            Set.of("https://api.weave.test/api"),
            DOMAIN_SCOPES,
            now,
            now.plusSeconds(30));
    when(exchange.exchange(any(), anyString(), eq(DOMAIN_SCOPES))).thenReturn(exchanged);
  }

  @Test
  void publishesProtectedResourceMetadataWithoutAuthentication() throws Exception {
    mvc.perform(get(McpSecurityConfiguration.PROTECTED_RESOURCE_METADATA_PATH))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
        .andExpect(jsonPath("$.resource", is(RESOURCE)))
        .andExpect(
            jsonPath("$.authorization_servers[0]", is("https://auth.weave.test/realms/weave")))
        .andExpect(jsonPath("$.scopes_supported[0]", is("mcp.tools")))
        .andExpect(jsonPath("$.scopes_supported[1]", is("files.read")));
  }

  @Test
  void missingBearerReceivesDiscoverableRfc9728Challenge() throws Exception {
    mvc.perform(mcpInitialize(null, true))
        .andExpect(status().isUnauthorized())
        .andExpect(
            header()
                .string(
                    HttpHeaders.WWW_AUTHENTICATE,
                    containsString(
                        "resource_metadata=\"https://api.weave.test/.well-known/oauth-protected-resource/mcp\"")));
    verify(exchange, never()).exchange(any(), anyString(), any());
  }

  @Test
  void humanBearerCannotDiscoverTheMcpCatalog() throws Exception {
    mvc.perform(mcpInitialize("human", true)).andExpect(status().isForbidden());
    verify(exchange, never()).exchange(any(), anyString(), any());
  }

  @Test
  void wrongAudienceWorkloadCannotDiscoverTheMcpCatalog() throws Exception {
    mvc.perform(mcpInitialize("wrong-audience", true)).andExpect(status().isForbidden());
    verify(exchange, never()).exchange(any(), eq("wrong-audience"), any());
  }

  @Test
  void additionalAudienceCannotBroadenTheMcpEdgeToken() throws Exception {
    mvc.perform(mcpInitialize("extra-audience", true)).andExpect(status().isForbidden());
    verify(exchange, never()).exchange(any(), eq("extra-audience"), any());
  }

  @Test
  void insufficientToolScopesReceiveAnOAuthScopeChallenge() throws Exception {
    mvc.perform(mcpInitialize("insufficient", true))
        .andExpect(status().isForbidden())
        .andExpect(
            header()
                .string(
                    HttpHeaders.WWW_AUTHENTICATE, containsString("error=\"insufficient_scope\"")))
        .andExpect(
            header()
                .string(
                    HttpHeaders.WWW_AUTHENTICATE,
                    containsString("scope=\"mcp.tools files.read\"")));
    verify(exchange, never()).exchange(any(), anyString(), any());
  }

  @Test
  void extensionNegotiationIsMandatoryForWorkloadClientCredentials() throws Exception {
    mvc.perform(mcpInitialize("valid", false)).andExpect(status().isBadRequest());
    verify(exchange, never()).exchange(any(), anyString(), any());
  }

  @Test
  void boundCellIsExchangedAndDispatchedThroughTheFrameworkTransport() throws Exception {
    mvc.perform(mcpInitialize("valid", true))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$['result']['capabilities']['extensions']['io.modelcontextprotocol/oauth-client-credentials']",
                is(Map.of())));

    verify(exchange).exchange(any(McpCellWorkloadPrincipal.class), eq("valid"), eq(DOMAIN_SCOPES));
  }

  @Test
  void discoversTheCuratedFilesToolAndCanonicalResourceTemplate() throws Exception {
    var initialized =
        mvc.perform(mcpInitialize("valid", true)).andExpect(status().isOk()).andReturn();
    String sessionId = initialized.getResponse().getHeader("Mcp-Session-Id");

    mvc.perform(
            post("/mcp")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid")
                .header("Mcp-Session-Id", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .content(
                    """
                                    {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
                    """))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(containsString("\"name\":\"files.search\"")))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(containsString("\"readOnlyHint\":true")))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(containsString("\"destructiveHint\":false")));

    mvc.perform(
            post("/mcp")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid")
                .header("Mcp-Session-Id", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .content(
                    """
                                    {"jsonrpc":"2.0","id":3,"method":"resources/templates/list","params":{}}
                    """))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(containsString("\"uriTemplate\":\"weave://files/{canonicalFileRef}\"")));
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder mcpInitialize(
      String bearer, boolean extension) {
    var request =
        post("/mcp")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
            .content(initializeRequest(extension));
    return bearer == null ? request : request.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
  }

  private Jwt token(String tokenValue) {
    Instant now = Instant.now().minusSeconds(1);
    boolean human = "human".equals(tokenValue);
    boolean insufficient = "insufficient".equals(tokenValue);
    boolean wrongAudience = "wrong-audience".equals(tokenValue);
    boolean extraAudience = "extra-audience".equals(tokenValue);
    String clientId = human ? "weave-app" : CELL;
    String scope = insufficient ? "mcp.tools" : "mcp.tools files.read";
    return Jwt.withTokenValue(tokenValue)
        .header("alg", "RS256")
        .header("typ", "at+jwt")
        .issuer("https://auth.weave.test/realms/weave")
        .subject(human ? "member-subject" : SUBJECT)
        .audience(
            human
                ? List.of("https://api.weave.test/api")
                : wrongAudience
                    ? List.of("https://api.weave.test/api")
                    : extraAudience
                        ? List.of(RESOURCE, EDGE, "unexpected-audience")
                        : List.of(RESOURCE, EDGE))
        .claim("client_id", clientId)
        .claim("azp", clientId)
        .claim("scope", scope)
        .claim(
            "realm_access",
            human ? Map.of("roles", List.of("member")) : Map.of("roles", List.of("weaver-runtime")))
        .claim("resource_access", Map.of())
        .jti("jti-" + tokenValue)
        .issuedAt(now)
        .expiresAt(now.plusSeconds(45))
        .build();
  }

  private String initializeRequest(boolean extension) {
    String extensions =
        extension
            ? "\"extensions\":{\"io.modelcontextprotocol/oauth-client-credentials\":{}}"
            : "\"extensions\":{}";
    return "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{"
        + "\"protocolVersion\":\"2025-11-25\",\"capabilities\":{"
        + extensions
        + "},"
        + "\"clientInfo\":{\"name\":\"weave-cell-test\",\"version\":\"1.0\"}}}";
  }
}

package com.massimotter.weave.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.json.JsonMapper;

/** A cell can exchange only its current, admitted domain scopes. */
class McpScopedExchangeTest {
  private static final String RESOURCE = "https://api.weave.test/mcp";
  private static final String BACKEND = "https://api.weave.test/api";

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void exchangesThePresentedDomainSubsetWithoutAddingConfiguredScopes() throws Exception {
    for (String domain : List.of("files.read", "calendar.read")) {
      var properties = properties();
      var exchange = mock(McpBackendTokenExchange.class);
      Instant now = Instant.now();
      var exchanged = new ExchangedAccessToken("backend-token", "cell-subject",
          "weave-mcp-server", Set.of(BACKEND), Set.of(domain), now, now.plusSeconds(30));
      when(exchange.exchange(any(), eq("cell-token"), eq(Set.of(domain)))).thenReturn(exchanged);
      SecurityContextHolder.getContext().setAuthentication(
          new JwtAuthenticationToken(token("mcp.tools " + domain), List.of()));
      var response = new MockHttpServletResponse();
      var forwarded = new AtomicBoolean();

      new McpRequestAdmissionFilter(properties, exchange, JsonMapper.builder().build())
          .doFilter(new MockHttpServletRequest("GET", "/mcp"), response,
              (request, ignored) -> {
                assertThat(request.getAttribute(McpRequestAdmissionFilter.EXCHANGED_TOKEN_ATTRIBUTE))
                    .isSameAs(exchanged);
                forwarded.set(true);
              });

      assertThat(forwarded).isTrue();
      verify(exchange).exchange(any(), eq("cell-token"), eq(Set.of(domain)));
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  void missingDomainUnknownScopeAndRepeatedScopeFailBeforeExchange() throws Exception {
    for (String scope : List.of("mcp.tools", "mcp.tools calendar.write",
        "files.read", "mcp.tools files.read files.read")) {
      var exchange = mock(McpBackendTokenExchange.class);
      SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token(scope), List.of()));
      var response = new MockHttpServletResponse();
      var forwarded = new AtomicBoolean();

      new McpRequestAdmissionFilter(properties(), exchange, JsonMapper.builder().build())
          .doFilter(new MockHttpServletRequest("GET", "/mcp"), response,
              (request, ignored) -> forwarded.set(true));

      assertThat(response.getStatus()).isEqualTo(403);
      assertThat(forwarded).isFalse();
      verify(exchange, never()).exchange(any(), any(), any());
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  void eachToolExchangesOnlyItsOwnDomainEvenWhenTheCellHoldsBoth() throws Exception {
    for (String tool : List.of("files.search", "calendar.agenda")) {
      String expected = tool.startsWith("files.") ? "files.read" : "calendar.read";
      var exchange = mock(McpBackendTokenExchange.class);
      Instant now = Instant.now();
      var exchanged = new ExchangedAccessToken("backend-token", "cell-subject",
          "weave-mcp-server", Set.of(BACKEND), Set.of(expected), now, now.plusSeconds(30));
      when(exchange.exchange(any(), eq("cell-token"), eq(Set.of(expected)))).thenReturn(exchanged);
      SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
          token("mcp.tools files.read calendar.read"), List.of()));
      var request = toolRequest(tool);
      var response = new MockHttpServletResponse();
      var forwarded = new AtomicBoolean();

      new McpRequestAdmissionFilter(properties(), exchange, JsonMapper.builder().build())
          .doFilter(request, response, (effective, ignored) -> {
            assertThat(effective.getAttribute(McpRequestAdmissionFilter.EXCHANGED_TOKEN_ATTRIBUTE))
                .isSameAs(exchanged);
            forwarded.set(true);
          });

      assertThat(forwarded).isTrue();
      verify(exchange).exchange(any(), eq("cell-token"), eq(Set.of(expected)));
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  void calendarOnlyCellCannotInvokeFilesToolBeforeExchange() throws Exception {
    var exchange = mock(McpBackendTokenExchange.class);
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
        token("mcp.tools calendar.read"), List.of()));
    var response = new MockHttpServletResponse();

    new McpRequestAdmissionFilter(properties(), exchange, JsonMapper.builder().build())
        .doFilter(toolRequest("files.search"), response, (request, ignored) -> {});

    assertThat(response.getStatus()).isEqualTo(403);
    verify(exchange, never()).exchange(any(), any(), any());
  }

  @Test
  void rejectsAConfiguredScopeThatIsNotPartOfTheClosedDomainCeiling() {
    assertThatThrownBy(() -> new McpWorkloadProperties(
        URI.create(RESOURCE),
        URI.create("https://api.weave.test/.well-known/oauth-protected-resource/mcp"),
        URI.create("https://auth.weave.test/realms/weave"),
        List.of("mcp.tools", "files.read", "calendar.write"),
        URI.create("https://auth.weave.test/realms/weave/protocol/openid-connect/token"),
        "weave-mcp-server", Path.of("/tmp/weave-mcp-test.jwk"), URI.create(BACKEND),
        URI.create("https://api.weave.test/api"), List.of("files.read"),
        Duration.ofSeconds(10), Duration.ofSeconds(60), 8192))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static McpWorkloadProperties properties() {
    return new McpWorkloadProperties(
        URI.create(RESOURCE),
        URI.create("https://api.weave.test/.well-known/oauth-protected-resource/mcp"),
        URI.create("https://auth.weave.test/realms/weave"),
        List.of("mcp.tools", "files.read", "calendar.read"),
        URI.create("https://auth.weave.test/realms/weave/protocol/openid-connect/token"),
        "weave-mcp-server", Path.of("/tmp/weave-mcp-test.jwk"), URI.create(BACKEND),
        URI.create("https://api.weave.test/api"), List.of("files.read", "calendar.read"),
        Duration.ofSeconds(10), Duration.ofSeconds(60), 8192);
  }

  private static Jwt token(String scope) {
    Instant now = Instant.now();
    return Jwt.withTokenValue("cell-token")
        .header("typ", "at+jwt")
        .issuer("https://auth.weave.test/realms/weave")
        .subject("cell-subject")
        .jti("cell-token-jti")
        .issuedAt(now)
        .expiresAt(now.plusSeconds(30))
        .audience(List.of(RESOURCE, "weave-mcp-server"))
        .claim("client_id", "weaver-cell-test")
        .claim("azp", "weaver-cell-test")
        .claim("scope", scope)
        .claim("realm_access", Map.of("roles", List.of("weaver-runtime")))
        .build();
  }

  private static MockHttpServletRequest toolRequest(String tool) {
    var request = new MockHttpServletRequest("POST", "/mcp");
    request.setContentType(MediaType.APPLICATION_JSON_VALUE);
    request.setContent(("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
        + "\"params\":{\"name\":\"" + tool + "\",\"arguments\":{}}}").getBytes());
    return request;
  }
}

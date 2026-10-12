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

import com.massimotter.weave.userapi.model.CalendarEventWriteRequest;
import com.massimotter.weave.userapi.model.CalendarTimeValue;
import com.massimotter.weave.userapi.model.CalendarUserAgenda;
import com.massimotter.weave.userapi.model.CalendarUserEvent;
import com.massimotter.weave.userapi.model.CalendarUserOccurrence;
import com.massimotter.weave.userapi.model.CalendarUserScope;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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

  @MockitoBean private CalendarUserApiClient calendarClient;

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
    when(exchange.exchange(any(), anyString(), eq(Set.of("calendar.read"))))
        .thenReturn(new ExchangedAccessToken(
            "calendar-backend-token", SUBJECT, EDGE, Set.of("https://api.weave.test/api"),
            Set.of("calendar.read"), now, now.plusSeconds(30)));
    when(exchange.exchange(any(), anyString(), eq(Set.of("calendar.write"))))
        .thenReturn(new ExchangedAccessToken(
            "calendar-write-backend-token", SUBJECT, EDGE, Set.of("https://api.weave.test/api"),
            Set.of("calendar.write"), now, now.plusSeconds(30)));
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
        .andExpect(jsonPath("$.scopes_supported[1]", is("files.read")))
        .andExpect(jsonPath("$.scopes_supported[2]", is("calendar.read")))
        .andExpect(jsonPath("$.scopes_supported[3]", is("calendar.write")));
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
                    containsString("scope=\"mcp.tools files.read calendar.read calendar.write\"")));
    verify(exchange, never()).exchange(any(), anyString(), any());
  }

  @Test
  void validWorkloadBearerInitializesWithoutOptionalExtensionMarker() throws Exception {
    mvc.perform(mcpInitialize("valid", false)).andExpect(status().isOk());
    verify(exchange).exchange(any(McpCellWorkloadPrincipal.class), eq("valid"), eq(DOMAIN_SCOPES));
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

  @Test
  void invokesCuratedCalendarToolThroughTheFrameworkTransport() throws Exception {
    String id = "calendar:" + "a".repeat(64);
    String eventId = "event:" + "b".repeat(64);
    var event = new CalendarUserEvent()
        .id(eventId)
        .calendarId(id)
        .scope(new CalendarUserScope().type(CalendarUserScope.TypeEnum.WORKSPACE)
            .spaceId("workspace-default"))
        .meetingThreadRef("room:meeting")
        .version("v1")
        .allowedActions(List.of("read"))
        .content(new CalendarEventWriteRequest()
            .title("Calendar DATE")
            .start(new CalendarTimeValue().kind(CalendarTimeValue.KindEnum.DATE)
                .date(LocalDate.parse("2026-10-25")))
            .end(new CalendarTimeValue().kind(CalendarTimeValue.KindEnum.DATE)
                .date(LocalDate.parse("2026-10-26")))
            .attendees(List.of())
            .overrides(List.of()));
    when(calendarClient.agenda(eq(id), anyString(), anyString(), eq("Europe/Berlin")))
        .thenReturn(new CalendarUserAgenda()
            .calendarId(id)
            .from(OffsetDateTime.parse("2026-10-23T00:00:00Z"))
            .to(OffsetDateTime.parse("2026-10-29T00:00:00Z"))
            .evaluationTimeZone("Europe/Berlin")
            .events(List.of(event))
            .occurrences(List.of(new CalendarUserOccurrence().eventId(eventId)
                .startsAt(OffsetDateTime.parse("2026-10-24T22:00:00Z"))
                .endsAt(OffsetDateTime.parse("2026-10-25T23:00:00Z"))))
            .previews(List.of())
            .previewOccurrences(List.of()));
    var initialized = mvc.perform(mcpInitialize("calendar", true))
        .andExpect(status().isOk()).andReturn();
    String sessionId = initialized.getResponse().getHeader("Mcp-Session-Id");

    mvc.perform(post("/mcp")
            .header(HttpHeaders.AUTHORIZATION, "Bearer calendar")
            .header("Mcp-Session-Id", sessionId)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
            .content("""
                {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{
                  "name":"calendar.agenda","arguments":{
                    "calendarId":"%s","from":"2026-10-23T00:00:00Z",
                    "to":"2026-10-29T00:00:00Z","evaluationTimeZone":"Europe/Berlin"}}}
                """.formatted(id)))
        .andExpect(status().isOk())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
            .string(containsString(id)))
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
            .string(containsString("Calendar DATE")))
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
            .string(containsString("\"isError\":false")));
    verify(calendarClient).agenda(eq(id), eq("2026-10-23T00:00:00Z"),
        eq("2026-10-29T00:00:00Z"), eq("Europe/Berlin"));
  }

  @Test
  void discoversAndInvokesCuratedCalendarWriteToolsWithGeneratedModels() throws Exception {
    String calendarId = "calendar:" + "a".repeat(64);
    String eventId = "event:" + "b".repeat(64);
    when(calendarClient.eventRequest(any())).thenAnswer(call -> {
      Map<?, ?> event = call.getArgument(0);
      return new CalendarEventWriteRequest().title(event.get("title").toString());
    });
    when(calendarClient.create(eq(calendarId), eq("retry-key"), any(CalendarEventWriteRequest.class)))
        .thenReturn(new CalendarUserEvent()
            .id(eventId).calendarId(calendarId).version("v1")
            .meetingThreadRef("room:meeting")
            .scope(new CalendarUserScope().type(CalendarUserScope.TypeEnum.WORKSPACE)
                .spaceId("workspace-default"))
            .allowedActions(List.of("read", "update", "delete"))
            .content(new CalendarEventWriteRequest().title("MCP planning")
                .start(new CalendarTimeValue().kind(CalendarTimeValue.KindEnum.DATE)
                    .date(LocalDate.parse("2026-10-25")))
                .end(new CalendarTimeValue().kind(CalendarTimeValue.KindEnum.DATE)
                    .date(LocalDate.parse("2026-10-26")))
                .attendees(List.of()).overrides(List.of())));
    var initialized = mvc.perform(mcpInitialize("calendar-write", true))
        .andExpect(status().isOk()).andReturn();
    String sessionId = initialized.getResponse().getHeader("Mcp-Session-Id");

    mvc.perform(post("/mcp")
            .header(HttpHeaders.AUTHORIZATION, "Bearer calendar-write")
            .header("Mcp-Session-Id", sessionId)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
            .content("""
                {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
                """))
        .andExpect(status().isOk())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
            .string(containsString("\"name\":\"calendar.create\"")))
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
            .string(containsString("\"name\":\"calendar.update\"")))
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
            .string(containsString("\"name\":\"calendar.delete\"")));

    mvc.perform(post("/mcp")
            .header(HttpHeaders.AUTHORIZATION, "Bearer calendar-write")
            .header("Mcp-Session-Id", sessionId)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
            .content("""
                {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{
                  "name":"calendar.create","arguments":{
                    "calendarId":"%s","idempotencyKey":"retry-key",
                    "event":{"title":"MCP planning",
                      "start":{"kind":"DATE","date":"2026-10-25"},
                      "end":{"kind":"DATE","date":"2026-10-26"},
                      "attendees":[],"overrides":[]}}}}
                """.formatted(calendarId)))
        .andExpect(status().isOk())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
            .string(containsString(eventId)))
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
            .string(containsString("\"isError\":false")));
    verify(calendarClient).create(eq(calendarId), eq("retry-key"),
        org.mockito.ArgumentMatchers.argThat(event -> "MCP planning".equals(event.getTitle())));
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
    String scope = insufficient ? "mcp.tools"
        : "calendar".equals(tokenValue) ? "mcp.tools calendar.read"
        : "calendar-write".equals(tokenValue) ? "mcp.tools calendar.write"
        : "mcp.tools files.read";
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
            human
                ? Map.of("roles", List.of("member"))
                : Map.of(
                    "roles",
                    List.of(
                        "weaver-runtime",
                        "default-roles-weave",
                        "offline_access",
                        "uma_authorization")))
        .claim(
            "resource_access",
            human
                ? Map.of()
                : Map.of(
                    "account",
                    Map.of(
                        "roles",
                        List.of("manage-account", "manage-account-links", "view-profile"))))
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

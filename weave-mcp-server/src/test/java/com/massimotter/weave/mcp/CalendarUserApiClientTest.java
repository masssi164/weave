package com.massimotter.weave.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Exercises the generated Calendar User HTTP client across the actual transport boundary. */
class CalendarUserApiClientTest {
  private static final String CALENDAR = "calendar:" + "a".repeat(64);
  private HttpServer server;
  private final AtomicInteger agendaReads = new AtomicInteger();
  private final AtomicReference<String> authorization = new AtomicReference<>();

  @BeforeEach
  void setUp() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void usesGeneratedListAndAgendaWithTheExchangedBearer() {
    server.createContext("/api/calendar/calendars", exchange -> {
      authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
      if (exchange.getRequestURI().getPath().equals("/api/calendar/calendars")) {
        reply(exchange, 200, listing(true));
      } else {
        agendaReads.incrementAndGet();
        assertThat(exchange.getRequestMethod()).isEqualTo("GET");
        assertThat(exchange.getRequestURI().getRawQuery()).contains("evaluationTimeZone=Europe%2FBerlin");
        reply(exchange, 200, emptyAgenda());
      }
    });
    server.start();

    var agenda = client().agenda(CALENDAR, "2026-10-07T09:00:00Z", "2026-10-08T09:00:00Z", "Europe/Berlin");

    assertThat(authorization.get()).isEqualTo("Bearer exchanged-only");
    assertThat(agendaReads.get()).isEqualTo(1);
    assertThat(agenda.getCalendarId()).isEqualTo(CALENDAR);
    assertThat(agenda.getEvents()).isEmpty();
  }

  @Test
  void invisibleCalendarNeverFetchesAgenda() {
    server.createContext("/api/calendar/calendars", exchange -> {
      if (exchange.getRequestURI().getPath().equals("/api/calendar/calendars")) {
        reply(exchange, 200, listing(false));
      } else {
        agendaReads.incrementAndGet();
        reply(exchange, 200, emptyAgenda());
      }
    });
    server.start();

    assertThatThrownBy(() -> client().agenda(CALENDAR, "2026-10-07T09:00:00Z",
        "2026-10-08T09:00:00Z", "UTC"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unavailable");
    assertThat(agendaReads.get()).isZero();
  }

  @Test
  void downstreamDenialDoesNotExposeItsPrivateBody() {
    server.createContext("/api/calendar/calendars", exchange ->
        reply(exchange, 403, "{\"access_token\":\"must-not-escape\"}"));
    server.start();

    assertThatThrownBy(() -> client().agenda(CALENDAR, "2026-10-07T09:00:00Z",
        "2026-10-08T09:00:00Z", "UTC"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Calendar User API rejected request: HTTP 403")
        .hasMessageNotContaining("must-not-escape");
  }

  @Test
  void rejectsUnboundedIntervalAndOffsetZoneBeforeHttp() {
    server.start();
    assertThatThrownBy(() -> client().agenda(CALENDAR, "2026-10-07T09:00:00Z",
        "2028-10-08T09:00:00Z", "UTC"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> client().agenda(CALENDAR, "2026-10-07T09:00:00Z",
        "2026-10-08T09:00:00Z", "+01:00"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private CalendarUserApiClient client() {
    McpInvocationCredentials credentials = mock(McpInvocationCredentials.class);
    when(credentials.exchangedBearer()).thenReturn("exchanged-only");
    String base = "http://127.0.0.1:" + server.getAddress().getPort();
    return new CalendarUserApiClient(new McpWorkloadProperties(
        URI.create("https://api.weave.test/mcp"),
        URI.create("https://api.weave.test/.well-known/oauth-protected-resource/mcp"),
        URI.create("https://auth.weave.test/realms/weave"),
        List.of("mcp.tools", "calendar.read"), URI.create(base + "/token"),
        "weave-mcp-server", Path.of("/tmp/not-read.jwk"),
        URI.create("https://api.weave.test/api"), URI.create(base + "/api"),
        List.of("calendar.read"), Duration.ofSeconds(2), Duration.ofSeconds(60), 8192), credentials);
  }

  private static String listing(boolean readable) {
    return "{\"calendars\":[{\"id\":\"" + CALENDAR
        + "\",\"scope\":{\"type\":\"WORKSPACE\",\"spaceId\":\"space:workspace\"},"
        + "\"allowedActions\":[" + (readable ? "\"read\"" : "\"none\"") + "]}]}";
  }

  private static String emptyAgenda() {
    return "{\"calendarId\":\"" + CALENDAR
        + "\",\"from\":\"2026-10-07T09:00:00Z\",\"to\":\"2026-10-08T09:00:00Z\","
        + "\"evaluationTimeZone\":\"Europe/Berlin\",\"events\":[],\"occurrences\":[],"
        + "\"previews\":[],\"previewOccurrences\":[]}";
  }

  private static void reply(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    try (var output = exchange.getResponseBody()) { output.write(bytes); }
  }
}

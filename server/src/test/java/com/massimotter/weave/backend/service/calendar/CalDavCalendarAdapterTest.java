package com.massimotter.weave.backend.service.calendar;

import com.massimotter.weave.backend.config.CalendarCalDavProperties;
import com.massimotter.weave.backend.model.calendar.CalendarScopeResponse;
import com.massimotter.weave.backend.model.calendar.CreateCalendarEventRequest;
import com.massimotter.weave.backend.model.calendar.UpdateCalendarEventRequest;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalDavCalendarAdapterTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void failsClosedWhenBackendActorCredentialIsMissing() {
        CalDavCalendarAdapter adapter = new CalDavCalendarAdapter(new CalendarCalDavProperties(
                "https://files.weave.local", null, null, null, null, 1));

        assertThatThrownBy(() -> adapter.list(principal(), null, null))
                .isInstanceOf(CalendarAdapterException.class)
                .satisfies(error -> assertThat(((CalendarAdapterException) error).type())
                        .isEqualTo(CalendarAdapterException.Type.NOT_CONFIGURED));
    }

    @Test
    void rejectsPrivateUserCalendarTemplatesUntilAccessModelIsContracted() {
        CalDavCalendarAdapter adapter = new CalDavCalendarAdapter(new CalendarCalDavProperties(
                "https://files.weave.local",
                "/remote.php/dav/calendars/{user}/personal/",
                CalendarCalDavProperties.AuthMode.BASIC,
                "weave-backend",
                "secret",
                1));

        assertThatThrownBy(() -> adapter.list(principal(), null, null))
                .isInstanceOf(CalendarAdapterException.class)
                .satisfies(error -> {
                    CalendarAdapterException adapterError = (CalendarAdapterException) error;
                    assertThat(adapterError.type()).isEqualTo(CalendarAdapterException.Type.NOT_CONFIGURED);
                    assertThat(adapterError.details()).containsEntry("calendarScope", "private-personal");
                    assertThat(adapterError.details()).containsEntry("privateUserTemplateAllowed", false);
                });
    }

    @Test
    void listsEventsWithCalDavCalendarQuery() throws Exception {
        List<String> methods = new ArrayList<>();
        List<String> paths = new ArrayList<>();
        server = server(exchange -> {
            methods.add(exchange.getRequestMethod());
            paths.add(exchange.getRequestURI().getRawPath());
            String response = """
                    <?xml version="1.0" encoding="utf-8"?>
                    <d:multistatus xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
                      <d:response>
                        <d:href>/remote.php/dav/calendars/weave-backend/personal/planning.ics</d:href>
                        <d:propstat><d:prop>
                          <d:getetag>\"etag-1\"</d:getetag>
                          <c:calendar-data>BEGIN:VCALENDAR&#13;
BEGIN:VEVENT&#13;
UID:event-1&#13;
LAST-MODIFIED:20260425T090000Z&#13;
DTSTART;TZID=Europe/Berlin:20260426T100000&#13;
DTEND;TZID=Europe/Berlin:20260426T110000&#13;
SUMMARY:Planning&#13;
ATTENDEE;CN=Ada Lovelace;ROLE=REQ-PARTICIPANT;PARTSTAT=ACCEPTED:mailto:ada@example.com&#13;
END:VEVENT&#13;
END:VCALENDAR&#13;
</c:calendar-data>
                        </d:prop></d:propstat>
                      </d:response>
                    </d:multistatus>
                    """;
            respond(exchange, 207, response, null);
        });

        var events = adapter().list(
                principal(),
                OffsetDateTime.parse("2026-04-26T00:00:00+02:00"),
                OffsetDateTime.parse("2026-04-27T00:00:00+02:00"));

        assertThat(methods).containsExactly("REPORT");
        assertThat(paths).containsExactly("/remote.php/dav/calendars/weave-backend/personal/");
        assertThat(events).hasSize(1);
        assertThat(events.get(0).title()).isEqualTo("Planning");
        assertThat(events.get(0).etag()).isEqualTo("\"etag-1\"");
        assertThat(events.get(0).scope().type()).isEqualTo("workspace");
        assertThat(events.get(0).updatedAt()).isEqualTo(OffsetDateTime.parse("2026-04-25T09:00:00Z"));
        assertThat(events.get(0).attendees()).singleElement()
                .satisfies(attendee -> assertThat(attendee.email()).isEqualTo("ada@example.com"));
        assertThat(events.get(0).providerRef().rawProviderPathExposed()).isFalse();
    }

    @Test
    void listsTeamAndChannelScopesFromSeparateBackendActorCollections() throws Exception {
        List<String> paths = new ArrayList<>();
        server = server(exchange -> {
            paths.add(exchange.getRequestURI().getRawPath());
            respond(exchange, 207, """
                    <?xml version="1.0" encoding="utf-8"?>
                    <d:multistatus xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav" />
                    """, null);
        });

        CalDavCalendarAdapter adapter = adapter();
        adapter.list(principal(), CalendarScopeResponse.team("Engineering", "Engineering"), null, null);
        adapter.list(principal(), CalendarScopeResponse.channel(
                "Engineering", "Engineering General", "Engineering / General"), null, null);

        assertThat(paths).containsExactly(
                "/remote.php/dav/calendars/weave-backend/weave-team-engineering/",
                "/remote.php/dav/calendars/weave-backend/weave-channel-engineering-general/");
    }

    @Test
    void rejectsOpaqueEventIdsReadThroughTheWrongScope() throws Exception {
        server = server(exchange -> respond(exchange, 201, "", "\"etag-new\""));
        CalDavCalendarAdapter adapter = adapter();
        var created = adapter.create(principal(), new CreateCalendarEventRequest(
                "Planning",
                null,
                OffsetDateTime.parse("2026-04-26T10:00:00+02:00"),
                OffsetDateTime.parse("2026-04-26T11:00:00+02:00"),
                "Europe/Berlin",
                null,
                false,
                CalendarScopeResponse.team("engineering", "Engineering")));

        assertThatThrownBy(() -> adapter.read(principal(), CalendarScopeResponse.workspace(), created.id()))
                .isInstanceOf(CalendarAdapterException.class)
                .satisfies(error -> assertThat(((CalendarAdapterException) error).type())
                        .isEqualTo(CalendarAdapterException.Type.INVALID_REQUEST));
    }

    @Test
    void createsUpdatesAndDeletesEventThroughOpaqueFacadeId() throws Exception {
        List<String> methods = new ArrayList<>();
        List<String> requestBodies = new ArrayList<>();
        server = server(exchange -> {
            methods.add(exchange.getRequestMethod());
            requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            if ("GET".equals(exchange.getRequestMethod())) {
                respond(exchange, 200, """
                        BEGIN:VCALENDAR
                        BEGIN:VEVENT
                        UID:event-1
                        DTSTART;TZID=Europe/Berlin:20260426T100000
                        DTEND;TZID=Europe/Berlin:20260426T110000
                        SUMMARY:Planning
                        END:VEVENT
                        END:VCALENDAR
                        """, "\"etag-existing\"");
            } else if ("PUT".equals(exchange.getRequestMethod())) {
                respond(exchange, 201, "", "\"etag-new\"");
            } else if ("DELETE".equals(exchange.getRequestMethod())) {
                respond(exchange, 204, "", null);
            } else {
                respond(exchange, 405, "", null);
            }
        });

        CalDavCalendarAdapter adapter = adapter();
        var created = adapter.create(principal(), new CreateCalendarEventRequest(
                "Planning",
                null,
                OffsetDateTime.parse("2026-04-26T10:00:00+02:00"),
                OffsetDateTime.parse("2026-04-26T11:00:00+02:00"),
                "Europe/Berlin",
                null,
                false));
        var read = adapter.read(principal(), created.id());
        var updated = adapter.update(principal(), created.id(), new UpdateCalendarEventRequest(
                "Updated", null, null, null, null, null, null, created.etag()));
        adapter.delete(principal(), created.id());

        assertThat(methods).containsExactly("PUT", "GET", "GET", "PUT", "DELETE");
        assertThat(requestBodies.get(0)).contains("SUMMARY:Planning");
        assertThat(requestBodies.get(3)).contains("SUMMARY:Updated");
        assertThat(read.title()).isEqualTo("Planning");
        assertThat(read.etag()).isEqualTo("\"etag-existing\"");
        assertThat(read.scope().type()).isEqualTo("workspace");
        assertThat(updated.title()).isEqualTo("Updated");
    }

    @Test
    void createsBackendActorCalendarWhenCreateFindsMissingCollection() throws Exception {
        List<String> methods = new ArrayList<>();
        List<String> paths = new ArrayList<>();
        List<String> requestBodies = new ArrayList<>();
        server = server(exchange -> {
            methods.add(exchange.getRequestMethod());
            paths.add(exchange.getRequestURI().getRawPath());
            requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            if ("PUT".equals(exchange.getRequestMethod()) && methods.size() == 1) {
                respond(exchange, 404, "", null);
            } else if ("MKCALENDAR".equals(exchange.getRequestMethod())) {
                respond(exchange, 201, "", null);
            } else if ("PUT".equals(exchange.getRequestMethod())) {
                respond(exchange, 201, "", "\"etag-new\"");
            } else {
                respond(exchange, 405, "", null);
            }
        });

        var created = adapter().create(principal(), new CreateCalendarEventRequest(
                "Planning",
                null,
                OffsetDateTime.parse("2026-04-26T10:00:00+02:00"),
                OffsetDateTime.parse("2026-04-26T11:00:00+02:00"),
                "Europe/Berlin",
                null,
                false));

        assertThat(methods).containsExactly("PUT", "MKCALENDAR", "PUT");
        assertThat(paths.get(0)).startsWith("/remote.php/dav/calendars/weave-backend/personal/");
        assertThat(paths.get(1)).isEqualTo("/remote.php/dav/calendars/weave-backend/personal/");
        assertThat(paths.get(2)).isEqualTo(paths.get(0));
        assertThat(requestBodies.get(1)).contains("Weave workspace calendar");
        assertThat(created.title()).isEqualTo("Planning");
        assertThat(created.etag()).isEqualTo("\"etag-new\"");
    }

    private CalDavCalendarAdapter adapter() {
        return new CalDavCalendarAdapter(new CalendarCalDavProperties(
                "http://localhost:" + server.getAddress().getPort(),
                "/remote.php/dav/calendars/weave-backend/personal/",
                CalendarCalDavProperties.AuthMode.BASIC,
                "backend",
                "secret",
                5));
    }

    private CalendarPrincipal principal() {
        return new CalendarPrincipal("user-123", "massimo");
    }

    private HttpServer server(ExchangeHandler handler) throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        httpServer.createContext("/", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).startsWith("Basic ");
            handler.handle(exchange);
        });
        httpServer.start();
        return httpServer;
    }

    private void respond(HttpExchange exchange, int status, String body, String etag) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (etag != null) {
            exchange.getResponseHeaders().add("ETag", etag);
        }
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}

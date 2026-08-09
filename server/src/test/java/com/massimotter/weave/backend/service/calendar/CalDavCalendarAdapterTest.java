package com.massimotter.weave.backend.service.calendar;

import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarEvent;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarId;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarScope;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarWrite;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.EventId;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.EventVersion;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.ScopeType;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.WriteIntent;
import com.massimotter.weave.backend.config.CalendarCalDavProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
        if (server != null) server.stop(0);
    }

    @Test
    void failsClosedWhenBackendActorCredentialIsMissing() {
        CalDavCalendarAdapter adapter = new CalDavCalendarAdapter(new CalendarCalDavProperties(
                "https://files.weave.test", null, null, null, null, 1, ZoneOffset.UTC));

        assertThatThrownBy(() -> adapter.query(calendarId(), CalendarScope.workspace(), null, null))
                .isInstanceOfSatisfying(CalendarAdapterException.class, error ->
                        assertThat(error.type()).isEqualTo(CalendarAdapterException.Type.NOT_CONFIGURED));
    }

    @Test
    void rejectsPrivateUserCalendarTemplatesUntilAccessModelIsContracted() {
        CalDavCalendarAdapter adapter = new CalDavCalendarAdapter(new CalendarCalDavProperties(
                "https://files.weave.test",
                "/remote.php/dav/calendars/{user}/personal/",
                CalendarCalDavProperties.AuthMode.BASIC,
                "weave-backend",
                "secret",
                1,
                ZoneOffset.UTC));

        assertThatThrownBy(() -> adapter.query(calendarId(), CalendarScope.workspace(), null, null))
                .isInstanceOfSatisfying(CalendarAdapterException.class, error -> {
                    assertThat(error.type()).isEqualTo(CalendarAdapterException.Type.NOT_CONFIGURED);
                    assertThat(error.details()).containsEntry("calendarScope", "private-personal");
                    assertThat(error.details()).containsEntry("privateUserTemplateAllowed", false);
                });
    }

    @Test
    void healthProbeUsesBoundedAuthenticatedCalDavAndNormalizesRetryAfter() throws Exception {
        List<String> methods = new ArrayList<>();
        server = server(exchange -> {
            methods.add(exchange.getRequestMethod());
            assertThat(exchange.getRequestHeaders().getFirst("Depth")).isEqualTo("0");
            exchange.getResponseHeaders().add("Retry-After", "120");
            respond(exchange, 429, "raw provider throttle for backend:secret", null);
        });

        var result = adapter().healthProbe();

        assertThat(methods).containsExactly("PROPFIND");
        assertThat(result.state().value()).isEqualTo("degraded");
        assertThat(result.supportSafeCode()).isEqualTo("calendar-storage-rate-limited");
        assertThat(result.retryAfter()).isEqualTo(Duration.ofSeconds(120));
        assertThat(result.toString()).doesNotContain("backend:secret");
    }

    @Test
    void listsCanonicalEventsWithCalDavCalendarQuery() throws Exception {
        List<String> methods = new ArrayList<>();
        List<String> paths = new ArrayList<>();
        server = server(exchange -> {
            methods.add(exchange.getRequestMethod());
            paths.add(exchange.getRequestURI().getRawPath());
            respond(exchange, 207, multistatus("""
                    UID:event-1&#13;
                    LAST-MODIFIED:20260425T090000Z&#13;
                    DTSTART;TZID=Europe/Berlin:20260426T100000&#13;
                    DTEND;TZID=Europe/Berlin:20260426T110000&#13;
                    SUMMARY:Planning&#13;
                    ATTENDEE;CN=Ada Lovelace;ROLE=REQ-PARTICIPANT;PARTSTAT=ACCEPTED:mailto:ada@example.com&#13;
                    """, "\"etag-1\""), null);
        });

        var events = adapter().query(
                calendarId(),
                CalendarScope.workspace(),
                Instant.parse("2026-04-25T22:00:00Z"),
                Instant.parse("2026-04-26T22:00:00Z"));

        assertThat(methods).containsExactly("REPORT");
        assertThat(paths).containsExactly("/remote.php/dav/calendars/weave-backend/personal/");
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.id().value()).isEqualTo("event-1");
            assertThat(event.title()).isEqualTo("Planning");
            assertThat(event.version().value()).isEqualTo("\"etag-1\"");
            assertThat(event.scope().type()).isEqualTo(ScopeType.WORKSPACE);
            assertThat(event.updatedAt()).isEqualTo(Instant.parse("2026-04-25T09:00:00Z"));
            assertThat(event.attendees()).singleElement()
                    .satisfies(attendee -> assertThat(attendee.address()).isEqualTo("ada@example.com"));
        });
    }

    @Test
    void usesSeparateBackendActorCollectionsForTeamAndChannelScopes() throws Exception {
        List<String> paths = new ArrayList<>();
        server = server(exchange -> {
            paths.add(exchange.getRequestURI().getRawPath());
            respond(exchange, 207, emptyMultistatus(), null);
        });

        CalDavCalendarAdapter adapter = adapter();
        adapter.query(calendarId(), new CalendarScope(ScopeType.TEAM, "Engineering", null), null, null);
        adapter.query(calendarId(), new CalendarScope(ScopeType.CHANNEL, "Engineering", "Engineering General"), null, null);

        assertThat(paths).containsExactly(
                "/remote.php/dav/calendars/weave-backend/weave-team-engineering/",
                "/remote.php/dav/calendars/weave-backend/weave-channel-engineering-general/");
    }

    @Test
    void createsReadsUpdatesAndDeletesByStableCanonicalUid() throws Exception {
        List<String> methods = new ArrayList<>();
        List<String> paths = new ArrayList<>();
        List<String> requestBodies = new ArrayList<>();
        server = server(exchange -> {
            methods.add(exchange.getRequestMethod());
            paths.add(exchange.getRequestURI().getRawPath());
            requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            if ("GET".equals(exchange.getRequestMethod())) respond(exchange, 200, calendar("event-1", "Planning"), "\"etag-existing\"");
            else if ("PUT".equals(exchange.getRequestMethod())) respond(exchange, 201, "", "\"etag-new\"");
            else if ("DELETE".equals(exchange.getRequestMethod())) respond(exchange, 204, "", null);
            else respond(exchange, 405, "", null);
        });

        CalDavCalendarAdapter adapter = adapter();
        CalendarEvent created = adapter.write(new CalendarWrite(event("event-1", "Planning", CalendarScope.workspace(), EventVersion.unknown()), WriteIntent.CREATE, EventVersion.unknown()));
        CalendarEvent read = adapter.read(calendarId(), CalendarScope.workspace(), new EventId("event-1"));
        CalendarEvent updated = adapter.write(new CalendarWrite(event("event-1", "Updated", CalendarScope.workspace(), created.version()), WriteIntent.UPDATE, created.version()));
        adapter.delete(calendarId(), CalendarScope.workspace(), new EventId("event-1"), updated.version());

        assertThat(methods).containsExactly("PUT", "GET", "PUT", "DELETE");
        assertThat(paths).containsOnly("/remote.php/dav/calendars/weave-backend/personal/event-1.ics");
        assertThat(requestBodies.get(0)).contains("UID:event-1", "SUMMARY:Planning");
        assertThat(requestBodies.get(2)).contains("SUMMARY:Updated");
        assertThat(read.title()).isEqualTo("Planning");
        assertThat(read.version().value()).isEqualTo("\"etag-existing\"");
        assertThat(updated.title()).isEqualTo("Updated");
    }

    @Test
    void failsClosedWithoutProvisioningWhenCreateFindsMissingCollection() throws Exception {
        List<String> methods = new ArrayList<>();
        List<String> paths = new ArrayList<>();
        server = server(exchange -> {
            methods.add(exchange.getRequestMethod());
            paths.add(exchange.getRequestURI().getRawPath());
            respond(exchange, 404, "", null);
        });

        assertThatThrownBy(() -> adapter().write(new CalendarWrite(
                        event("event-1", "Planning", CalendarScope.workspace(), EventVersion.unknown()),
                        WriteIntent.CREATE,
                        EventVersion.unknown())))
                .isInstanceOfSatisfying(CalendarAdapterException.class, exception ->
                        assertThat(exception.type()).isEqualTo(CalendarAdapterException.Type.NOT_FOUND));
        assertThat(methods).containsExactly("PUT");
        assertThat(paths).containsExactly("/remote.php/dav/calendars/weave-backend/personal/event-1.ics");
    }

    @Test
    void failsClosedWithoutProvisioningWhenQueryFindsMissingCollection() throws Exception {
        List<String> methods = new ArrayList<>();
        List<String> paths = new ArrayList<>();
        server = server(exchange -> {
            methods.add(exchange.getRequestMethod());
            paths.add(exchange.getRequestURI().getRawPath());
            respond(exchange, 404, "", null);
        });

        assertThatThrownBy(() -> adapter().query(calendarId(), CalendarScope.workspace(), null, null))
                .isInstanceOfSatisfying(CalendarAdapterException.class, exception ->
                        assertThat(exception.type()).isEqualTo(CalendarAdapterException.Type.NOT_FOUND));
        assertThat(methods).containsExactly("REPORT");
        assertThat(paths).containsExactly("/remote.php/dav/calendars/weave-backend/personal/");
    }

    @Test
    void mapsCalendarConflictsWithoutLeakingProviderPathsOrBodies() throws Exception {
        server = server(exchange -> respond(exchange, 412,
                "conflict at /remote.php/dav/calendars/weave-backend/personal/event-1.ics for backend:secret", null));

        assertThatThrownBy(() -> adapter().write(new CalendarWrite(
                event("event-1", "Updated", CalendarScope.workspace(), new EventVersion("\"stale\"")),
                WriteIntent.UPDATE,
                new EventVersion("\"stale\""))))
                .isInstanceOfSatisfying(CalendarAdapterException.class, exception -> {
                    assertThat(exception.type()).isEqualTo(CalendarAdapterException.Type.CONFLICT);
                    assertThat(exception.details()).containsEntry("downstreamStatus", 412);
                    assertThat(exception.details()).containsEntry("providerPathRedacted", true);
                    assertSupportSafe(exception);
                });
    }

    @Test
    void preservesBoundedRecurringEventsFromProvider() throws Exception {
        server = server(exchange -> respond(exchange, 207, multistatus("""
                UID:event-recurring&#13;
                DTSTART;TZID=Europe/Berlin:20260322T090000&#13;
                DTEND;TZID=Europe/Berlin:20260322T100000&#13;
                RRULE:FREQ=WEEKLY;COUNT=3&#13;
                EXDATE;TZID=Europe/Berlin:20260329T090000&#13;
                SUMMARY:Planning&#13;
                """, "\"etag-recurring\""), null));

        var event = adapter().query(calendarId(), CalendarScope.workspace(), null, null).get(0);
        var occurrenceEngine = new CalendarOccurrenceEngine(new Ical4jRecurrenceEngine());

        assertThat(event.recurrence().count()).isEqualTo(3);
        assertThat(occurrenceEngine.occurrences(
                        event,
                        Instant.parse("2026-03-20T00:00:00Z"),
                        Instant.parse("2026-04-10T00:00:00Z"),
                        ZoneId.of("Europe/Berlin")))
                .extracting(occurrence -> occurrence.start().toLocalDate().toString())
                .containsExactly("2026-03-22", "2026-04-05");
    }

    @Test
    void mapsCalDavSyncCollectionChangesAndDeletionsToCanonicalIds() throws Exception {
        List<String> requestBodies = new ArrayList<>();
        server = server(exchange -> {
            requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 207, """
                    <?xml version="1.0" encoding="utf-8"?>
                    <d:multistatus xmlns:d="DAV:">
                      <d:response><d:href>/remote.php/dav/calendars/weave-backend/personal/planning%40weave.test.ics</d:href><d:propstat><d:prop><d:getetag>"etag-2"</d:getetag></d:prop></d:propstat><d:status>HTTP/1.1 200 OK</d:status></d:response>
                      <d:response><d:href>/remote.php/dav/calendars/weave-backend/personal/cancelled.ics</d:href><d:status>HTTP/1.1 404 Not Found</d:status></d:response>
                      <d:sync-token>https://provider.invalid/sync/2</d:sync-token>
                    </d:multistatus>
                    """, null);
        });

        var result = adapter().changes(calendarId(), CalendarScope.workspace(), "provider-sync-1");

        assertThat(requestBodies).singleElement().satisfies(body -> assertThat(body)
                .contains("<d:sync-token>provider-sync-1</d:sync-token>")
                .contains("<d:sync-level>1</d:sync-level>"));
        assertThat(result.syncToken()).isEqualTo("https://provider.invalid/sync/2");
        assertThat(result.changes()).extracting(change -> change.eventId().value())
                .containsExactly("planning@weave.test", "cancelled");
        assertThat(result.changes().get(0).version().value()).isEqualTo("\"etag-2\"");
        assertThat(result.changes().get(1).deleted()).isTrue();
    }

    private CalendarEvent event(String id, String title, CalendarScope scope, EventVersion version) {
        return new CalendarEvent(
                calendarId(), new EventId(id), scope, title, null,
                LocalDateTime.parse("2026-04-26T10:00:00"), LocalDateTime.parse("2026-04-26T11:00:00"),
                ZoneId.of("Europe/Berlin"), false, null, List.of(), null, version, null);
    }

    private String multistatus(String eventProperties, String etag) {
        return """
                <?xml version="1.0" encoding="utf-8"?>
                <d:multistatus xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav"><d:response><d:href>/remote.php/dav/calendars/weave-backend/personal/event.ics</d:href><d:propstat><d:prop><d:getetag>%s</d:getetag><c:calendar-data>BEGIN:VCALENDAR&#13;
                BEGIN:VEVENT&#13;
                %sEND:VEVENT&#13;
                END:VCALENDAR&#13;
                </c:calendar-data></d:prop></d:propstat></d:response></d:multistatus>
                """.formatted(etag, eventProperties);
    }

    private String emptyMultistatus() {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?><d:multistatus xmlns:d=\"DAV:\" xmlns:c=\"urn:ietf:params:xml:ns:caldav\" />";
    }

    private String calendar(String uid, String title) {
        return """
                BEGIN:VCALENDAR
                BEGIN:VEVENT
                UID:%s
                DTSTART;TZID=Europe/Berlin:20260426T100000
                DTEND;TZID=Europe/Berlin:20260426T110000
                SUMMARY:%s
                END:VEVENT
                END:VCALENDAR
                """.formatted(uid, title);
    }

    private void assertSupportSafe(CalendarAdapterException exception) {
        String rendered = exception.getMessage() + " " + exception.details();
        assertThat(rendered)
                .doesNotContain("backend:secret")
                .doesNotContain("/remote.php/dav")
                .doesNotContain("localhost:")
                .doesNotContain("conflict at");
    }

    private CalDavCalendarAdapter adapter() {
        return new CalDavCalendarAdapter(new CalendarCalDavProperties(
                "http://localhost:" + server.getAddress().getPort(),
                "/remote.php/dav/calendars/weave-backend/personal/",
                CalendarCalDavProperties.AuthMode.BASIC,
                "backend",
                "secret",
                5,
                ZoneId.of("Europe/Berlin")));
    }

    private CalendarId calendarId() { return new CalendarId("massimo"); }

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
        if (etag != null) exchange.getResponseHeaders().add("ETag", etag);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @FunctionalInterface
    private interface ExchangeHandler { void handle(HttpExchange exchange) throws IOException; }
}

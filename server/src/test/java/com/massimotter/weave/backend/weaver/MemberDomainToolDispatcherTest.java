package com.massimotter.weave.backend.weaver;

import com.massimotter.weave.backend.chat.ChatDomainFacadeService;
import com.massimotter.weave.backend.chat.domain.ChatMessage;
import com.massimotter.weave.backend.model.calendar.CalendarEventResponse;
import com.massimotter.weave.backend.model.calendar.CalendarEventsResponse;
import com.massimotter.weave.backend.model.calendar.CalendarScopeResponse;
import com.massimotter.weave.backend.model.files.FileItemResponse;
import com.massimotter.weave.backend.model.files.FileListResponse;
import com.massimotter.weave.backend.service.CalendarFacadeService;
import com.massimotter.weave.backend.service.FilesFacadeService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MemberDomainToolDispatcherTest {

    private final FilesFacadeService filesFacadeService = mock(FilesFacadeService.class);
    private final CalendarFacadeService calendarFacadeService = mock(CalendarFacadeService.class);
    private final ChatDomainFacadeService chatDomainFacadeService = mock(ChatDomainFacadeService.class);
    private final MemberDomainToolDispatcher dispatcher = new MemberDomainToolDispatcher(
            filesFacadeService,
            calendarFacadeService,
            chatDomainFacadeService);

    @Test
    void filesSearchUsesFilesFacadeAndProjectsWebdavBackedMcpMetadata() {
        when(filesFacadeService.list("/Team")).thenReturn(teamListing());

        Map<String, Object> result = dispatcher.dispatch(null, "files.search", Map.of(
                "path", "/Team",
                "query", "readme",
                "limit", 5));

        assertThat(result)
                .containsEntry("status", "ok")
                .containsEntry("supportSafe", true)
                .containsEntry("dataPlane", "weave-webdav-facade")
                .containsEntry("webDavFacadePath", "/dav/files")
                .containsEntry("openApiDataPlaneUsed", false)
                .containsEntry("rawProviderPayload", "redacted");
        assertThat(result.get("items").toString())
                .contains("file:/Team/readme.md", "/dav/files/Team/readme.md", "sizeKnown=true", "size=12")
                .doesNotContain("Nextcloud", "remote.php", "Bearer ");
    }

    @Test
    void filesSearchUsesLocaleStableMatchingAndEncodedDavHrefs() {
        when(filesFacadeService.list("/Team")).thenReturn(new FileListResponse(
                "/Team",
                List.of(new FileItemResponse(
                        "files:quarterly",
                        "İstanbul plan.md",
                        "/Team/Design docs/İstanbul plan.md",
                        "file",
                        "text/markdown",
                        null,
                        OffsetDateTime.parse("2026-07-04T12:01:00Z"),
                        true)),
                null));

        Map<String, Object> result = dispatcher.dispatch(null, "files.search", Map.of(
                "path", "/Team",
                "query", "PLAN",
                "limit", 5));

        assertThat(result.get("items").toString())
                .contains("/dav/files/Team/Design%20docs/%C4%B0stanbul%20plan.md", "sizeKnown=false", "size=-1");
    }

    @Test
    void filesReadRequiresCanonicalWeaveFileRefAndReturnsMetadataOnly() {
        when(filesFacadeService.list("/Team")).thenReturn(teamListing());

        Map<String, Object> result = dispatcher.dispatch(null, "files.read", Map.of("fileRef", "file:/Team/readme.md"));

        assertThat(result)
                .containsEntry("status", "ok")
                .containsEntry("dataPlane", "weave-webdav-facade")
                .containsEntry("openApiDataPlaneUsed", false);
        assertThat(result.get("item").toString())
                .contains("file:/Team/readme.md", "readme.md")
                .doesNotContain("content=", "Nextcloud", "remote.php", "Bearer ");
    }

    @Test
    void filesReadRejectsProviderShapedRefsBeforeProviderAccess() {
        Map<String, Object> result = dispatcher.dispatch(null, "files.read", Map.of("fileRef", "https://files.example.invalid/remote.php/dav/files/readme.md"));

        assertThat(result)
                .containsEntry("status", "blocked")
                .containsEntry("supportSafe", true)
                .containsEntry("rawProviderPayload", "redacted");
        assertThat(result.toString()).contains("files_file_ref_requires_weave_webdav_facade_path");
    }

    @Test
    void calendarSearchUsesTheRequestedCanonicalScopeAndReturnsCanonicalEventsOnly() {
        CalendarScopeResponse scope = CalendarScopeResponse.team("engineering", "Team engineering calendar");
        OffsetDateTime from = OffsetDateTime.parse("2026-07-10T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2026-07-11T00:00:00Z");
        when(calendarFacadeService.listCalDavEvents(scope, from, to)).thenReturn(new CalendarEventsResponse(
                scope,
                List.of(calendarEvent(scope, "planning@weave.test", "Planning"))));

        Map<String, Object> result = dispatcher.dispatch(null, "calendar.search_events", Map.of(
                "from", from.toString(),
                "to", to.toString(),
                "query", "plan",
                "calendarRef", "calendar:team:engineering",
                "limit", 5));

        assertThat(result)
                .containsEntry("status", "ok")
                .containsEntry("dataPlane", "weave-caldav-facade")
                .containsEntry("openApiDataPlaneUsed", false)
                .containsEntry("rawProviderPayload", "redacted");
        assertThat(result.toString())
                .contains("calendar:team:engineering", "event:planning@weave.test", "Planning")
                .doesNotContain("providerRef", "remote.php", "Nextcloud");
    }

    @Test
    void calendarCreateMapsTheCanonicalCalendarRefIntoTheDomainScope() {
        CalendarScopeResponse scope = CalendarScopeResponse.channel("engineering", "general", "Channel general calendar");
        when(calendarFacadeService.create(argThat(request -> request.scope().equals(scope)
                        && request.title().equals("Planning")
                        && request.startsAt().equals(OffsetDateTime.parse("2026-07-10T09:00:00Z")))))
                .thenReturn(calendarEvent(scope, "created@weave.test", "Planning"));

        Map<String, Object> result = dispatcher.dispatch(null, "calendar.create_event", Map.of(
                "title", "Planning",
                "startsAt", "2026-07-10T09:00:00Z",
                "calendarRef", "calendar:channel:engineering:general"));

        assertThat(result)
                .containsEntry("status", "ok")
                .containsEntry("dataPlane", "weave-caldav-facade")
                .containsEntry("rawProviderPayload", "redacted");
        assertThat(result.toString())
                .contains("calendar:channel:engineering:general", "event:created@weave.test")
                .doesNotContain("providerRef", "Nextcloud");
        verify(calendarFacadeService).create(argThat(request -> request.scope().equals(scope)));
    }

    @Test
    void calendarCreateRejectsProviderShapedOrMalformedScopeBeforeFacadeAccess() {
        Map<String, Object> result = dispatcher.dispatch(null, "calendar.create_event", Map.of(
                "title", "Planning",
                "startsAt", "not-a-date",
                "calendarRef", "https://calendar.invalid/remote.php/dav"));

        assertThat(result)
                .containsEntry("status", "blocked")
                .containsEntry("rawProviderPayload", "redacted");
        verifyNoInteractions(calendarFacadeService);
    }

    @Test
    void chatSendUsesCanonicalChatFacadeAndProjectsNoProviderPayload() {
        Jwt jwt = mock(Jwt.class);
        when(chatDomainFacadeService.sendMessage("channel-general", "mcp-send-1", "Hello", jwt))
                .thenReturn(new ChatMessage(
                        "message-1",
                        "channel-general",
                        "user:member",
                        Instant.parse("2026-07-09T20:00:00Z"),
                        "Hello",
                        "sent",
                        List.of()));

        Map<String, Object> result = dispatcher.dispatch(jwt, "chat.send_message", Map.of(
                "threadRef", "thread:channel-general",
                "body", "Hello",
                "idempotencyKey", "mcp-send-1"));

        assertThat(result)
                .containsEntry("status", "ok")
                .containsEntry("supportSafe", true)
                .containsEntry("dataPlane", "weave-matrix-facade")
                .containsEntry("rawProviderPayload", "redacted");
        assertThat(result.toString())
                .contains("conversation:channel-general", "message:message-1")
                .doesNotContain("synapse", "providerTenant", "providerChannelId", "access_token");
    }

    @Test
    void chatSendRejectsProviderShapedThreadBeforeCanonicalFacade() {
        Map<String, Object> result = dispatcher.dispatch(mock(Jwt.class), "chat.send_message", Map.of(
                "threadRef", "https://matrix.example.invalid/_matrix/room/1",
                "body", "Hello",
                "idempotencyKey", "mcp-send-1"));

        assertThat(result)
                .containsEntry("status", "blocked")
                .containsEntry("rawProviderPayload", "redacted");
    }

    private FileListResponse teamListing() {
        return new FileListResponse(
                "/Team",
                List.of(
                        new FileItemResponse(
                                "files:folder",
                                "Design",
                                "/Team/Design",
                                "folder",
                                null,
                                null,
                                OffsetDateTime.parse("2026-07-04T12:00:00Z"),
                                false),
                        new FileItemResponse(
                                "files:readme",
                                "readme.md",
                                "/Team/readme.md",
                                "file",
                                "text/markdown",
                                12L,
                                OffsetDateTime.parse("2026-07-04T12:01:00Z"),
                                true)),
                null);
    }

    private CalendarEventResponse calendarEvent(CalendarScopeResponse scope, String id, String title) {
        return new CalendarEventResponse(
                id,
                title,
                "Support-safe description",
                OffsetDateTime.parse("2026-07-10T09:00:00Z"),
                OffsetDateTime.parse("2026-07-10T10:00:00Z"),
                "UTC",
                "",
                false,
                "\"etag-1\"",
                scope);
    }
}

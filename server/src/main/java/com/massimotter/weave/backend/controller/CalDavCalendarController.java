package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.controller.protocol.CalDavReportParser;
import com.massimotter.weave.backend.controller.protocol.CalDavReportParser.InvalidCalDavReportException;
import com.massimotter.weave.backend.controller.protocol.CalDavReportParser.Report;
import com.massimotter.weave.backend.model.calendar.CalendarEventResponse;
import com.massimotter.weave.backend.model.calendar.CalendarScopeResponse;
import com.massimotter.weave.backend.model.calendar.CalendarScopesResponse;
import com.massimotter.weave.backend.service.CalendarFacadeService;
import com.massimotter.weave.backend.service.calendar.CalDavEventResource;
import com.massimotter.weave.backend.service.calendar.CalDavSyncResult;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.FreeBusyWindow;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;

@RestController
@Hidden
public class CalDavCalendarController {

    private static final String CALDAV_ROOT = "/caldav";
    private static final MediaType XML = MediaType.APPLICATION_XML;
    private static final MediaType CALENDAR = MediaType.parseMediaType("text/calendar; charset=UTF-8");
    private static final DateTimeFormatter CALDAV_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);

    private final CalendarFacadeService calendarFacadeService;

    public CalDavCalendarController(CalendarFacadeService calendarFacadeService) {
        this.calendarFacadeService = calendarFacadeService;
    }

    @RequestMapping(value = "/.well-known/caldav")
    public ResponseEntity<Void> wellKnownCalDav() {
        return ResponseEntity.status(HttpStatus.PERMANENT_REDIRECT)
                .header(HttpHeaders.LOCATION, CALDAV_ROOT)
                .build();
    }

    @RequestMapping(value = {"/caldav", "/caldav/", "/caldav/**"}, method = RequestMethod.OPTIONS)
    public ResponseEntity<Void> handleOptions() {
        return options();
    }

    @RequestMapping({"/caldav", "/caldav/", "/caldav/**"})
    public ResponseEntity<?> handle(HttpServletRequest request) {
        String method = request.getMethod().toUpperCase(Locale.ROOT);
        try {
            return switch (method) {
                case "OPTIONS" -> options();
                case "PROPFIND" -> propfind(request);
                case "REPORT" -> report(request);
                case "GET" -> get(request, false);
                case "HEAD" -> get(request, true);
                case "PUT" -> put(request);
                case "DELETE" -> delete(request);
                case "MKCALENDAR", "COPY", "MOVE", "LOCK", "UNLOCK" -> unsupportedMethod(method);
                default -> unsupportedMethod(method);
            };
        } catch (ApiErrorException exception) {
            return calDavError(exception);
        }
    }

    private ResponseEntity<Void> options() {
        return ResponseEntity.noContent()
                .header("DAV", "1, calendar-access")
                .header(HttpHeaders.ALLOW, "OPTIONS, PROPFIND, REPORT, GET, HEAD, PUT, DELETE")
                .header("MS-Author-Via", "DAV")
                .build();
    }

    private ResponseEntity<String> propfind(HttpServletRequest request) {
        String depth = request.getHeader("Depth");
        if (depth != null && !depth.isBlank() && !"0".equals(depth) && !"1".equals(depth)) {
            throw new ApiErrorException(
                    HttpStatus.FORBIDDEN,
                    "caldav-depth-not-supported",
                    "Only Depth 0 and Depth 1 are supported for Weave Calendar CalDAV.",
                    Map.of("module", "calendar", "operation", "caldav-propfind"));
        }
        String productPath = productPath(request);
        if (isPrincipalPath(productPath)) {
            return ResponseEntity.status(207)
                    .contentType(XML)
                    .header("DAV", "1, calendar-access")
                    .body(principalMultistatus(productPath));
        }
        if (!"/".equals(productPath)) {
            scopeFromPath(productPath);
        }

        CalendarScopesResponse scopes = calendarFacadeService.scopes();
        boolean includeChildren = "1".equals(depth);
        return ResponseEntity.status(207)
                .contentType(XML)
                .header("DAV", "1, calendar-access")
                .body(multistatus(includeChildren, scopes));
    }

    private ResponseEntity<String> report(HttpServletRequest request) {
        Report report;
        try {
            report = CalDavReportParser.parse(requestBody(request));
        } catch (InvalidCalDavReportException exception) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "caldav-report-invalid",
                    exception.getMessage(),
                    Map.of("module", "calendar", "operation", "caldav-report"));
        }
        if (report.kind() == CalDavReportParser.Kind.CALENDAR_MULTIGET) {
            return ResponseEntity.status(207)
                    .contentType(XML)
                    .header("DAV", "1, calendar-access")
                    .header("X-Weave-Projection", "caldav-calendar-data-plane")
                    .body(calendarMultigetMultistatus(report.hrefs()));
        }
        if (report.kind() == CalDavReportParser.Kind.SYNC_COLLECTION) {
            CalendarScopeResponse scope = scope(request);
            CalDavSyncResult result = calendarFacadeService.syncCalDavResources(scope, report.syncToken());
            return ResponseEntity.status(207)
                    .contentType(XML)
                    .header("DAV", "1, calendar-access")
                    .header("Sync-Token", result.syncToken())
                    .header("X-Weave-Projection", "caldav-calendar-data-plane")
                    .body(calendarSyncMultistatus(result));
        }
        CalendarScopeResponse scope = scope(request);
        TimeRange range = timeRange(report);
        if (report.kind() == CalDavReportParser.Kind.FREE_BUSY_QUERY) {
            return ResponseEntity.ok()
                    .contentType(CALENDAR)
                    .header("X-Weave-Projection", "caldav-calendar-data-plane")
                    .body(freeBusyCalendar(
                            calendarFacadeService.calDavFreeBusy(scope, range.from(), range.to()),
                            range));
        }
        var events = calendarFacadeService.listCalDavResources(scope, range.from(), range.to());
        return ResponseEntity.status(207)
                .contentType(XML)
                .header("DAV", "1, calendar-access")
                .header("X-Weave-Projection", "caldav-calendar-data-plane")
                .body(calendarQueryMultistatus(events));
    }

    private ResponseEntity<byte[]> get(HttpServletRequest request, boolean headOnly) {
        String eventUid = eventUid(request);
        CalendarScopeResponse scope = scope(request);
        CalDavEventResource event = calendarFacadeService.readCalDavResource(eventUid, scope);
        byte[] body = event.calendarData().getBytes(StandardCharsets.UTF_8);
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(CALENDAR)
                .contentLength(body.length)
                .eTag(event.etag())
                .cacheControl(CacheControl.empty().noTransform())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + safeFilename(eventUid) + ".ics\"");
        return builder.body(headOnly ? null : body);
    }

    private ResponseEntity<Void> put(HttpServletRequest request) {
        String eventUid = eventUid(request);
        CalendarScopeResponse scope = scope(request);
        var event = calendarFacadeService.putCalDavEventIcs(
                eventUid,
                requestBody(request),
                request.getHeader(HttpHeaders.IF_MATCH),
                request.getHeader(HttpHeaders.IF_NONE_MATCH),
                scope);
        HttpStatus status = "*".equals(request.getHeader(HttpHeaders.IF_NONE_MATCH))
                ? HttpStatus.CREATED
                : HttpStatus.NO_CONTENT;
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status)
                .header(HttpHeaders.LOCATION, calDavHref(event))
                .contentType(CALENDAR);
        if (event.etag() != null && !event.etag().isBlank()) {
            builder.eTag(event.etag());
        }
        return builder.build();
    }

    private ResponseEntity<Void> delete(HttpServletRequest request) {
        String ifMatch = request.getHeader(HttpHeaders.IF_MATCH);
        CalendarScopeResponse scope = scope(request);
        if (ifMatch != null && !ifMatch.isBlank()) {
            CalendarEventResponse event = calendarFacadeService.readCalDavEvent(eventUid(request), scope);
            if (!etagMatches(event.etag(), ifMatch)) {
                throw new ApiErrorException(
                        HttpStatus.PRECONDITION_FAILED,
                        "caldav-precondition-failed",
                        "If-Match did not match the current calendar event state.",
                        Map.of("module", "calendar", "operation", "caldav-delete"));
            }
        }
        calendarFacadeService.deleteCalDavEventIcs(eventUid(request), scope);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<String> unsupportedMethod(String method) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .contentType(XML)
                .header(HttpHeaders.ALLOW, "OPTIONS, PROPFIND, REPORT, GET, HEAD, PUT, DELETE")
                .header("X-Weave-Error-Code", "caldav-method-not-implemented")
                .body(errorXml("caldav-method-not-implemented",
                        "Weave Calendar CalDAV does not implement " + method + " in the current protocol slice."));
    }

    private ResponseEntity<String> calDavError(ApiErrorException exception) {
        return ResponseEntity.status(exception.status())
                .contentType(XML)
                .header("X-Weave-Error-Code", exception.code())
                .body(errorXml(exception.code(), exception.getMessage()));
    }

    private String eventUid(HttpServletRequest request) {
        String productPath = productPath(request);
        String[] segments = productPath.split("/");
        String last = segments.length == 0 ? "" : segments[segments.length - 1];
        if (last.endsWith(".ics")) {
            last = last.substring(0, last.length() - ".ics".length());
        }
        if (last.isBlank()) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "caldav-event-path-required",
                    "CalDAV event operations require an event .ics path.",
                    Map.of("module", "calendar", "operation", "caldav-event-path"));
        }
        return last;
    }

    private CalendarScopeResponse scope(HttpServletRequest request) {
        return scopeFromPath(productPath(request));
    }

    private CalendarScopeResponse scopeFromPath(String productPath) {
        String firstSegment = firstSegment(productPath);
        if (firstSegment == null || firstSegment.isBlank() || firstSegment.endsWith(".ics") || "workspace".equals(firstSegment)) {
            return CalendarScopeResponse.workspace();
        }
        if (firstSegment.startsWith("team:")) {
            String teamId = firstSegment.substring("team:".length()).trim();
            if (!teamId.isBlank()) {
                return CalendarScopeResponse.team(teamId, teamId + " team calendar");
            }
        }
        if (firstSegment.startsWith("channel:")) {
            String channelId = firstSegment.substring("channel:".length()).trim();
            if (!channelId.isBlank()) {
                return CalendarScopeResponse.channel("engineering", channelId, "Engineering / " + channelId + " channel calendar");
            }
        }
        throw new ApiErrorException(
                HttpStatus.BAD_REQUEST,
                "caldav-scope-invalid",
                "CalDAV paths must use workspace, team:<teamId>, or channel:<channelId> scope segments.",
                Map.of("module", "calendar", "operation", "caldav-scope"));
    }

    private String firstSegment(String productPath) {
        if (productPath == null || productPath.isBlank() || "/".equals(productPath)) {
            return null;
        }
        String normalized = productPath.startsWith("/") ? productPath.substring(1) : productPath;
        int slash = normalized.indexOf('/');
        return slash < 0 ? normalized : normalized.substring(0, slash);
    }

    private String productPath(HttpServletRequest request) {
        String requestPath = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && requestPath.startsWith(contextPath)) {
            requestPath = requestPath.substring(contextPath.length());
        }
        String suffix = requestPath.length() <= CALDAV_ROOT.length() ? "" : requestPath.substring(CALDAV_ROOT.length());
        String decoded = UriUtils.decode(suffix, StandardCharsets.UTF_8);
        return decoded == null || decoded.isBlank() ? "/" : decoded;
    }

    private String requestBody(HttpServletRequest request) {
        try {
            return new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "calendar-ics-unreadable",
                    "Calendar request body could not be read by the backend.",
                    Map.of("module", "calendar", "operation", "caldav-body"));
        }
    }

    private String multistatus(boolean includeChildren, CalendarScopesResponse scopes) {
        StringBuilder xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <d:multistatus xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
                """);
        appendCollection(xml, CALDAV_ROOT + "/", "Weave Calendar");
        if (includeChildren) {
            for (CalendarScopeResponse scope : scopes.scopes()) {
                appendCollection(xml, calDavScopeHref(scope), scope.label());
            }
        }
        xml.append("</d:multistatus>");
        return xml.toString();
    }

    private String principalMultistatus(String productPath) {
        String href = CALDAV_ROOT + (productPath.endsWith("/") ? productPath : productPath + "/");
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <d:multistatus xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
                  <d:response>
                    <d:href>%s</d:href>
                    <d:propstat>
                      <d:prop>
                        <d:resourcetype><d:principal/></d:resourcetype>
                        <d:current-user-principal><d:href>%s</d:href></d:current-user-principal>
                        <c:calendar-home-set><d:href>/caldav/</d:href></c:calendar-home-set>
                      </d:prop>
                      <d:status>HTTP/1.1 200 OK</d:status>
                    </d:propstat>
                  </d:response>
                </d:multistatus>
                """.formatted(escapeXml(href), escapeXml(href));
    }

    private boolean isPrincipalPath(String productPath) {
        return productPath != null && productPath.matches("^/principals/users/[^/]+/?$");
    }

    private String calendarMultigetMultistatus(List<String> hrefs) {
        List<CalDavEventResource> events = new ArrayList<>();
        for (String href : hrefs) {
            CalendarEventReference reference = eventReferenceFromHref(href);
            if (reference != null && reference.eventUid() != null && !reference.eventUid().isBlank()) {
                events.add(calendarFacadeService.readCalDavResource(reference.eventUid(), reference.scope()));
            }
        }
        return calendarQueryMultistatus(events);
    }

    private String calendarQueryMultistatus(Iterable<CalDavEventResource> events) {
        StringBuilder xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <d:multistatus xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
                """);
        for (CalDavEventResource event : events) {
            xml.append("  <d:response>\n")
                    .append("    <d:href>").append(escapeXml(calDavHref(event))).append("</d:href>\n")
                    .append("    <d:propstat>\n")
                    .append("      <d:prop>\n")
                    .append("        <d:getetag>").append(escapeXml(event.etag())).append("</d:getetag>\n")
                    .append("        <c:calendar-data>").append(escapeXml(event.calendarData())).append("</c:calendar-data>\n")
                    .append("      </d:prop>\n")
                    .append("      <d:status>HTTP/1.1 200 OK</d:status>\n")
                    .append("    </d:propstat>\n")
                    .append("  </d:response>\n");
        }
        xml.append("</d:multistatus>");
        return xml.toString();
    }

    private String calendarSyncMultistatus(CalDavSyncResult result) {
        StringBuilder xml = new StringBuilder(calendarQueryMultistatus(result.changedResources()));
        int closingTag = xml.lastIndexOf("</d:multistatus>");
        StringBuilder changes = new StringBuilder();
        changes.append("  <d:sync-token>").append(escapeXml(result.syncToken())).append("</d:sync-token>\n");
        for (String deletedEventId : result.deletedEventIds()) {
            changes.append("  <d:response>\n")
                    .append("    <d:href>")
                    .append(escapeXml(CALDAV_ROOT + "/" + strictPathSegment(result.scope().id())
                            + "/" + strictPathSegment(deletedEventId) + ".ics"))
                    .append("</d:href>\n")
                    .append("    <d:status>HTTP/1.1 404 Not Found</d:status>\n")
                    .append("  </d:response>\n");
        }
        xml.insert(closingTag, changes);
        return xml.toString();
    }

    private CalendarEventReference eventReferenceFromHref(String href) {
        String rawPath;
        try {
            rawPath = java.net.URI.create(href).getRawPath();
        } catch (IllegalArgumentException exception) {
            rawPath = href;
        }
        String decoded = UriUtils.decode(rawPath, StandardCharsets.UTF_8);
        String last = decoded.substring(decoded.lastIndexOf('/') + 1);
        if (last.endsWith(".ics")) {
            last = last.substring(0, last.length() - ".ics".length());
        }
        String suffix = decoded.startsWith(CALDAV_ROOT) ? decoded.substring(CALDAV_ROOT.length()) : decoded;
        return new CalendarEventReference(scopeFromPath(suffix), last);
    }

    private String freeBusyCalendar(Iterable<FreeBusyWindow> windows, TimeRange range) {
        StringBuilder calendar = new StringBuilder();
        calendar.append("BEGIN:VCALENDAR\r\n");
        calendar.append("VERSION:2.0\r\n");
        calendar.append("PRODID:-//Weave//CalDAV Facade//EN\r\n");
        calendar.append("BEGIN:VFREEBUSY\r\n");
        calendar.append("UID:weave-freebusy\r\n");
        calendar.append("DTSTAMP:").append(CALDAV_TIME.format(OffsetDateTime.now(ZoneOffset.UTC))).append("\r\n");
        if (range.from() != null) {
            calendar.append("DTSTART:").append(CALDAV_TIME.format(range.from())).append("\r\n");
        }
        if (range.to() != null) {
            calendar.append("DTEND:").append(CALDAV_TIME.format(range.to())).append("\r\n");
        }
        for (FreeBusyWindow window : windows) {
            calendar.append("FREEBUSY:")
                    .append(CALDAV_TIME.format(window.start()))
                    .append("/")
                    .append(CALDAV_TIME.format(window.end()))
                    .append("\r\n");
        }
        calendar.append("END:VFREEBUSY\r\n");
        calendar.append("END:VCALENDAR\r\n");
        return calendar.toString();
    }

    private TimeRange timeRange(Report report) {
        if (report.rangeStart() == null || report.rangeEnd() == null) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "caldav-time-range-required",
                    "CalDAV " + report.kind().name().toLowerCase(Locale.ROOT)
                            + " REPORT requires a valid time-range.",
                    Map.of("module", "calendar", "operation", "caldav-report-" + report.kind().name().toLowerCase(Locale.ROOT)));
        }
        return new TimeRange(parseCalDavTime(report.rangeStart()), parseCalDavTime(report.rangeEnd()));
    }

    private OffsetDateTime parseCalDavTime(String value) {
        try {
            return LocalDateTime.parse(value.toUpperCase(Locale.ROOT), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))
                    .atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException exception) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "caldav-time-range-invalid",
                    "CalDAV REPORT time-range must use UTC basic date-time values.",
                    Map.of("module", "calendar", "operation", "caldav-report"));
        }
    }

    private void appendCollection(StringBuilder xml, String href, String displayName) {
        xml.append("  <d:response>\n")
                .append("    <d:href>").append(escapeXml(href)).append("</d:href>\n")
                .append("    <d:propstat>\n")
                .append("      <d:prop>\n")
                .append("        <d:displayname>").append(escapeXml(displayName)).append("</d:displayname>\n")
                .append("        <d:resourcetype><d:collection/><c:calendar/></d:resourcetype>\n")
                .append("        <d:current-user-principal><d:href>/caldav/principals/users/weave/</d:href></d:current-user-principal>\n")
                .append("        <c:calendar-home-set><d:href>/caldav/</d:href></c:calendar-home-set>\n")
                .append("        <c:supported-calendar-component-set><c:comp name=\"VEVENT\"/></c:supported-calendar-component-set>\n")
                .append("      </d:prop>\n")
                .append("      <d:status>HTTP/1.1 200 OK</d:status>\n")
                .append("    </d:propstat>\n")
                .append("  </d:response>\n");
    }

    private String calDavScopeHref(CalendarScopeResponse scope) {
        return CALDAV_ROOT + "/" + strictPathSegment(scope.id()) + "/";
    }

    private String calDavHref(CalendarEventResponse event) {
        CalendarScopeResponse scope = event.scope() == null ? CalendarScopeResponse.workspace() : event.scope();
        return CALDAV_ROOT
                + "/"
                + strictPathSegment(scope.id())
                + "/"
                + strictPathSegment(event.id())
                + ".ics";
    }

    private String calDavHref(CalDavEventResource event) {
        CalendarScopeResponse scope = event.scope() == null ? CalendarScopeResponse.workspace() : event.scope();
        return CALDAV_ROOT
                + "/"
                + strictPathSegment(scope.id())
                + "/"
                + strictPathSegment(event.eventId())
                + ".ics";
    }

    private String strictPathSegment(String value) {
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8)
                .replace(":", "%3A");
    }

    private boolean etagMatches(String currentEtag, String candidateHeader) {
        if (currentEtag == null || candidateHeader == null || candidateHeader.isBlank()) {
            return false;
        }
        String current = normalizeEtag(currentEtag);
        for (String candidate : candidateHeader.split(",")) {
            if (current.equals(normalizeEtag(candidate))) {
                return true;
            }
        }
        return false;
    }

    private String normalizeEtag(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.startsWith("W/")) {
            normalized = normalized.substring(2).trim();
        }
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return normalized;
    }

    private String safeFilename(String eventUid) {
        return eventUid.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String errorXml(String code, String message) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <d:error xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
                  <d:responsedescription>%s</d:responsedescription>
                  <weave-code>%s</weave-code>
                </d:error>
                """.formatted(escapeXml(message), escapeXml(code));
    }

    private String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private record TimeRange(OffsetDateTime from, OffsetDateTime to) {
    }

    private record CalendarEventReference(CalendarScopeResponse scope, String eventUid) {
    }
}

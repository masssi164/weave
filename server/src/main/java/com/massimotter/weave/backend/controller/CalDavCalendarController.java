package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.model.calendar.CalendarEventResponse;
import com.massimotter.weave.backend.model.calendar.CalendarScopeResponse;
import com.massimotter.weave.backend.model.calendar.CalendarScopesResponse;
import com.massimotter.weave.backend.service.CalendarFacadeService;
import com.massimotter.weave.backend.service.calendar.IcalendarMapper;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private static final Pattern TIME_RANGE = Pattern.compile(
            "time-range[^>]*\\sstart=[\"']([^\"']+)[\"'][^>]*\\send=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HREF = Pattern.compile("<[^:>/]*:?href[^>]*>([^<]+)</[^:>/]*:?href>",
            Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter CALDAV_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);

    private final CalendarFacadeService calendarFacadeService;
    private final IcalendarMapper icalendarMapper = new IcalendarMapper();

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
        String rawBody = requestBody(request);
        String body = rawBody.toLowerCase(Locale.ROOT);
        String reportKind = body.contains("free-busy-query")
                ? "free-busy-query"
                : body.contains("calendar-multiget")
                        ? "calendar-multiget"
                        : body.contains("sync-collection")
                                ? "sync-collection"
                                : "calendar-query";
        if (!body.contains("calendar-query")
                && !body.contains("calendar-multiget")
                && !body.contains("sync-collection")
                && !body.contains("free-busy-query")) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "caldav-report-unsupported",
                    "Only calendar-query, calendar-multiget, sync-collection, and free-busy-query REPORT bodies are recognized by the Weave CalDAV facade.",
                    Map.of("module", "calendar", "operation", "caldav-report"));
        }
        if ("calendar-multiget".equals(reportKind)) {
            return ResponseEntity.status(207)
                    .contentType(XML)
                    .header("DAV", "1, calendar-access")
                    .header("X-Weave-Projection", "caldav-calendar-data-plane")
                    .body(calendarMultigetMultistatus(rawBody));
        }
        if ("sync-collection".equals(reportKind)) {
            CalendarScopeResponse scope = scope(request);
            var events = calendarFacadeService.listCalDavEvents(scope, null, null).events();
            return ResponseEntity.status(207)
                    .contentType(XML)
                    .header("DAV", "1, calendar-access")
                    .header("Sync-Token", "weave-sync-" + System.currentTimeMillis())
                    .header("X-Weave-Projection", "caldav-calendar-data-plane")
                    .body(calendarQueryMultistatus(events, true));
        }
        CalendarScopeResponse scope = scope(request);
        TimeRange range = timeRange(body, reportKind);
        var events = calendarFacadeService.listCalDavEvents(scope, range.from(), range.to()).events();
        if ("free-busy-query".equals(reportKind)) {
            return ResponseEntity.ok()
                    .contentType(CALENDAR)
                    .header("X-Weave-Projection", "caldav-calendar-data-plane")
                    .body(freeBusyCalendar(events, range));
        }
        return ResponseEntity.status(207)
                .contentType(XML)
                .header("DAV", "1, calendar-access")
                .header("X-Weave-Projection", "caldav-calendar-data-plane")
                .body(calendarQueryMultistatus(events, false));
    }

    private ResponseEntity<byte[]> get(HttpServletRequest request, boolean headOnly) {
        String eventUid = eventUid(request);
        CalendarScopeResponse scope = scope(request);
        CalendarEventResponse event = calendarFacadeService.readCalDavEvent(eventUid, scope);
        String calendarData = icalendarMapper.toIcalendar(new IcalendarMapper.EventDraft(
                eventUid,
                event.title(),
                event.description(),
                event.startsAt(),
                event.endsAt(),
                event.timezone(),
                event.location(),
                event.allDay()));
        byte[] body = calendarData.getBytes(StandardCharsets.UTF_8);
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(CALENDAR)
                .contentLength(body.length)
                .eTag(event.etag())
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

    private String calendarMultigetMultistatus(String reportBody) {
        List<CalendarEventResponse> events = new ArrayList<>();
        Matcher matcher = HREF.matcher(reportBody);
        while (matcher.find()) {
            String href = matcher.group(1);
            CalendarEventReference reference = eventReferenceFromHref(href);
            if (reference != null && reference.eventUid() != null && !reference.eventUid().isBlank()) {
                events.add(calendarFacadeService.readCalDavEvent(reference.eventUid(), reference.scope()));
            }
        }
        return calendarQueryMultistatus(events, false);
    }

    private String calendarQueryMultistatus(Iterable<CalendarEventResponse> events, boolean includeSyncToken) {
        StringBuilder xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <d:multistatus xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
                """);
        if (includeSyncToken) {
            xml.append("  <d:sync-token>weave-sync-").append(System.currentTimeMillis()).append("</d:sync-token>\n");
        }
        for (CalendarEventResponse event : events) {
            String calendarData = icalendarMapper.toIcalendar(new IcalendarMapper.EventDraft(
                    event.id(),
                    event.title(),
                    event.description(),
                    event.startsAt(),
                    event.endsAt(),
                    event.timezone(),
                    event.location(),
                    event.allDay()));
            xml.append("  <d:response>\n")
                    .append("    <d:href>").append(escapeXml(calDavHref(event))).append("</d:href>\n")
                    .append("    <d:propstat>\n")
                    .append("      <d:prop>\n")
                    .append("        <d:getetag>").append(escapeXml(event.etag())).append("</d:getetag>\n")
                    .append("        <c:calendar-data>").append(escapeXml(calendarData)).append("</c:calendar-data>\n")
                    .append("      </d:prop>\n")
                    .append("      <d:status>HTTP/1.1 200 OK</d:status>\n")
                    .append("    </d:propstat>\n")
                    .append("  </d:response>\n");
        }
        xml.append("</d:multistatus>");
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

    private String freeBusyCalendar(Iterable<CalendarEventResponse> events, TimeRange range) {
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
        for (CalendarEventResponse event : events) {
            calendar.append("FREEBUSY:")
                    .append(CALDAV_TIME.format(event.startsAt()))
                    .append("/")
                    .append(CALDAV_TIME.format(event.endsAt()))
                    .append("\r\n");
        }
        calendar.append("END:VFREEBUSY\r\n");
        calendar.append("END:VCALENDAR\r\n");
        return calendar.toString();
    }

    private TimeRange timeRange(String body, String reportKind) {
        Matcher matcher = TIME_RANGE.matcher(body);
        if (!matcher.find()) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "caldav-time-range-required",
                    "CalDAV " + reportKind + " REPORT requires a valid time-range.",
                    Map.of("module", "calendar", "operation", "caldav-report-" + reportKind));
        }
        return new TimeRange(parseCalDavTime(matcher.group(1)), parseCalDavTime(matcher.group(2)));
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

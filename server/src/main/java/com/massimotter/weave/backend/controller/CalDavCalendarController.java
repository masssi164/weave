package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.model.calendar.CalendarScopeResponse;
import com.massimotter.weave.backend.model.calendar.CalendarScopesResponse;
import com.massimotter.weave.backend.service.CalendarFacadeService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
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

    private final CalendarFacadeService calendarFacadeService;

    public CalDavCalendarController(CalendarFacadeService calendarFacadeService) {
        this.calendarFacadeService = calendarFacadeService;
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

        CalendarScopesResponse scopes = calendarFacadeService.scopes();
        boolean includeChildren = "1".equals(depth);
        return ResponseEntity.status(207)
                .contentType(XML)
                .header("DAV", "1, calendar-access")
                .body(multistatus(includeChildren, scopes));
    }

    private ResponseEntity<String> report(HttpServletRequest request) {
        String body = requestBody(request).toLowerCase(Locale.ROOT);
        String reportKind = body.contains("free-busy-query") ? "free-busy-query" : "calendar-query";
        if (!body.contains("calendar-query") && !body.contains("free-busy-query")) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "caldav-report-unsupported",
                    "Only calendar-query and free-busy-query REPORT bodies are recognized by the Weave CalDAV skeleton.",
                    Map.of("module", "calendar", "operation", "caldav-report"));
        }
        return calDavError(calendarFacadeService.reportCalendarQueryNotReady(reportKind));
    }

    private ResponseEntity<byte[]> get(HttpServletRequest request, boolean headOnly) {
        String eventUid = eventUid(request);
        String calendarData = calendarFacadeService.readCalDavEventIcs(eventUid);
        byte[] body = calendarData.getBytes(StandardCharsets.UTF_8);
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(CALENDAR)
                .contentLength(body.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + safeFilename(eventUid) + ".ics\"");
        return builder.body(headOnly ? null : body);
    }

    private ResponseEntity<Void> put(HttpServletRequest request) {
        String eventUid = eventUid(request);
        var event = calendarFacadeService.putCalDavEventIcs(
                eventUid,
                requestBody(request),
                request.getHeader(HttpHeaders.IF_MATCH),
                request.getHeader(HttpHeaders.IF_NONE_MATCH));
        HttpStatus status = "*".equals(request.getHeader(HttpHeaders.IF_NONE_MATCH))
                ? HttpStatus.CREATED
                : HttpStatus.NO_CONTENT;
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status)
                .header(HttpHeaders.LOCATION, calDavHref(event.id()))
                .contentType(CALENDAR);
        if (event.etag() != null && !event.etag().isBlank()) {
            builder.eTag(event.etag());
        }
        return builder.build();
    }

    private ResponseEntity<Void> delete(HttpServletRequest request) {
        calendarFacadeService.deleteCalDavEventIcs(eventUid(request));
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

    private void appendCollection(StringBuilder xml, String href, String displayName) {
        xml.append("  <d:response>\n")
                .append("    <d:href>").append(escapeXml(href)).append("</d:href>\n")
                .append("    <d:propstat>\n")
                .append("      <d:prop>\n")
                .append("        <d:displayname>").append(escapeXml(displayName)).append("</d:displayname>\n")
                .append("        <d:resourcetype><d:collection/><c:calendar/></d:resourcetype>\n")
                .append("        <c:supported-calendar-component-set><c:comp name=\"VEVENT\"/></c:supported-calendar-component-set>\n")
                .append("      </d:prop>\n")
                .append("      <d:status>HTTP/1.1 200 OK</d:status>\n")
                .append("    </d:propstat>\n")
                .append("  </d:response>\n");
    }

    private String calDavScopeHref(CalendarScopeResponse scope) {
        return CALDAV_ROOT + "/" + UriUtils.encodePathSegment(scope.id(), StandardCharsets.UTF_8) + "/";
    }

    private String calDavHref(String eventId) {
        return CALDAV_ROOT + "/workspace/" + UriUtils.encodePathSegment(eventId, StandardCharsets.UTF_8) + ".ics";
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
}

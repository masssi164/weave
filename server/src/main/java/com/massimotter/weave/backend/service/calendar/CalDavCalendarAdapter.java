package com.massimotter.weave.backend.service.calendar;

import com.massimotter.weave.backend.config.CalendarCalDavProperties;
import com.massimotter.weave.backend.model.calendar.CalendarEventResponse;
import com.massimotter.weave.backend.model.calendar.CalendarScopeResponse;
import com.massimotter.weave.backend.model.calendar.CreateCalendarEventRequest;
import com.massimotter.weave.backend.model.calendar.UpdateCalendarEventRequest;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class CalDavCalendarAdapter implements CalendarAdapter {

    private static final int HTTP_MULTI_STATUS = 207;
    private static final int HTTP_NOT_FOUND = 404;
    private static final int HTTP_METHOD_NOT_ALLOWED = 405;
    private static final DateTimeFormatter CALDAV_TIME_RANGE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);

    private final CalendarCalDavProperties properties;
    private final HttpClient httpClient;
    private final IcalendarMapper mapper;

    public CalDavCalendarAdapter(CalendarCalDavProperties properties) {
        this(properties, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.requestTimeoutSeconds()))
                .build(), new IcalendarMapper());
    }

    CalDavCalendarAdapter(CalendarCalDavProperties properties, HttpClient httpClient, IcalendarMapper mapper) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.mapper = mapper;
    }

    @Override
    public List<CalendarEventResponse> list(CalendarPrincipal principal, OffsetDateTime from, OffsetDateTime to) {
        return list(principal, CalendarScopeResponse.workspace(), from, to);
    }

    @Override
    public List<CalendarEventResponse> list(
            CalendarPrincipal principal,
            CalendarScopeResponse scope,
            OffsetDateTime from,
            OffsetDateTime to) {
        ensureConfigured("list-events");
        CalendarScopeResponse resolvedScope = normalizeScope(scope);
        URI calendarUri = calendarCollectionUri(principal, resolvedScope);
        String body = calendarQuery(from, to);
        HttpRequest request = requestBuilder(calendarUri)
                .method("REPORT", HttpRequest.BodyPublishers.ofString(body))
                .header("Depth", "1")
                .header("Content-Type", "application/xml; charset=utf-8")
                .header("Accept", "application/xml, text/xml")
                .build();
        HttpResponse<String> response = send(request, "list-events");
        if (response.statusCode() == HTTP_NOT_FOUND) {
            ensureCalendarCollection(principal, resolvedScope);
            response = send(request, "list-events");
        }
        if (response.statusCode() != HTTP_MULTI_STATUS && !isSuccess(response.statusCode())) {
            throw mapStatus(response.statusCode(), "list-events", null);
        }
        return parseMultistatus(response.body());
    }

    @Override
    public CalendarEventResponse create(CalendarPrincipal principal, CreateCalendarEventRequest request) {
        ensureConfigured("create-event");
        CalendarScopeResponse scope = normalizeScope(request.scope());
        IcalendarMapper.EventDraft draft = mapper.draftFrom(request);
        String href = calendarHref(principal, scope, draft.uid() + ".ics");
        URI eventUri = eventUri(href);
        HttpRequest httpRequest = requestBuilder(eventUri)
                .PUT(HttpRequest.BodyPublishers.ofString(mapper.toIcalendar(draft)))
                .header("Content-Type", "text/calendar; charset=utf-8")
                .header("If-None-Match", "*")
                .build();
        HttpResponse<String> response = send(httpRequest, "create-event");
        if (response.statusCode() == HTTP_NOT_FOUND) {
            ensureCalendarCollection(principal, scope);
            response = send(httpRequest, "create-event");
        }
        if (!isSuccess(response.statusCode())) {
            throw mapStatus(response.statusCode(), "create-event", href);
        }
        return new CalendarEventResponse(
                opaqueId(href),
                draft.title(),
                draft.description(),
                draft.startsAt(),
                draft.endsAt(),
                draft.timezone(),
                draft.location(),
                draft.allDay(),
                response.headers().firstValue("ETag").orElse(null));
    }

    @Override
    public CalendarEventResponse read(CalendarPrincipal principal, String id) {
        return read(principal, CalendarScopeResponse.workspace(), id);
    }

    @Override
    public CalendarEventResponse read(CalendarPrincipal principal, CalendarScopeResponse scope, String id) {
        ensureConfigured("read-event");
        String href = hrefFromOpaqueId(id, principal, normalizeScope(scope));
        HttpResponse<String> existing = getEvent(href, "read-event");
        return mapper.toResponse(id, existing.headers().firstValue("ETag").orElse(null), existing.body());
    }

    @Override
    public CalendarEventResponse update(CalendarPrincipal principal, String id, UpdateCalendarEventRequest request) {
        return update(principal, CalendarScopeResponse.workspace(), id, request);
    }

    @Override
    public CalendarEventResponse update(
            CalendarPrincipal principal,
            CalendarScopeResponse scope,
            String id,
            UpdateCalendarEventRequest request) {
        ensureConfigured("update-event");
        String href = hrefFromOpaqueId(id, principal, normalizeScope(scope));
        URI eventUri = eventUri(href);
        HttpResponse<String> existing = getEvent(href, "update-event");

        IcalendarMapper.EventDraft merged = mapper.merge(mapper.parse(existing.body()), request);
        HttpRequest.Builder builder = requestBuilder(eventUri)
                .PUT(HttpRequest.BodyPublishers.ofString(mapper.toIcalendar(merged)))
                .header("Content-Type", "text/calendar; charset=utf-8");
        if (request.etag() != null && !request.etag().isBlank()) {
            builder.header("If-Match", request.etag());
        }
        HttpResponse<String> updated = send(builder.build(), "update-event");
        if (!isSuccess(updated.statusCode())) {
            throw mapStatus(updated.statusCode(), "update-event", href);
        }
        return new CalendarEventResponse(
                id,
                merged.title(),
                merged.description(),
                merged.startsAt(),
                merged.endsAt(),
                merged.timezone(),
                merged.location(),
                merged.allDay(),
                updated.headers().firstValue("ETag").orElse(null));
    }

    @Override
    public void delete(CalendarPrincipal principal, String id) {
        delete(principal, CalendarScopeResponse.workspace(), id);
    }

    @Override
    public void delete(CalendarPrincipal principal, CalendarScopeResponse scope, String id) {
        ensureConfigured("delete-event");
        String href = hrefFromOpaqueId(id, principal, normalizeScope(scope));
        HttpResponse<String> response = send(requestBuilder(eventUri(href))
                .DELETE()
                .build(), "delete-event");
        if (!isSuccess(response.statusCode())) {
            throw mapStatus(response.statusCode(), "delete-event", href);
        }
    }

    private List<CalendarEventResponse> parseMultistatus(String body) {
        try {
            Document document = parseXml(body);
            NodeList responseNodes = document.getElementsByTagNameNS("DAV:", "response");
            List<CalendarEventResponse> events = new ArrayList<>();
            for (int index = 0; index < responseNodes.getLength(); index++) {
                Element response = (Element) responseNodes.item(index);
                String href = firstText(response, "DAV:", "href");
                String calendarData = firstText(response, "urn:ietf:params:xml:ns:caldav", "calendar-data");
                if (href == null || calendarData == null || calendarData.isBlank()) {
                    continue;
                }
                String etag = firstText(response, "DAV:", "getetag");
                events.add(mapper.toResponse(opaqueId(pathFromHref(href)), etag, calendarData));
            }
            return events;
        } catch (CalendarAdapterException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CalendarAdapterException(
                    CalendarAdapterException.Type.INVALID_RESPONSE,
                    "CalDAV calendar-query response could not be parsed.",
                    Map.of("module", "calendar", "operation", "list-events"),
                    exception);
        }
    }

    private Document parseXml(String body) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(body.stripLeading())));
    }

    private String firstText(Element parent, String namespace, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS(namespace, localName);
        if (nodes.getLength() == 0) {
            return null;
        }
        Node node = nodes.item(0);
        return node == null ? null : node.getTextContent();
    }

    private String calendarQuery(OffsetDateTime from, OffsetDateTime to) {
        String timeRange = "";
        if (from != null && to != null) {
            timeRange = "<c:time-range start=\"" + CALDAV_TIME_RANGE_FORMAT.format(from) + "\" end=\""
                    + CALDAV_TIME_RANGE_FORMAT.format(to) + "\"/>";
        }
        return """
                <?xml version="1.0" encoding="utf-8" ?>
                <c:calendar-query xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
                  <d:prop>
                    <d:getetag />
                    <c:calendar-data />
                  </d:prop>
                  <c:filter>
                    <c:comp-filter name="VCALENDAR">
                      <c:comp-filter name="VEVENT">%s</c:comp-filter>
                    </c:comp-filter>
                  </c:filter>
                </c:calendar-query>
                """.formatted(timeRange);
    }

    private String mkCalendarBody() {
        return """
                <?xml version="1.0" encoding="utf-8" ?>
                <c:mkcalendar xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
                  <d:set>
                    <d:prop>
                      <d:displayname>Weave workspace calendar</d:displayname>
                      <c:supported-calendar-component-set>
                        <c:comp name="VEVENT" />
                      </c:supported-calendar-component-set>
                    </d:prop>
                  </d:set>
                </c:mkcalendar>
                """;
    }

    private HttpRequest.Builder requestBuilder(URI uri) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(properties.requestTimeoutSeconds()))
                .header("User-Agent", "Weave-Backend-CalDAV/0.1");
        if (properties.authMode() == CalendarCalDavProperties.AuthMode.BEARER) {
            builder.header("Authorization", "Bearer " + properties.backendToken());
        } else {
            String credentials = properties.backendUsername() + ":" + properties.backendToken();
            builder.header("Authorization", "Basic " + Base64.getEncoder()
                    .encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
        }
        return builder;
    }

    private HttpResponse<String> getEvent(String href, String operation) {
        HttpResponse<String> existing = send(requestBuilder(eventUri(href))
                .GET()
                .header("Accept", "text/calendar")
                .build(), operation);
        if (!isSuccess(existing.statusCode())) {
            throw mapStatus(existing.statusCode(), operation, href);
        }
        return existing;
    }

    private HttpResponse<String> send(HttpRequest request, String operation) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new CalendarAdapterException(
                    CalendarAdapterException.Type.DOWNSTREAM_UNAVAILABLE,
                    "CalDAV request failed.",
                    Map.of("module", "calendar", "operation", operation),
                    exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CalendarAdapterException(
                    CalendarAdapterException.Type.DOWNSTREAM_UNAVAILABLE,
                    "CalDAV request was interrupted.",
                    Map.of("module", "calendar", "operation", operation),
                    exception);
        }
    }

    private void ensureCalendarCollection(CalendarPrincipal principal, CalendarScopeResponse scope) {
        URI calendarUri = calendarCollectionUri(principal, scope);
        HttpRequest request = requestBuilder(calendarUri)
                .method("MKCALENDAR", HttpRequest.BodyPublishers.ofString(mkCalendarBody()))
                .header("Content-Type", "application/xml; charset=utf-8")
                .build();
        HttpResponse<String> response = send(request, "ensure-calendar-collection");
        if (isSuccess(response.statusCode())
                || response.statusCode() == HTTP_MULTI_STATUS
                || response.statusCode() == HTTP_METHOD_NOT_ALLOWED) {
            return;
        }
        throw mapStatus(response.statusCode(), "ensure-calendar-collection", calendarHref(principal, scope, ""));
    }

    private CalendarAdapterException mapStatus(int status, String operation, String href) {
        Map<String, Object> details = href == null
                ? Map.of("module", "calendar", "operation", operation, "downstreamStatus", status)
                : Map.of("module", "calendar", "operation", operation, "downstreamStatus", status, "href", href);
        if (status == 401 || status == 403) {
            return new CalendarAdapterException(
                    CalendarAdapterException.Type.AUTH_FAILED,
                    "CalDAV backend actor was not authorized.", details);
        }
        if (status == HTTP_NOT_FOUND) {
            return new CalendarAdapterException(
                    CalendarAdapterException.Type.NOT_FOUND,
                    "Calendar event was not found.", details);
        }
        if (status == 409 || status == 412 || status == 423) {
            return new CalendarAdapterException(
                    CalendarAdapterException.Type.CONFLICT,
                    "Calendar event update conflicted with downstream state.", details);
        }
        return new CalendarAdapterException(
                CalendarAdapterException.Type.DOWNSTREAM_UNAVAILABLE,
                "CalDAV downstream returned an unavailable response.", details);
    }

    private boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private void ensureConfigured(String operation) {
        if (!properties.isConfigured()) {
            throw new CalendarAdapterException(
                    CalendarAdapterException.Type.NOT_CONFIGURED,
                    "Calendar facade is available, but the downstream Nextcloud CalDAV adapter is not configured.",
                    Map.of(
                            "module", "calendar",
                            "operation", operation,
                            "calendarScope", properties.calendarScope(),
                            "privateUserTemplateAllowed", false));
        }
    }

    private URI calendarCollectionUri(CalendarPrincipal principal, CalendarScopeResponse scope) {
        return eventUri(calendarHref(principal, scope, ""));
    }

    private String calendarHref(CalendarPrincipal principal, CalendarScopeResponse scope, String eventFileName) {
        CalendarScopeResponse resolvedScope = normalizeScope(scope);
        String user = URLEncoder.encode(principal.nextcloudUserId(), StandardCharsets.UTF_8).replace("+", "%20");
        String path = properties.calendarPathTemplate().replace("{user}", user);
        path = applyScopePath(path, resolvedScope);
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (!path.endsWith("/")) {
            path = path + "/";
        }
        return path + eventFileName;
    }

    private String applyScopePath(String path, CalendarScopeResponse scope) {
        if (path.contains("{scopeId}")
                || path.contains("{scopeType}")
                || path.contains("{team}")
                || path.contains("{channel}")) {
            return path
                    .replace("{scopeId}", scopeSegment(scope))
                    .replace("{scopeType}", pathSegment(scope.type()))
                    .replace("{team}", pathSegment(scope.teamId()))
                    .replace("{channel}", pathSegment(scope.channelId()));
        }
        if ("workspace".equals(scope.type())) {
            return path;
        }
        String normalizedPath = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        int lastSlash = normalizedPath.lastIndexOf('/');
        String parent = lastSlash < 0 ? "" : normalizedPath.substring(0, lastSlash + 1);
        return parent + "weave-" + scopeSegment(scope) + "/";
    }

    private CalendarScopeResponse normalizeScope(CalendarScopeResponse scope) {
        return scope == null || scope.type() == null || scope.type().isBlank()
                ? CalendarScopeResponse.workspace()
                : scope;
    }

    private String scopeSegment(CalendarScopeResponse scope) {
        return switch (scope.type()) {
            case "team" -> "team-" + pathSegment(scope.teamId());
            case "channel" -> "channel-" + pathSegment(scope.channelId());
            default -> "workspace";
        };
    }

    private String pathSegment(String value) {
        if (value == null || value.isBlank()) {
            return "default";
        }
        String sanitized = value.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-");
        sanitized = sanitized.replaceAll("(^[.-]+|[.-]+$)", "");
        return sanitized.isBlank() ? "default" : sanitized;
    }

    private URI eventUri(String href) {
        String path = href.startsWith("/") ? href : "/" + href;
        return URI.create(stripTrailingSlash(properties.baseUrl())).resolve(path);
    }

    private String hrefFromOpaqueId(String id, CalendarPrincipal principal, CalendarScopeResponse scope) {
        try {
            String href = new String(Base64.getUrlDecoder().decode(id), StandardCharsets.UTF_8);
            String calendarHref = calendarHref(principal, scope, "");
            if (!href.startsWith("/") || href.contains("..") || !href.startsWith(calendarHref)) {
                throw invalidId();
            }
            return href;
        } catch (IllegalArgumentException exception) {
            throw invalidId();
        }
    }

    private CalendarAdapterException invalidId() {
        return new CalendarAdapterException(
                CalendarAdapterException.Type.INVALID_REQUEST,
                "Calendar event id is not a valid Weave calendar facade id.",
                Map.of("module", "calendar"));
    }

    private String opaqueId(String href) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(pathFromHref(href).getBytes(StandardCharsets.UTF_8));
    }

    private String pathFromHref(String href) {
        URI uri = URI.create(href);
        if (uri.isAbsolute()) {
            return uri.getRawPath();
        }
        return href;
    }

    private String stripTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}

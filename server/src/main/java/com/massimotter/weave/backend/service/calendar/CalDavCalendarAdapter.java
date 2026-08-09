package com.massimotter.weave.backend.service.calendar;

import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarChange;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarChangeSet;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarEvent;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarId;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarScope;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarWrite;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.EventId;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.EventVersion;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.FreeBusyWindow;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.WriteIntent;
import com.massimotter.weave.backend.calendar.port.CalendarProviderPort;
import com.massimotter.weave.backend.config.CalendarCalDavProperties;
import com.massimotter.weave.backend.portability.ProviderConformanceProfile;
import com.massimotter.weave.backend.portability.ProviderConformanceProfile.MappingClass;
import com.massimotter.weave.backend.portability.ProviderCapabilityProbeResult;
import com.massimotter.weave.backend.portability.ProviderCapabilityState;
import com.massimotter.weave.backend.portability.ProviderReadiness;
import com.massimotter.weave.backend.portability.RetryAfterParser;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class CalDavCalendarAdapter implements CalendarProviderPort {

    private static final int HTTP_MULTI_STATUS = 207;
    private static final int HTTP_NOT_FOUND = 404;
    private static final DateTimeFormatter CALDAV_TIME_RANGE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);

    private final CalendarCalDavProperties properties;
    private final HttpClient httpClient;
    private final IcalendarMapper mapper;
    private final CalendarOccurrenceEngine occurrenceEngine;

    public CalDavCalendarAdapter(CalendarCalDavProperties properties) {
        this(properties,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(properties.requestTimeoutSeconds())).build(),
                new IcalendarMapper(),
                new CalendarOccurrenceEngine(new Ical4jRecurrenceEngine()));
    }

    CalDavCalendarAdapter(CalendarCalDavProperties properties, HttpClient httpClient, IcalendarMapper mapper) {
        this(properties, httpClient, mapper, new CalendarOccurrenceEngine(new Ical4jRecurrenceEngine()));
    }

    CalDavCalendarAdapter(CalendarCalDavProperties properties, HttpClient httpClient, IcalendarMapper mapper, CalendarOccurrenceEngine occurrenceEngine) {
        this.properties = properties;
        this.httpClient = httpClient;
        this.mapper = mapper;
        this.occurrenceEngine = occurrenceEngine;
    }

    @Override public boolean configured() { return properties.isConfigured(); }

    @Override
    public ProviderReadiness readiness() {
        ProviderCapabilityProbeResult result = healthProbe();
        return result.state() == ProviderCapabilityState.AVAILABLE
                ? ProviderReadiness.ready(result.supportSafeCode())
                : ProviderReadiness.degraded(result.supportSafeCode());
    }

    @Override
    public ProviderCapabilityProbeResult healthProbe() {
        if (!configured()) return ProviderCapabilityProbeResult.unavailable("calendar-storage-not-configured");
        try {
            HttpRequest request = requestBuilder(calendarCollectionUri(new CalendarId("provider-health"), CalendarScope.workspace()))
                    .method("PROPFIND", HttpRequest.BodyPublishers.noBody())
                    .header("Depth", "0")
                    .header("Accept", "application/xml, text/xml")
                    .build();
            HttpResponse<String> response = send(request, "probe-calendar-root");
            if (response.statusCode() == HTTP_MULTI_STATUS || isSuccess(response.statusCode())) return ProviderCapabilityProbeResult.available("calendar-storage-ready");
            if (response.statusCode() == 429) return ProviderCapabilityProbeResult.degraded("calendar-storage-rate-limited",
                    RetryAfterParser.parse(response.headers().firstValue("Retry-After").orElse(null), Instant.now()));
            if (response.statusCode() == 401 || response.statusCode() == 403) return ProviderCapabilityProbeResult.unavailable("calendar-storage-auth-failed");
            if (response.statusCode() == HTTP_NOT_FOUND) return ProviderCapabilityProbeResult.unavailable("calendar-storage-root-missing");
            return ProviderCapabilityProbeResult.degraded("calendar-storage-unavailable");
        } catch (CalendarAdapterException exception) {
            return switch (exception.type()) {
                case NOT_CONFIGURED, AUTH_FAILED -> ProviderCapabilityProbeResult.unavailable("calendar-storage-unavailable");
                default -> ProviderCapabilityProbeResult.degraded("calendar-storage-unavailable");
            };
        }
    }

    @Override
    public ProviderConformanceProfile conformanceProfile() {
        return new ProviderConformanceProfile(
                "calendar",
                "nextcloud-caldav",
                Set.of("query", "read", "create", "update", "delete", "free-busy", "bounded-recurrence", "etag"),
                Map.of(
                        "event", MappingClass.PORTABLE,
                        "timezone", MappingClass.PORTABLE,
                        "recurrence", MappingClass.PORTABLE,
                        "attendee", MappingClass.PORTABLE,
                        "reminder", MappingClass.LOSSY,
                        "meetingLink", MappingClass.LOSSY,
                        "syncToken", MappingClass.MANUAL_REVIEW),
                true, true, true);
    }

    @Override
    public List<CalendarEvent> query(CalendarId calendarId, CalendarScope scope, Instant from, Instant to) {
        ensureConfigured("list-events");
        CalendarScope resolvedScope = normalizeScope(scope);
        URI calendarUri = calendarCollectionUri(calendarId, resolvedScope);
        HttpRequest request = requestBuilder(calendarUri)
                .method("REPORT", HttpRequest.BodyPublishers.ofString(calendarQuery(from, to)))
                .header("Depth", "1")
                .header("Content-Type", "application/xml; charset=utf-8")
                .header("Accept", "application/xml, text/xml")
                .build();
        HttpResponse<String> response = send(request, "list-events");
        if (response.statusCode() != HTTP_MULTI_STATUS && !isSuccess(response.statusCode())) throw mapStatus(response.statusCode(), "list-events", null);
        List<CalendarEvent> events = parseMultistatus(calendarId, resolvedScope, response.body());
        if (from == null || to == null) return events;
        return events.stream()
                .filter(event -> !occurrenceEngine.occurrences(event, from, to, properties.evaluationZone()).isEmpty())
                .toList();
    }

    @Override
    public CalendarEvent write(CalendarWrite write) {
        String operation = write.intent() == WriteIntent.CREATE ? "create-event" : "update-event";
        ensureConfigured(operation);
        CalendarEvent event = write.event();
        CalendarScope scope = normalizeScope(event.scope());
        String href = calendarHref(event.calendarId(), scope, eventFileName(event.id()));
        HttpRequest.Builder request = requestBuilder(eventUri(href))
                .PUT(HttpRequest.BodyPublishers.ofString(mapper.toIcalendar(event)))
                .header("Content-Type", "text/calendar; charset=utf-8");
        if (write.intent() == WriteIntent.CREATE) request.header("If-None-Match", "*");
        else if (write.expectedVersion().value() != null) request.header("If-Match", write.expectedVersion().value());
        HttpResponse<String> response = send(request.build(), operation);
        if (!isSuccess(response.statusCode())) throw mapStatus(response.statusCode(), operation, href);
        return withVersion(event, new EventVersion(response.headers().firstValue("ETag").orElse(null)), Instant.now());
    }

    @Override
    public CalendarEvent read(CalendarId calendarId, CalendarScope scope, EventId id) {
        ensureConfigured("read-event");
        CalendarScope resolvedScope = normalizeScope(scope);
        String href = calendarHref(calendarId, resolvedScope, eventFileName(id));
        HttpResponse<String> existing = getEvent(href, "read-event");
        CalendarEvent event = mapper.parse(calendarId, resolvedScope, new EventVersion(existing.headers().firstValue("ETag").orElse(null)), existing.body());
        if (!event.id().equals(id)) throw new CalendarAdapterException(CalendarAdapterException.Type.INVALID_RESPONSE,
                "CalDAV event UID did not match the requested canonical event id.",
                Map.of("module", "calendar", "operation", "read-event", "supportSafe", true));
        return event;
    }

    @Override
    public void delete(CalendarId calendarId, CalendarScope scope, EventId id, EventVersion expectedVersion) {
        ensureConfigured("delete-event");
        String href = calendarHref(calendarId, normalizeScope(scope), eventFileName(id));
        HttpRequest.Builder request = requestBuilder(eventUri(href)).DELETE();
        if (expectedVersion != null && expectedVersion.value() != null) request.header("If-Match", expectedVersion.value());
        HttpResponse<String> response = send(request.build(), "delete-event");
        if (!isSuccess(response.statusCode())) throw mapStatus(response.statusCode(), "delete-event", href);
    }

    @Override
    public List<FreeBusyWindow> freeBusy(CalendarId calendarId, CalendarScope scope, Instant from, Instant to) {
        return query(calendarId, scope, from, to).stream()
                .flatMap(event -> occurrenceEngine.occurrences(event, from, to, properties.evaluationZone()).stream())
                .map(occurrence -> new FreeBusyWindow(occurrence.start().toInstant(), occurrence.end().toInstant()))
                .sorted(java.util.Comparator.comparing(FreeBusyWindow::start))
                .toList();
    }

    @Override
    public CalendarChangeSet changes(CalendarId calendarId, CalendarScope scope, String sinceToken) {
        ensureConfigured("sync-events");
        CalendarScope resolvedScope = normalizeScope(scope);
        HttpRequest request = requestBuilder(calendarCollectionUri(calendarId, resolvedScope))
                .method("REPORT", HttpRequest.BodyPublishers.ofString(syncCollectionReport(sinceToken)))
                .header("Depth", "1")
                .header("Content-Type", "application/xml; charset=utf-8")
                .header("Accept", "application/xml, text/xml")
                .build();
        HttpResponse<String> response = send(request, "sync-events");
        if (response.statusCode() != HTTP_MULTI_STATUS && !isSuccess(response.statusCode())) throw mapStatus(response.statusCode(), "sync-events", null);
        return parseChanges(response.body());
    }

    private List<CalendarEvent> parseMultistatus(CalendarId calendarId, CalendarScope scope, String body) {
        try {
            Document document = parseXml(body);
            NodeList responseNodes = document.getElementsByTagNameNS("DAV:", "response");
            List<CalendarEvent> events = new ArrayList<>();
            for (int index = 0; index < responseNodes.getLength(); index++) {
                Element response = (Element) responseNodes.item(index);
                String href = firstText(response, "DAV:", "href");
                String calendarData = firstText(response, "urn:ietf:params:xml:ns:caldav", "calendar-data");
                if (href == null || calendarData == null || calendarData.isBlank()) continue;
                String etag = firstText(response, "DAV:", "getetag");
                events.add(mapper.parse(calendarId, scope, new EventVersion(etag), calendarData));
            }
            return events;
        } catch (CalendarAdapterException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CalendarAdapterException(CalendarAdapterException.Type.INVALID_RESPONSE,
                    "CalDAV calendar-query response could not be parsed.",
                    Map.of("module", "calendar", "operation", "list-events"), exception);
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

    private CalendarChangeSet parseChanges(String body) {
        try {
            Document document = parseXml(body);
            String syncToken = firstText(document.getDocumentElement(), "DAV:", "sync-token");
            if (syncToken == null || syncToken.isBlank()) throw new IllegalArgumentException("sync token is missing");
            NodeList responseNodes = document.getElementsByTagNameNS("DAV:", "response");
            List<CalendarChange> changes = new ArrayList<>();
            for (int index = 0; index < responseNodes.getLength(); index++) {
                Element response = (Element) responseNodes.item(index);
                String href = firstText(response, "DAV:", "href");
                if (href == null || href.isBlank() || href.endsWith("/")) continue;
                String status = firstText(response, "DAV:", "status");
                String etag = firstText(response, "DAV:", "getetag");
                changes.add(new CalendarChange(syncToken.trim(), eventIdFromHref(href), status != null && status.contains(" 404 "), new EventVersion(etag)));
            }
            return new CalendarChangeSet(syncToken.trim(), changes);
        } catch (CalendarAdapterException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CalendarAdapterException(CalendarAdapterException.Type.INVALID_RESPONSE,
                    "CalDAV sync-collection response could not be parsed.",
                    Map.of("module", "calendar", "operation", "sync-events"), exception);
        }
    }

    private String firstText(Element parent, String namespace, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS(namespace, localName);
        if (nodes.getLength() == 0) return null;
        Node node = nodes.item(0);
        return node == null ? null : node.getTextContent();
    }

    private String calendarQuery(Instant from, Instant to) {
        String timeRange = "";
        if (from != null && to != null) timeRange = "<c:time-range start=\"" + CALDAV_TIME_RANGE_FORMAT.format(from) + "\" end=\"" + CALDAV_TIME_RANGE_FORMAT.format(to) + "\"/>";
        return """
                <?xml version="1.0" encoding="utf-8" ?>
                <c:calendar-query xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
                  <d:prop><d:getetag /><c:calendar-data /></d:prop>
                  <c:filter><c:comp-filter name="VCALENDAR"><c:comp-filter name="VEVENT">%s</c:comp-filter></c:comp-filter></c:filter>
                </c:calendar-query>
                """.formatted(timeRange);
    }

    private String syncCollectionReport(String sinceToken) {
        String token = sinceToken == null || sinceToken.isBlank() ? "<d:sync-token/>" : "<d:sync-token>" + xmlEscape(sinceToken.trim()) + "</d:sync-token>";
        return """
                <?xml version="1.0" encoding="utf-8" ?>
                <d:sync-collection xmlns:d="DAV:">%s<d:sync-level>1</d:sync-level><d:prop><d:getetag /></d:prop></d:sync-collection>
                """.formatted(token);
    }

    private HttpRequest.Builder requestBuilder(URI uri) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(properties.requestTimeoutSeconds()))
                .header("User-Agent", "Weave-Backend-CalDAV/0.1");
        if (properties.authMode() == CalendarCalDavProperties.AuthMode.BEARER) builder.header("Authorization", "Bearer " + properties.backendToken());
        else {
            String credentials = properties.backendUsername() + ":" + properties.backendToken();
            builder.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
        }
        return builder;
    }

    private HttpResponse<String> getEvent(String href, String operation) {
        HttpResponse<String> existing = send(requestBuilder(eventUri(href)).GET().header("Accept", "text/calendar").build(), operation);
        if (!isSuccess(existing.statusCode())) throw mapStatus(existing.statusCode(), operation, href);
        return existing;
    }

    private HttpResponse<String> send(HttpRequest request, String operation) {
        try { return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)); }
        catch (IOException exception) { throw new CalendarAdapterException(CalendarAdapterException.Type.DOWNSTREAM_UNAVAILABLE, "CalDAV request failed.", Map.of("module", "calendar", "operation", operation), exception); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new CalendarAdapterException(CalendarAdapterException.Type.DOWNSTREAM_UNAVAILABLE, "CalDAV request was interrupted.", Map.of("module", "calendar", "operation", operation), exception); }
    }

    private CalendarAdapterException mapStatus(int status, String operation, String href) {
        Map<String, Object> details = Map.of("module", "calendar", "operation", operation, "downstreamStatus", status, "providerPathRedacted", href != null);
        if (status == 401 || status == 403) return new CalendarAdapterException(CalendarAdapterException.Type.AUTH_FAILED, "CalDAV backend actor was not authorized.", details);
        if (status == HTTP_NOT_FOUND) return new CalendarAdapterException(CalendarAdapterException.Type.NOT_FOUND, "Calendar event was not found.", details);
        if (status == 409 || status == 412 || status == 423) return new CalendarAdapterException(CalendarAdapterException.Type.CONFLICT, "Calendar event update conflicted with downstream state.", details);
        return new CalendarAdapterException(CalendarAdapterException.Type.DOWNSTREAM_UNAVAILABLE, "CalDAV downstream returned an unavailable response.", details);
    }

    private boolean isSuccess(int statusCode) { return statusCode >= 200 && statusCode < 300; }

    private void ensureConfigured(String operation) {
        if (!properties.isConfigured()) throw new CalendarAdapterException(CalendarAdapterException.Type.NOT_CONFIGURED,
                "Calendar provider is not configured.", Map.of("module", "calendar", "operation", operation, "calendarScope", properties.calendarScope(), "privateUserTemplateAllowed", false));
    }

    private URI calendarCollectionUri(CalendarId calendarId, CalendarScope scope) { return eventUri(calendarHref(calendarId, scope, "")); }

    private String calendarHref(CalendarId calendarId, CalendarScope scope, String eventFileName) {
        CalendarScope resolvedScope = normalizeScope(scope);
        String user = URLEncoder.encode(calendarId.value(), StandardCharsets.UTF_8).replace("+", "%20");
        String path = properties.calendarPathTemplate().replace("{user}", user);
        path = applyScopePath(path, resolvedScope);
        if (!path.startsWith("/")) path = "/" + path;
        if (!path.endsWith("/")) path = path + "/";
        return path + eventFileName;
    }

    private String applyScopePath(String path, CalendarScope scope) {
        if (path.contains("{scopeId}") || path.contains("{scopeType}") || path.contains("{team}") || path.contains("{channel}")) {
            return path.replace("{scopeId}", scopeSegment(scope)).replace("{scopeType}", pathSegment(scope.type().name())).replace("{team}", pathSegment(scope.teamId())).replace("{channel}", pathSegment(scope.channelId()));
        }
        if (scope.type() == com.massimotter.weave.backend.calendar.domain.CalendarDomain.ScopeType.WORKSPACE) return path;
        String normalizedPath = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        int lastSlash = normalizedPath.lastIndexOf('/');
        String parent = lastSlash < 0 ? "" : normalizedPath.substring(0, lastSlash + 1);
        return parent + "weave-" + scopeSegment(scope) + "/";
    }

    private CalendarScope normalizeScope(CalendarScope scope) { return scope == null ? CalendarScope.workspace() : scope; }
    private String scopeSegment(CalendarScope scope) { return switch (scope.type()) { case TEAM -> "team-" + pathSegment(scope.teamId()); case CHANNEL -> "channel-" + pathSegment(scope.channelId()); case WORKSPACE -> "workspace"; }; }
    private String pathSegment(String value) { if (value == null || value.isBlank()) return "default"; String sanitized = value.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-").replaceAll("(^[.-]+|[.-]+$)", ""); return sanitized.isBlank() ? "default" : sanitized; }
    private URI eventUri(String href) { String path = href.startsWith("/") ? href : "/" + href; return URI.create(stripTrailingSlash(properties.baseUrl())).resolve(path); }
    private String eventFileName(EventId id) { return URLEncoder.encode(id.value(), StandardCharsets.UTF_8).replace("+", "%20") + ".ics"; }

    private EventId eventIdFromHref(String href) {
        String path = URI.create(href).getRawPath();
        String filename = path.substring(path.lastIndexOf('/') + 1);
        String decoded = URLDecoder.decode(filename, StandardCharsets.UTF_8);
        if (decoded.endsWith(".ics")) decoded = decoded.substring(0, decoded.length() - 4);
        return new EventId(decoded);
    }

    private String xmlEscape(String value) { return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;"); }

    private CalendarEvent withVersion(CalendarEvent event, EventVersion version, Instant updatedAt) {
        return new CalendarEvent(event.calendarId(), event.id(), event.scope(), event.title(), event.description(), event.startValue(), event.endValue(), event.location(), event.attendees(), event.recurrence(), event.overrides(), version, updatedAt);
    }

    private String stripTrailingSlash(String value) { return value.endsWith("/") ? value.substring(0, value.length() - 1) : value; }
}

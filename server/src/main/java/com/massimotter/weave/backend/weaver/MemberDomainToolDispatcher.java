package com.massimotter.weave.backend.weaver;

import com.massimotter.weave.backend.chat.ChatDomainFacadeService;
import com.massimotter.weave.backend.chat.domain.ChatMessage;
import com.massimotter.weave.backend.model.calendar.CalendarEventResponse;
import com.massimotter.weave.backend.model.calendar.CalendarEventsResponse;
import com.massimotter.weave.backend.model.calendar.CalendarScopeResponse;
import com.massimotter.weave.backend.model.calendar.CreateCalendarEventRequest;
import com.massimotter.weave.backend.model.files.FileItemResponse;
import com.massimotter.weave.backend.model.files.FileListResponse;
import com.massimotter.weave.backend.service.CalendarFacadeService;
import com.massimotter.weave.backend.service.FilesFacadeService;
import com.massimotter.weave.backend.service.files.FilePathCodec;
import com.massimotter.weave.contract.mcp.MemberMcpToolResultProjections;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

@Service
public class MemberDomainToolDispatcher {

    private static final int DEFAULT_FILES_LIMIT = 25;

    private final FilesFacadeService filesFacadeService;
    private final CalendarFacadeService calendarFacadeService;
    private final ChatDomainFacadeService chatDomainFacadeService;

    public MemberDomainToolDispatcher(
            FilesFacadeService filesFacadeService,
            CalendarFacadeService calendarFacadeService,
            ChatDomainFacadeService chatDomainFacadeService) {
        this.filesFacadeService = filesFacadeService;
        this.calendarFacadeService = calendarFacadeService;
        this.chatDomainFacadeService = chatDomainFacadeService;
    }

    public Map<String, Object> dispatch(Jwt jwt, String toolName, Map<String, Object> arguments) {
        Map<String, Object> safeArguments = arguments == null ? Map.of() : arguments;
        try {
            return switch (toolName) {
                case "files.search" -> filesSearch(safeArguments);
                case "files.read" -> filesRead(safeArguments);
                case "calendar.search_events" -> calendarSearchEvents(safeArguments);
                case "calendar.create_event" -> calendarCreateEvent(safeArguments);
                case "chat.send_message" -> chatSendMessage(jwt, safeArguments);
                default -> MemberMcpToolResultProjections.blocked("member_tool_dispatch_not_implemented");
            };
        } catch (RuntimeException exception) {
            return MemberMcpToolResultProjections.blocked("member_domain_facade_unavailable");
        }
    }

    private Map<String, Object> filesSearch(Map<String, Object> arguments) {
        String query = text(arguments.get("query"), "");
        String path = productPath(arguments.get("path"), "/");
        int limit = limit(arguments.get("limit"), DEFAULT_FILES_LIMIT);
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        FileListResponse listing = filesFacadeService.list(path);
        List<Map<String, Object>> items = listing.items().stream()
                .filter(item -> query.isBlank() || item.name().toLowerCase(Locale.ROOT).contains(normalizedQuery))
                .sorted(Comparator.comparing(FileItemResponse::path))
                .limit(limit)
                .map(this::fileItemProjection)
                .toList();
        return MemberMcpToolResultProjections.filesSearch(items, listing.path(), query);
    }

    private Map<String, Object> filesRead(Map<String, Object> arguments) {
        Optional<String> path = pathFromFileRef(arguments.get("fileRef"));
        if (path.isEmpty()) {
            return MemberMcpToolResultProjections.blocked("files_file_ref_requires_weave_webdav_facade_path");
        }
        String productPath = path.get();
        FileListResponse listing = filesFacadeService.list(parentPath(productPath));
        return listing.items().stream()
                .filter(item -> FilePathCodec.normalizeProductPath(item.path()).equals(productPath))
                .findFirst()
                .map(item -> MemberMcpToolResultProjections.filesReadMetadata(fileItemProjection(item), "file:" + productPath))
                .orElseGet(() -> MemberMcpToolResultProjections.blocked("files_item_not_found_in_weave_webdav_facade"));
    }

    private Map<String, Object> calendarSearchEvents(Map<String, Object> arguments) {
        Optional<OffsetDateTime> requestedFrom = optionalOffsetDateTime(arguments.get("from"));
        Optional<OffsetDateTime> requestedTo = optionalOffsetDateTime(arguments.get("to"));
        Optional<CalendarScopeResponse> scope = calendarScope(arguments.get("calendarRef"), true);
        if (invalidDate(arguments.get("from"), requestedFrom)
                || invalidDate(arguments.get("to"), requestedTo)
                || scope.isEmpty()) {
            return MemberMcpToolResultProjections.blocked("calendar_search_requires_valid_range_and_calendar_ref");
        }
        OffsetDateTime from = requestedFrom.orElseGet(() -> OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));
        OffsetDateTime to = requestedTo.orElseGet(() -> from.plusDays(14));
        if (!to.isAfter(from)) {
            return MemberMcpToolResultProjections.blocked("calendar_search_requires_valid_range_and_calendar_ref");
        }
        String query = text(arguments.get("query"), "").toLowerCase(Locale.ROOT);
        int limit = limit(arguments.get("limit"), DEFAULT_FILES_LIMIT);
        CalendarEventsResponse response = calendarFacadeService.listCalDavEvents(scope.get(), from, to);
        List<Map<String, Object>> events = response.events().stream()
                .filter(event -> query.isBlank() || calendarSearchText(event).contains(query))
                .sorted(Comparator.comparing(CalendarEventResponse::startsAt))
                .limit(limit)
                .map(this::calendarEventProjection)
                .toList();
        return MemberMcpToolResultProjections.calendarSearchEvents(
                events,
                calendarScopeProjection(response.scope()),
                calendarRef(response.scope()));
    }

    private Map<String, Object> calendarCreateEvent(Map<String, Object> arguments) {
        String title = text(arguments.get("title"), "");
        Optional<OffsetDateTime> startsAt = optionalOffsetDateTime(arguments.get("startsAt"));
        Optional<OffsetDateTime> requestedEnd = optionalOffsetDateTime(arguments.get("endsAt"));
        Optional<CalendarScopeResponse> scope = calendarScope(arguments.get("calendarRef"), false);
        String timezone = text(arguments.get("timezone"), "UTC");
        if (title.isBlank()
                || title.length() > 255
                || startsAt.isEmpty()
                || invalidDate(arguments.get("endsAt"), requestedEnd)
                || scope.isEmpty()
                || !validTimezone(timezone)) {
            return MemberMcpToolResultProjections.blocked("calendar_create_requires_valid_title_time_and_calendar_ref");
        }
        OffsetDateTime endsAt = requestedEnd.orElseGet(() -> startsAt.get().plusHours(1));
        if (!endsAt.isAfter(startsAt.get())) {
            return MemberMcpToolResultProjections.blocked("calendar_create_requires_valid_title_time_and_calendar_ref");
        }
        CalendarEventResponse event = calendarFacadeService.create(new CreateCalendarEventRequest(
                title,
                text(arguments.get("description"), ""),
                startsAt.get(),
                endsAt,
                timezone,
                text(arguments.get("location"), ""),
                Boolean.TRUE.equals(arguments.get("allDay")),
                scope.get()));
        return MemberMcpToolResultProjections.calendarCreateEvent(
                calendarEventProjection(event),
                eventRef(event),
                calendarRef(event.scope()));
    }

    private Map<String, Object> chatSendMessage(Jwt jwt, Map<String, Object> arguments) {
        Optional<String> conversationId = conversationId(arguments.get("threadRef"));
        String body = text(arguments.get("body"), "");
        String idempotencyKey = text(arguments.get("idempotencyKey"), "");
        if (jwt == null || conversationId.isEmpty() || body.isBlank() || idempotencyKey.isBlank()) {
            return MemberMcpToolResultProjections.blocked("chat_send_requires_canonical_thread_body_and_idempotency_key");
        }
        ChatMessage message = chatDomainFacadeService.sendMessage(
                conversationId.get(),
                idempotencyKey,
                body,
                jwt);
        return MemberMcpToolResultProjections.chatSendMessage(
                message.conversationId(),
                message.messageId(),
                message.deliveryState(),
                message.sentAt());
    }

    private Optional<OffsetDateTime> optionalOffsetDateTime(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(OffsetDateTime.parse(text));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private String text(Object value, String fallback) {
        return value instanceof String text && !text.isBlank() ? text : fallback;
    }

    private String productPath(Object value, String fallback) {
        return FilePathCodec.normalizeProductPath(text(value, fallback));
    }

    private Optional<String> pathFromFileRef(Object value) {
        if (!(value instanceof String fileRef) || fileRef.isBlank() || !fileRef.startsWith("file:")) {
            return Optional.empty();
        }
        String suffix = fileRef.substring("file:".length());
        if (!suffix.startsWith("/")) {
            return Optional.empty();
        }
        return Optional.of(FilePathCodec.normalizeProductPath(suffix));
    }

    private Optional<String> conversationId(Object value) {
        if (!(value instanceof String threadRef) || threadRef.isBlank()) {
            return Optional.empty();
        }
        for (String prefix : List.of("thread:", "conversation:")) {
            if (threadRef.startsWith(prefix) && threadRef.length() > prefix.length()) {
                String candidate = threadRef.substring(prefix.length()).trim();
                if (candidate.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,199}")) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }

    private Optional<CalendarScopeResponse> calendarScope(Object value, boolean defaultWorkspace) {
        if (value == null && defaultWorkspace) {
            return Optional.of(CalendarScopeResponse.workspace());
        }
        if (!(value instanceof String calendarRef) || calendarRef.isBlank()) {
            return Optional.empty();
        }
        if (calendarRef.equals("calendar:workspace")) {
            return Optional.of(CalendarScopeResponse.workspace());
        }
        if (calendarRef.startsWith("calendar:team:")) {
            String teamId = calendarRef.substring("calendar:team:".length());
            return canonicalId(teamId)
                    ? Optional.of(CalendarScopeResponse.team(teamId, "Team " + teamId + " calendar"))
                    : Optional.empty();
        }
        if (calendarRef.startsWith("calendar:channel:")) {
            String[] ids = calendarRef.substring("calendar:channel:".length()).split(":", 2);
            return ids.length == 2 && canonicalId(ids[0]) && canonicalId(ids[1])
                    ? Optional.of(CalendarScopeResponse.channel(ids[0], ids[1], "Channel " + ids[1] + " calendar"))
                    : Optional.empty();
        }
        return Optional.empty();
    }

    private boolean canonicalId(String value) {
        return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,99}");
    }

    private boolean invalidDate(Object value, Optional<OffsetDateTime> parsed) {
        return value != null && parsed.isEmpty();
    }

    private boolean validTimezone(String timezone) {
        try {
            ZoneId.of(timezone);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private String calendarSearchText(CalendarEventResponse event) {
        return (text(event.title(), "") + " " + text(event.description(), ""))
                .toLowerCase(Locale.ROOT);
    }

    private Map<String, Object> calendarEventProjection(CalendarEventResponse event) {
        Map<String, Object> projection = new LinkedHashMap<>();
        projection.put("eventRef", eventRef(event));
        projection.put("calendarRef", calendarRef(event.scope()));
        projection.put("title", event.title());
        projection.put("description", text(event.description(), ""));
        projection.put("startsAt", event.startsAt().toString());
        projection.put("endsAt", event.endsAt().toString());
        projection.put("timezone", event.timezone());
        projection.put("location", text(event.location(), ""));
        projection.put("allDay", event.allDay());
        projection.put("etag", text(event.etag(), ""));
        return Map.copyOf(projection);
    }

    private Map<String, Object> calendarScopeProjection(CalendarScopeResponse scope) {
        return Map.of(
                "calendarRef", calendarRef(scope),
                "type", scope.type(),
                "contextRef", "space:" + scope.contextId());
    }

    private String eventRef(CalendarEventResponse event) {
        return event.id().startsWith("event:") ? event.id() : "event:" + event.id();
    }

    private String calendarRef(CalendarScopeResponse scope) {
        return switch (scope.type()) {
            case "team" -> "calendar:team:" + scope.teamId();
            case "channel" -> "calendar:channel:" + scope.teamId() + ":" + scope.channelId();
            default -> "calendar:workspace";
        };
    }

    private String parentPath(String path) {
        int index = path.lastIndexOf('/');
        if (index <= 0) {
            return "/";
        }
        return path.substring(0, index);
    }

    private int limit(Object value, int fallback) {
        if (value instanceof Number number) {
            int requested = number.intValue();
            return Math.max(1, Math.min(requested, 100));
        }
        return fallback;
    }

    private Map<String, Object> fileItemProjection(FileItemResponse item) {
        String path = FilePathCodec.normalizeProductPath(item.path());
        Map<String, Object> projection = new LinkedHashMap<>();
        projection.put("fileRef", "file:" + path);
        projection.put("name", item.name());
        projection.put("path", path);
        projection.put("kind", "folder".equals(item.type()) ? "folder" : "file");
        projection.put("size", item.size() == null ? -1L : item.size());
        projection.put("sizeKnown", item.size() != null);
        projection.put("downloadable", item.downloadable());
        projection.put("webDavHref", davHref(path));
        return Map.copyOf(projection);
    }

    private String davHref(String path) {
        String normalized = FilePathCodec.normalizeProductPath(path);
        if ("/".equals(normalized)) {
            return "/dav/files";
        }
        StringBuilder href = new StringBuilder("/dav/files");
        for (String segment : normalized.substring(1).split("/")) {
            href.append('/').append(UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8));
        }
        return href.toString();
    }
}

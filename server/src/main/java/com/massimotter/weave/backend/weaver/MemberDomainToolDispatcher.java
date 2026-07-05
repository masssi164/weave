package com.massimotter.weave.backend.weaver;

import com.massimotter.weave.backend.model.calendar.CalendarEventResponse;
import com.massimotter.weave.backend.model.calendar.CalendarEventsResponse;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

@Service
public class MemberDomainToolDispatcher {

    private static final int DEFAULT_FILES_LIMIT = 25;

    private final FilesFacadeService filesFacadeService;
    private final CalendarFacadeService calendarFacadeService;

    public MemberDomainToolDispatcher(FilesFacadeService filesFacadeService, CalendarFacadeService calendarFacadeService) {
        this.filesFacadeService = filesFacadeService;
        this.calendarFacadeService = calendarFacadeService;
    }

    public Map<String, Object> dispatch(String toolName, Map<String, Object> arguments) {
        Map<String, Object> safeArguments = arguments == null ? Map.of() : arguments;
        return switch (toolName) {
            case "files.search" -> filesSearch(safeArguments);
            case "files.read" -> filesRead(safeArguments);
            case "calendar.search_events" -> calendarSearchEvents(safeArguments);
            case "calendar.create_event" -> calendarCreateEvent(safeArguments);
            default -> MemberMcpToolResultProjections.blocked("member_tool_dispatch_not_implemented");
        };
    }

    private Map<String, Object> filesSearch(Map<String, Object> arguments) {
        String query = text(arguments.get("query"), "");
        String path = productPath(arguments.get("path"), "/");
        int limit = limit(arguments.get("limit"), DEFAULT_FILES_LIMIT);
        FileListResponse listing = filesFacadeService.list(path);
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
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
        OffsetDateTime from = offsetDateTime(arguments.get("from"), OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));
        OffsetDateTime to = offsetDateTime(arguments.get("to"), from.plusDays(14));
        CalendarEventsResponse response = calendarFacadeService.list(from, to);
        return MemberMcpToolResultProjections.calendarSearchEvents(response.events(), response.scope(), response.scope().id());
    }

    private Map<String, Object> calendarCreateEvent(Map<String, Object> arguments) {
        OffsetDateTime startsAt = offsetDateTime(arguments.get("startsAt"), OffsetDateTime.now(ZoneOffset.UTC).plusHours(1));
        OffsetDateTime endsAt = offsetDateTime(arguments.get("endsAt"), startsAt.plusHours(1));
        CalendarEventResponse event = calendarFacadeService.create(new CreateCalendarEventRequest(
                text(arguments.get("title"), "Weaver-created event"),
                text(arguments.get("description"), "Created through governed Weaver MCP bridge."),
                startsAt,
                endsAt,
                text(arguments.get("timezone"), "UTC"),
                text(arguments.get("location"), ""),
                Boolean.TRUE.equals(arguments.get("allDay"))));
        return MemberMcpToolResultProjections.calendarCreateEvent(event, event.id(), event.scope().id());
    }

    private OffsetDateTime offsetDateTime(Object value, OffsetDateTime fallback) {
        if (value instanceof String text && !text.isBlank()) {
            return OffsetDateTime.parse(text);
        }
        return fallback;
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
        projection.put("size", item.size());
        projection.put("downloadable", item.downloadable());
        projection.put("webDavHref", webDavHref(path));
        return projection;
    }

    private String webDavHref(String path) {
        if ("/".equals(path)) {
            return "/dav/files";
        }
        String encodedPath = List.of(path.substring(1).split("/")).stream()
                .map(segment -> UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8))
                .reduce((left, right) -> left + "/" + right)
                .orElse("");
        return "/dav/files/" + encodedPath;
    }
}

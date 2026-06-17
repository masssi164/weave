package com.massimotter.weave.backend.weaver;

import com.massimotter.weave.backend.model.calendar.CalendarEventResponse;
import com.massimotter.weave.backend.model.calendar.CalendarEventsResponse;
import com.massimotter.weave.backend.model.calendar.CreateCalendarEventRequest;
import com.massimotter.weave.backend.service.CalendarFacadeService;
import com.massimotter.weave.contract.mcp.MemberMcpToolResultProjections;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MemberDomainToolDispatcher {

    private final CalendarFacadeService calendarFacadeService;

    public MemberDomainToolDispatcher(CalendarFacadeService calendarFacadeService) {
        this.calendarFacadeService = calendarFacadeService;
    }

    public Map<String, Object> dispatch(String toolName, Map<String, Object> arguments) {
        Map<String, Object> safeArguments = arguments == null ? Map.of() : arguments;
        return switch (toolName) {
            case "calendar.search_events" -> calendarSearchEvents(safeArguments);
            case "calendar.create_event" -> calendarCreateEvent(safeArguments);
            default -> MemberMcpToolResultProjections.blocked("member_tool_dispatch_not_implemented");
        };
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
}

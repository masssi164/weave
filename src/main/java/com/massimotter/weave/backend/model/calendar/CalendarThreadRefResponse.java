package com.massimotter.weave.backend.model.calendar;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Safe event thread/context metadata for a calendar event. Does not expose Matrix message contents or provider secrets.")
public record CalendarThreadRefResponse(
        @Schema(description = "Reference kind exposed by the calendar facade.", example = "context")
        String kind,
        @Schema(description = "Durable Weave Context/Space identifier for event discussion and follow-up work.", example = "channel-engineering-general")
        String contextId,
        @Schema(description = "Channel projection identifier when the event belongs to a channel scope.", example = "engineering-general")
        String channelId,
        @Schema(description = "Matrix room id when a safe room mapping is available. Null when unavailable or unsafe to expose.")
        String matrixRoomId,
        @Schema(description = "Matrix thread id when a safe thread mapping is available. Null when unavailable or unsafe to expose.")
        String matrixThreadId,
        @Schema(description = "Board task ids linked to this event when available.")
        List<String> boardTaskIds) {

    public CalendarThreadRefResponse {
        kind = kind == null || kind.isBlank() ? "context" : kind.trim();
        boardTaskIds = boardTaskIds == null ? List.of() : List.copyOf(boardTaskIds);
    }

    public static CalendarThreadRefResponse forScope(CalendarScopeResponse scope) {
        CalendarScopeResponse normalizedScope = scope == null ? CalendarScopeResponse.workspace() : scope;
        return new CalendarThreadRefResponse(
                "context",
                normalizedScope.contextId(),
                "channel".equals(normalizedScope.type()) ? normalizedScope.channelId() : null,
                null,
                null,
                List.of());
    }
}

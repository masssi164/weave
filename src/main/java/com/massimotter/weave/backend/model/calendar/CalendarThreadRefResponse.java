package com.massimotter.weave.backend.model.calendar;

import io.swagger.v3.oas.annotations.media.Schema;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Schema(description = "Safe event thread/context metadata for a calendar event. Does not expose Matrix message contents or provider secrets.")
public record CalendarThreadRefResponse(
        @Schema(description = "Reference kind exposed by the calendar facade.", example = "context")
        String kind,
        @Schema(description = "Durable Weave Context/Space identifier for event discussion and follow-up work.", example = "channel-engineering-general")
        String contextId,
        @Schema(description = "Stable Weave meeting-thread identifier for this calendar event.", example = "meeting:channel-engineering-general:7f3c6f3e1b9a")
        String meetingThreadId,
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
        meetingThreadId = meetingThreadId == null || meetingThreadId.isBlank()
                ? stableMeetingThreadId(contextId, null)
                : meetingThreadId.trim();
        boardTaskIds = boardTaskIds == null ? List.of() : List.copyOf(boardTaskIds);
    }

    public static CalendarThreadRefResponse forScope(CalendarScopeResponse scope) {
        return forEvent(scope, null);
    }

    public static CalendarThreadRefResponse forEvent(CalendarScopeResponse scope, String eventId) {
        CalendarScopeResponse normalizedScope = scope == null ? CalendarScopeResponse.workspace() : scope;
        return new CalendarThreadRefResponse(
                "context",
                normalizedScope.contextId(),
                stableMeetingThreadId(normalizedScope.contextId(), eventId),
                "channel".equals(normalizedScope.type()) ? normalizedScope.channelId() : null,
                null,
                null,
                List.of());
    }

    private static String stableMeetingThreadId(String contextId, String eventId) {
        String safeContextId = contextId == null || contextId.isBlank() ? "workspace-default" : contextId.trim();
        String material = safeContextId + ":" + (eventId == null || eventId.isBlank() ? "scope" : eventId.trim());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
            return "meeting:" + safeContextId + ":" + HexFormat.of().formatHex(digest).substring(0, 12);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required for stable meeting-thread ids", exception);
        }
    }
}

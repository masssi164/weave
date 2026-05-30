package com.massimotter.weave.backend.calls.domain;

import java.time.Instant;
import java.util.List;

public record Meeting(
        String id,
        String spaceId,
        String title,
        String roomId,
        List<String> linkedCalendarRefs,
        List<String> linkedChatRefs,
        List<String> linkedFileRefs,
        List<String> linkedDecisionRefs,
        MeetingArtifacts artifacts,
        Instant updatedAt) {

    public Meeting {
        id = requireText(id, "id");
        spaceId = requireText(spaceId, "spaceId");
        title = requireText(title, "title");
        roomId = requireText(roomId, "roomId");
        linkedCalendarRefs = linkedCalendarRefs == null ? List.of() : List.copyOf(linkedCalendarRefs);
        linkedChatRefs = linkedChatRefs == null ? List.of() : List.copyOf(linkedChatRefs);
        linkedFileRefs = linkedFileRefs == null ? List.of() : List.copyOf(linkedFileRefs);
        linkedDecisionRefs = linkedDecisionRefs == null ? List.of() : List.copyOf(linkedDecisionRefs);
        artifacts = java.util.Objects.requireNonNull(artifacts, "artifacts must not be null");
        updatedAt = java.util.Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}

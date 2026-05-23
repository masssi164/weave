package com.massimotter.weave.backend.boards.events;

import com.massimotter.weave.backend.boards.domain.EventRedactionLevel;
import com.massimotter.weave.backend.boards.domain.ProviderRef;
import com.massimotter.weave.backend.boards.domain.TaskBoardEventType;

import java.time.Instant;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Provider-facing draft event accepted by the preview Boards/Tasks event normalizer.
 * Adapters may populate this from webhooks, polling snapshots, or provider audit/activity
 * records without leaking provider vocabulary beyond this boundary.
 */
public record TaskBoardEventInput(
        String sourceEventId,
        TaskBoardEventType type,
        String actorRef,
        Instant occurredAt,
        String workspaceId,
        String projectId,
        String boardId,
        String taskId,
        ProviderRef providerRef,
        EventRedactionLevel redactionLevel,
        Map<String, Object> payload) {

    public TaskBoardEventInput {
        type = requireNonNull(type, "type must not be null");
        actorRef = requireText(actorRef, "actorRef");
        occurredAt = requireNonNull(occurredAt, "occurredAt must not be null");
        workspaceId = requireText(workspaceId, "workspaceId");
        redactionLevel = requireNonNull(redactionLevel, "redactionLevel must not be null");
        payload = Map.copyOf(requireNonNull(payload, "payload must not be null"));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}

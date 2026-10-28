package com.massimotter.weave.backend.boards.domain;

import java.time.Instant;
import java.util.Map;

import static java.util.Objects.requireNonNull;

public record TaskBoardEvent(
        String idempotencyKey,
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

    public TaskBoardEvent {
        idempotencyKey = BoardsContract.requireText(idempotencyKey, "idempotencyKey");
        type = requireNonNull(type, "type must not be null");
        actorRef = BoardsContract.requireText(actorRef, "actorRef");
        occurredAt = requireNonNull(occurredAt, "occurredAt must not be null");
        workspaceId = BoardsContract.requireText(workspaceId, "workspaceId");
        redactionLevel = requireNonNull(redactionLevel, "redactionLevel must not be null");
        payload = Map.copyOf(requireNonNull(payload, "payload must not be null"));
    }
}

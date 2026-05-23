package com.massimotter.weave.backend.boards.domain;

import java.time.Instant;
import java.util.List;

import static java.util.Objects.requireNonNull;

public record TaskComment(
        String id,
        String taskId,
        String actorRef,
        String body,
        Instant createdAt,
        Instant editedAt,
        List<ProviderRef> providerRefs) {

    public TaskComment {
        id = BoardsContract.requireText(id, "id");
        taskId = BoardsContract.requireText(taskId, "taskId");
        actorRef = BoardsContract.requireText(actorRef, "actorRef");
        body = BoardsContract.requireText(body, "body");
        createdAt = requireNonNull(createdAt, "createdAt must not be null");
        providerRefs = BoardsContract.immutableList(providerRefs, "providerRefs");
    }
}

package com.massimotter.weave.backend.boards.domain;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;

import static java.util.Objects.requireNonNull;

public record TaskItem(
        String id,
        String boardId,
        String columnId,
        String title,
        String description,
        TaskStatus status,
        int position,
        List<String> assigneeRefs,
        List<String> labelRefs,
        List<String> decisionRefs,
        TaskPriority priority,
        Instant startAt,
        Instant dueAt,
        Instant completedAt,
        Instant updatedAt,
        List<ProviderRef> providerRefs) {

    public TaskItem {
        id = BoardsContract.requireText(id, "id");
        boardId = BoardsContract.requireText(boardId, "boardId");
        columnId = BoardsContract.requireText(columnId, "columnId");
        title = BoardsContract.requireText(title, "title");
        status = requireNonNull(status, "status must not be null");
        if (position < 0) {
            throw new IllegalArgumentException("position must not be negative");
        }
        assigneeRefs = BoardsContract.immutableList(assigneeRefs, "assigneeRefs");
        labelRefs = BoardsContract.immutableList(labelRefs, "labelRefs");
        decisionRefs = BoardsContract.immutableList(decisionRefs, "decisionRefs");
        updatedAt = requireNonNull(updatedAt, "updatedAt must not be null");
        providerRefs = BoardsContract.immutableList(providerRefs, "providerRefs");
    }

    public TaskItem moveTo(String targetColumnId, int targetPosition, Instant changedAt) {
        return new TaskItem(
                id,
                boardId,
                BoardsContract.requireText(targetColumnId, "targetColumnId"),
                title,
                description,
                status,
                targetPosition,
                assigneeRefs,
                labelRefs,
                decisionRefs,
                priority,
                startAt,
                dueAt,
                completedAt,
                requireNonNull(changedAt, "changedAt must not be null"),
                providerRefs);
    }

    public TaskItem complete(Instant changedAt) {
        return withStatus(TaskStatus.COMPLETED, columnId, changedAt);
    }

    public TaskItem withStatus(TaskStatus targetStatus, String targetColumnId, Instant changedAt) {
        TaskStatus normalizedStatus = requireNonNull(targetStatus, "targetStatus must not be null");
        Instant changed = requireNonNull(changedAt, "changedAt must not be null");
        Instant completed = normalizedStatus == TaskStatus.COMPLETED ? changed : null;
        return new TaskItem(
                id,
                boardId,
                BoardsContract.requireText(targetColumnId, "targetColumnId"),
                title,
                description,
                normalizedStatus,
                position,
                assigneeRefs,
                labelRefs,
                decisionRefs,
                priority,
                startAt,
                dueAt,
                completed,
                changed,
                providerRefs);
    }

    public TaskItem withDecisionRef(String decisionRef, Instant changedAt) {
        String normalizedDecisionRef = BoardsContract.requireText(decisionRef, "decisionRef");
        LinkedHashSet<String> refs = new LinkedHashSet<>(decisionRefs);
        refs.add(normalizedDecisionRef);
        return new TaskItem(
                id,
                boardId,
                columnId,
                title,
                description,
                status,
                position,
                assigneeRefs,
                labelRefs,
                List.copyOf(refs),
                priority,
                startAt,
                dueAt,
                completedAt,
                requireNonNull(changedAt, "changedAt must not be null"),
                providerRefs);
    }
}

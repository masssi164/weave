package com.massimotter.weave.backend.boards.port;

import java.time.Instant;
import java.util.List;

public record CreateTaskCommand(
        String boardId,
        String columnId,
        String title,
        String description,
        List<String> assigneeRefs,
        List<String> labelRefs,
        Instant dueAt) {

    public CreateTaskCommand {
        if (boardId == null || boardId.isBlank()) {
            throw new IllegalArgumentException("boardId must not be blank");
        }
        if (columnId == null || columnId.isBlank()) {
            throw new IllegalArgumentException("columnId must not be blank");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        assigneeRefs = List.copyOf(assigneeRefs == null ? List.of() : assigneeRefs);
        labelRefs = List.copyOf(labelRefs == null ? List.of() : labelRefs);
    }
}

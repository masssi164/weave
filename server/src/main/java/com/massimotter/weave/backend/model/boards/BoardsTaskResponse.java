package com.massimotter.weave.backend.model.boards;

import com.massimotter.weave.backend.boards.domain.TaskPriority;
import com.massimotter.weave.backend.boards.domain.TaskStatus;
import java.time.Instant;
import java.util.List;

public record BoardsTaskResponse(
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
        String mappingRef) {

    public BoardsTaskResponse {
        assigneeRefs = assigneeRefs == null ? List.of() : List.copyOf(assigneeRefs);
        labelRefs = labelRefs == null ? List.of() : List.copyOf(labelRefs);
        decisionRefs = decisionRefs == null ? List.of() : List.copyOf(decisionRefs);
    }
}

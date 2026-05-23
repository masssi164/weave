package com.massimotter.weave.backend.model.boards;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public record BoardsCreateTaskRequest(
        @NotBlank @Size(max = 128) String columnId,
        @NotBlank @Size(max = 160) String title,
        @Size(max = 2000) String description,
        List<@Size(max = 128) String> assigneeRefs,
        List<@Size(max = 128) String> labelRefs,
        Instant dueAt) {
}

package com.massimotter.weave.backend.boards.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BoardsMoveTaskRequest(
        @NotBlank @Size(max = 128) String targetColumnId,
        @Min(0) int targetPosition) {
}

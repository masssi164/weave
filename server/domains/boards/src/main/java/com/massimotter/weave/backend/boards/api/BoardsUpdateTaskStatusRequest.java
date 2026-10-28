package com.massimotter.weave.backend.boards.api;

import jakarta.validation.constraints.Size;

public record BoardsUpdateTaskStatusRequest(
        @Size(max = 32) String status,
        @Size(max = 128) String targetColumnId) {
}

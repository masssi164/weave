package com.massimotter.weave.backend.boards.port;

public record MoveTaskCommand(String taskId, String targetColumnId, int targetPosition) {

    public MoveTaskCommand {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId must not be blank");
        }
        if (targetColumnId == null || targetColumnId.isBlank()) {
            throw new IllegalArgumentException("targetColumnId must not be blank");
        }
        if (targetPosition < 0) {
            throw new IllegalArgumentException("targetPosition must not be negative");
        }
    }
}

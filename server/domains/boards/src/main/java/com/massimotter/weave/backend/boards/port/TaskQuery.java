package com.massimotter.weave.backend.boards.port;

import com.massimotter.weave.backend.boards.domain.TaskStatus;

public record TaskQuery(String cursor, int limit, String columnId, TaskStatus status) {

    public TaskQuery {
        if (limit < 1 || limit > BoardQuery.MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + BoardQuery.MAX_LIMIT);
        }
    }

    public static TaskQuery all() {
        return new TaskQuery(null, BoardQuery.DEFAULT_LIMIT, null, null);
    }
}

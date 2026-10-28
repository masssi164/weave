package com.massimotter.weave.backend.boards.port;

import com.massimotter.weave.backend.boards.domain.TaskBoardEvent;

/**
 * Event boundary for future automation, recent activity, audit, and support diagnostics.
 * Implementations must preserve idempotency and redaction semantics before events leave
 * the backend process.
 */
public interface TaskBoardEventPublisher {

    void publish(TaskBoardEvent event);
}

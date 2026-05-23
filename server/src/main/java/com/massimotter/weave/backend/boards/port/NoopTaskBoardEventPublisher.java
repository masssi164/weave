package com.massimotter.weave.backend.boards.port;

import com.massimotter.weave.backend.boards.domain.TaskBoardEvent;

import static java.util.Objects.requireNonNull;

/**
 * Safe default for the hidden Boards preview slice. It validates the event envelope
 * without enabling notifications, audit streams, webhooks, or live runtime behavior.
 */
public final class NoopTaskBoardEventPublisher implements TaskBoardEventPublisher {

    @Override
    public void publish(TaskBoardEvent event) {
        requireNonNull(event, "event must not be null");
    }
}

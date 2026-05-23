package com.massimotter.weave.backend.boards.port;

import com.massimotter.weave.backend.boards.support.BoardsErrorCode;
import com.massimotter.weave.backend.boards.support.BoardsException;

/**
 * Central guard for the active feature-gated Boards/Tasks workspace slice. Keeping this guard near
 * the repository port prevents exploratory adapters from accidentally becoming a
 * reachable product API before routes, auth scopes, DTOs, OpenAPI publication,
 * smoke, E2E, and accessibility gates pass.
 */
public final class BoardsRuntimeGuard {

    private final boolean enabled;

    public BoardsRuntimeGuard(boolean enabled) {
        this.enabled = enabled;
    }

    public void requireEnabled() {
        if (!enabled) {
            throw new BoardsException(
                    BoardsErrorCode.PROVIDER_UNAVAILABLE,
                    "Boards and tasks are feature-gated until runtime validation passes.");
        }
    }

    public boolean enabled() {
        return enabled;
    }
}

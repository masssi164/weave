package com.massimotter.weave.backend.boards.port;

import com.massimotter.weave.backend.boards.support.BoardsErrorCode;
import com.massimotter.weave.backend.boards.support.BoardsException;

/**
 * Central guard for the active feature-gated Boards/Tasks slice. Keeping this guard near
 * the repository port prevents exploratory adapters from accidentally becoming a
 * reachable product API before a promotion spec defines routes, auth scopes, DTOs,
 * OpenAPI publication, smoke, E2E, and accessibility gates.
 */
public final class BoardsPreviewGuard {

    private final boolean enabled;

    public BoardsPreviewGuard(boolean enabled) {
        this.enabled = enabled;
    }

    public void requireEnabled() {
        if (!enabled) {
            throw new BoardsException(
                    BoardsErrorCode.PROVIDER_UNAVAILABLE,
                    "Boards and tasks are a feature-gated preview module and are feature-gated until runtime validation passes.");
        }
    }

    public boolean enabled() {
        return enabled;
    }
}

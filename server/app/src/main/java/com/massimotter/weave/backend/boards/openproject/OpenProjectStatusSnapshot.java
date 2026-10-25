package com.massimotter.weave.backend.boards.openproject;

import java.net.URI;

/**
 * Minimal OpenProject status shape used to build Weave-owned board columns.
 */
public record OpenProjectStatusSnapshot(
        long id,
        String name,
        int position,
        boolean closed,
        URI webUrl) {
}

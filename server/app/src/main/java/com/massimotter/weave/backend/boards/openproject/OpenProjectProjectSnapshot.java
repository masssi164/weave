package com.massimotter.weave.backend.boards.openproject;

import java.net.URI;
import java.time.Instant;

/**
 * Minimal OpenProject project shape used by the read-sync contract mapper.
 * This is an adapter-side snapshot, not a public Weave API DTO.
 */
public record OpenProjectProjectSnapshot(
        long id,
        String identifier,
        String name,
        String description,
        boolean archived,
        URI webUrl,
        Instant updatedAt) {
}

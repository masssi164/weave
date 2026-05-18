package com.massimotter.weave.backend.boards.openproject;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/**
 * Minimal OpenProject work package shape used by the read-only board sync seam.
 * Provider-specific HAL links and raw payloads stay outside the product model.
 */
public record OpenProjectWorkPackageSnapshot(
        long id,
        long projectId,
        long statusId,
        String subject,
        String description,
        int position,
        String priority,
        List<String> assigneeRefs,
        List<String> labelRefs,
        Instant startAt,
        Instant dueAt,
        Instant closedAt,
        Instant updatedAt,
        URI webUrl,
        String lockVersion) {
}

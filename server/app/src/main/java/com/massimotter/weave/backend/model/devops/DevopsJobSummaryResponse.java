package com.massimotter.weave.backend.model.devops;

import java.time.Instant;

public record DevopsJobSummaryResponse(
        String id,
        String name,
        String state,
        Instant startedAt,
        Instant finishedAt) {
}

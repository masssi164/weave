package com.massimotter.weave.backend.model.devops;

import java.time.Instant;
import java.util.List;

public record DevopsIssueSummaryResponse(
        String id,
        String projectId,
        String title,
        String state,
        String providerKey,
        Instant updatedAt,
        List<String> labels) {

    public DevopsIssueSummaryResponse {
        labels = labels == null ? List.of() : List.copyOf(labels);
    }
}

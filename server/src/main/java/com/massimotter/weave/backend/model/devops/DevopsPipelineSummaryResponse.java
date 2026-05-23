package com.massimotter.weave.backend.model.devops;

import java.time.Instant;
import java.util.List;

public record DevopsPipelineSummaryResponse(
        String id,
        String repositoryId,
        String ref,
        String state,
        String providerKey,
        Instant updatedAt,
        List<DevopsJobSummaryResponse> jobs) {

    public DevopsPipelineSummaryResponse {
        jobs = jobs == null ? List.of() : List.copyOf(jobs);
    }
}

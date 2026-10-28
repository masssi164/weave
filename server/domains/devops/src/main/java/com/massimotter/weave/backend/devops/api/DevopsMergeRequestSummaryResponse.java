package com.massimotter.weave.backend.devops.api;

import java.time.Instant;

public record DevopsMergeRequestSummaryResponse(
        String id,
        String repositoryId,
        String title,
        String sourceBranch,
        String targetBranch,
        String state,
        String providerKey,
        Instant updatedAt) {
}

package com.massimotter.weave.backend.devops.api;

import java.time.Instant;

public record DevopsReleaseSummaryResponse(
        String id,
        String repositoryId,
        String name,
        String tagName,
        String providerKey,
        Instant releasedAt) {
}

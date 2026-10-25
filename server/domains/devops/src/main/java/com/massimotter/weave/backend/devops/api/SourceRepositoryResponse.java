package com.massimotter.weave.backend.devops.api;

public record SourceRepositoryResponse(
        String id,
        String projectId,
        String displayName,
        String defaultBranch,
        String providerKey,
        boolean archived) {
}

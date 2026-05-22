package com.massimotter.weave.backend.model.devops;

public record SourceRepositoryResponse(
        String id,
        String projectId,
        String displayName,
        String defaultBranch,
        String providerKey,
        boolean archived) {
}

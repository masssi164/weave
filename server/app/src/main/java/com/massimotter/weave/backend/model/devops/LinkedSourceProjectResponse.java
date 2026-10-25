package com.massimotter.weave.backend.model.devops;

import java.util.List;

public record LinkedSourceProjectResponse(
        String id,
        String displayName,
        String providerKey,
        String visibility,
        List<String> repositoryIds) {

    public LinkedSourceProjectResponse {
        repositoryIds = repositoryIds == null ? List.of() : List.copyOf(repositoryIds);
    }
}

package com.massimotter.weave.backend.model.office;

import java.util.List;

public record OfficeProviderCandidateResponse(
        String providerKey,
        String displayName,
        boolean defaultCandidate,
        String runtimeFit,
        String licensingPosture,
        String integrationPath,
        List<String> notes) {

    public OfficeProviderCandidateResponse {
        notes = notes == null ? List.of() : List.copyOf(notes);
    }
}

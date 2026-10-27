package com.massimotter.weave.backend.identity.profile.api;

import java.util.List;

public record ProfileReadinessResponse(
        String contractId,
        String endpoint,
        boolean backendOwnedFacade,
        boolean directProviderCallsAllowed,
        boolean supportSafe,
        String readiness,
        List<String> unsupportedOperations) {

    public ProfileReadinessResponse {
        unsupportedOperations = unsupportedOperations == null ? List.of() : List.copyOf(unsupportedOperations);
    }
}

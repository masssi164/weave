package com.massimotter.weave.backend.model.office;

public record OfficeLockSessionReadinessResponse(
        String documentLocks,
        String sessionTokens,
        String callbackVerification,
        boolean supportSafe) {
}

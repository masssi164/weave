package com.massimotter.weave.backend.office.api;

public record OfficeLockSessionReadinessResponse(
        String documentLocks,
        String sessionTokens,
        String callbackVerification,
        boolean supportSafe) {
}

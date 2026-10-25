package com.massimotter.weave.backend.model.spaces;

/** Support-safe setup result: no human or provider identity is included. */
public record DefaultSpaceBootstrapResponse(String schemaVersion, String status, String spaceRef,
        String outcome, boolean ownerAccessVerified, boolean filesReady, boolean calendarReady,
        boolean supportSafe, boolean containsSecretValues) {
    public static DefaultSpaceBootstrapResponse passed(String outcome) {
        return new DefaultSpaceBootstrapResponse("weave.default-space-bootstrap/v1", "passed",
                "workspace-default", outcome, true, true, true, true, false);
    }
}

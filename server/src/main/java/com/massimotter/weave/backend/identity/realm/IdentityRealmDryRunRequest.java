package com.massimotter.weave.backend.identity.realm;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Dry-run request comparing an optional current realm snapshot with the desired support-safe state.")
public record IdentityRealmDryRunRequest(
        IdentityRealmDesiredState currentState,
        IdentityRealmDesiredState desiredState,
        String reason) {
}

package com.massimotter.weave.backend.model.identity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Provider-neutral result of authenticated identity-session reconciliation.")
public record IdentitySessionReconcileResponse(
        @Schema(
                        description = "Closed reconciliation state. Access changes require one OIDC token refresh.",
                        allowableValues = {"unchanged", "access_updated"},
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        example = "unchanged")
                String state,
        @Schema(
                        description = "Whether the caller must refresh its already-issued OIDC token before domain bootstrap.",
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        example = "false")
                boolean sessionRefreshRequired) {

    public IdentitySessionReconcileResponse {
        if (!"unchanged".equals(state) && !"access_updated".equals(state)) {
            throw new IllegalArgumentException("state must be unchanged or access_updated");
        }
        if (sessionRefreshRequired != "access_updated".equals(state)) {
            throw new IllegalArgumentException("sessionRefreshRequired must match the reconciliation state");
        }
    }

    public static IdentitySessionReconcileResponse unchanged() {
        return new IdentitySessionReconcileResponse("unchanged", false);
    }

    public static IdentitySessionReconcileResponse accessUpdated() {
        return new IdentitySessionReconcileResponse("access_updated", true);
    }
}

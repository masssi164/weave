package com.massimotter.weave.backend.model.admin;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Support-safe reference to a provider secret. Never contains the raw secret value.")
public record SecretRefResponse(
        @Schema(example = "secretref://weave/provider/keycloak-client-secret")
        String ref,
        @Schema(example = "keycloak-realm")
        String providerKey,
        @Schema(example = "oidc-client-secret")
        String purpose,
        boolean configured,
        boolean rotationRequired,
        boolean supportSafe,
        boolean rawSecretExposed) {
}

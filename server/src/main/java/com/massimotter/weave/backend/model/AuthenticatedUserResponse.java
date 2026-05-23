package com.massimotter.weave.backend.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Canonical Weave identity and product profile snapshot for the authenticated user.")
public record AuthenticatedUserResponse(
        @Schema(description = "Stable immutable Weave user identifier, backed by the Keycloak subject.")
        String userId,
        @Schema(description = "Stable login/workspace handle.")
        String username,
        @Schema(description = "Verified email address when available.")
        String email,
        @Schema(description = "Whether Keycloak reports the email address as verified.")
        boolean emailVerified,
        @Schema(description = "User-visible product display name.")
        String displayName,
        @Schema(description = "Preferred locale.")
        String locale,
        @Schema(description = "Preferred timezone.")
        String timezone,
        @Schema(description = "MVP product roles.")
        List<String> roles,
        @Schema(description = "Workspace or module groups.")
        List<String> groups,
        @Schema(description = "Authorized party/client that requested the token.")
        String issuedFor,
        @Schema(description = "Token audience values.")
        List<String> audience,
        @Schema(description = "Raw realm roles from Keycloak.")
        List<String> realmRoles,
        @Schema(description = "Product profile sync status by module.")
        ModuleSyncStatusResponse moduleSyncStatus) {
}

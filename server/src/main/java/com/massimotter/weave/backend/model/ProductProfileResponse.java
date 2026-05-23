package com.massimotter.weave.backend.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@Schema(description = "Product profile facade data for the authenticated Weave user.")
public record ProductProfileResponse(
        @Schema(description = "Stable immutable Weave user identifier, backed by the Keycloak subject.")
        String userId,
        @Schema(description = "Stable login/workspace handle.")
        String username,
        @Schema(description = "Email address from the identity authority when available.")
        String email,
        @Schema(description = "Whether Keycloak reports the email address as verified.")
        boolean emailVerified,
        @Schema(description = "User-visible product display name.")
        String displayName,
        @Schema(description = "Backend-managed avatar reference or upstream picture claim.")
        String avatar,
        @Schema(description = "Preferred locale.")
        String locale,
        @Schema(description = "Preferred timezone.")
        String timezone,
        @Schema(description = "Frontend accessibility preferences owned by the product profile facade.")
        Map<String, String> accessibilityPreferences,
        @Schema(description = "Profile visibility policy for product surfaces.", allowableValues = {"private", "workspace", "public"})
        String profileVisibility,
        @Schema(description = "Product profile sync status by module.")
        ModuleSyncStatusResponse moduleSyncStatus) {
}

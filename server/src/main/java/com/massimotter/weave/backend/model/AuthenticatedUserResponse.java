package com.massimotter.weave.backend.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Canonical Weave identity and product profile snapshot for the authenticated user.")
public record AuthenticatedUserResponse(
        @Schema(description = "Stable immutable Weave account identifier derived from issuer plus subject, never from email.")
        String userId,
        @Schema(description = "Stable login/workspace handle.")
        String username,
        @Schema(description = "Verified email address when available; never the primary identity key.")
        String email,
        @Schema(description = "Whether the identity source reports the email address as verified.")
        boolean emailVerified,
        @Schema(description = "User-visible product display name.")
        String displayName,
        @Schema(description = "Preferred locale.")
        String locale,
        @Schema(description = "Preferred timezone.")
        String timezone,
        @Schema(description = "Canonical Weave organization roles mapped from identity-source claims.")
        List<String> roles,
        @Schema(description = "Workspace or module groups.")
        List<String> groups,
        @Schema(description = "Authorized party/client that requested the token.")
        String issuedFor,
        @Schema(description = "Token audience values.")
        List<String> audience,
        @Schema(description = "Organization identifier in Weave policy space.")
        String organizationId,
        @Schema(description = "Identity source issuer used to form the immutable primary identity key.")
        String identityIssuer,
        @Schema(description = "Identity source subject used to form the immutable primary identity key.")
        String subject,
        @Schema(description = "Documented immutable primary identity key: issuer plus subject.")
        String primaryIdentityKey,
        @Schema(description = "Support-safe account id derived from the primary identity key.")
        String accountId,
        @Schema(description = "Context-local roles for the current organization/session.")
        List<String> contextRoles,
        @Schema(description = "Support-safe role/group claim mapping evidence.")
        List<String> providerRoleMappings,
        @Schema(description = "Always false: email is an attribute, not the primary key.")
        boolean emailPrimaryKey,
        @Schema(description = "Product profile sync status by module.")
        ModuleSyncStatusResponse moduleSyncStatus) {
}

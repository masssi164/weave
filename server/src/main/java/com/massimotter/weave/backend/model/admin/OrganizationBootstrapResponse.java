package com.massimotter.weave.backend.model.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "Support-safe organization bootstrap result.")
public record OrganizationBootstrapResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String organizationId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String bootstrapMode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String actorPrimaryIdentityKey,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> retainedAdminPrimaryIdentityKeys,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean lastAdminGuardPassed,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean supportSafe,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant bootstrappedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> auditRefs) {

    public OrganizationBootstrapResponse {
        retainedAdminPrimaryIdentityKeys = retainedAdminPrimaryIdentityKeys == null ? List.of() : List.copyOf(retainedAdminPrimaryIdentityKeys);
        auditRefs = auditRefs == null ? List.of() : List.copyOf(auditRefs);
    }
}

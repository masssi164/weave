package com.massimotter.weave.backend.model.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Admin/operator support-safe explanation of the effective Weave policy for the authenticated subject.")
public record EffectivePolicyResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String subject,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String organization,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String context,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> identitySources,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> groups,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> orgRoles,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> contextRoles,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> providerRoleMappings,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> capabilityGrants,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<EffectivePolicyDenyResponse> denies,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> readinessImpact,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> auditRefs,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean denyByDefault,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean supportSafe,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String primaryIdentityKey,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean emailPrimaryKey) {

    public EffectivePolicyResponse {
        context = context == null || context.isBlank() ? "organization" : context.trim();
        identitySources = identitySources == null ? List.of() : List.copyOf(identitySources);
        groups = groups == null ? List.of() : List.copyOf(groups);
        orgRoles = orgRoles == null ? List.of() : List.copyOf(orgRoles);
        contextRoles = contextRoles == null ? List.of() : List.copyOf(contextRoles);
        providerRoleMappings = providerRoleMappings == null ? List.of() : List.copyOf(providerRoleMappings);
        capabilityGrants = capabilityGrants == null ? List.of() : List.copyOf(capabilityGrants);
        denies = denies == null ? List.of() : List.copyOf(denies);
        readinessImpact = readinessImpact == null ? List.of() : List.copyOf(readinessImpact);
        auditRefs = auditRefs == null ? List.of() : List.copyOf(auditRefs);
    }
}

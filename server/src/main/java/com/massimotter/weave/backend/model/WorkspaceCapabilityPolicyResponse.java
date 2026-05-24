package com.massimotter.weave.backend.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Admin/operator support-safe capability policy snapshot.")
public record WorkspaceCapabilityPolicyResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String idmProviderCategory,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String defaultIdmProvider,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String adapterContract,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String principalSource,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> roles,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> groups,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> profileKeys,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> grantedCapabilities,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean denyByDefault,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean supportSafe,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String weaverRuntimePosture) {

    public WorkspaceCapabilityPolicyResponse {
        roles = roles == null ? List.of() : List.copyOf(roles);
        groups = groups == null ? List.of() : List.copyOf(groups);
        profileKeys = profileKeys == null ? List.of() : List.copyOf(profileKeys);
        grantedCapabilities = grantedCapabilities == null ? List.of() : List.copyOf(grantedCapabilities);
    }
}

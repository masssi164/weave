package com.massimotter.weave.backend.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record WeaverRuntimeProfileResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean enabled,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String posture,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String runtimeKind,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String generatedFrom,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String userRef,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String baselineProfile,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String containerImage,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String workspacePath,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String isolatedAgentDirectory,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String dockerNetworkMode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> allowedCapabilities,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> pluginAllowlist,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> toolAllowlist,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean execEnabled,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean elevatedEnabled,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean auditRequired,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean forkRequired,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String memberImpact) {

    public WeaverRuntimeProfileResponse {
        allowedCapabilities = List.copyOf(allowedCapabilities == null ? List.of() : allowedCapabilities);
        pluginAllowlist = List.copyOf(pluginAllowlist == null ? List.of() : pluginAllowlist);
        toolAllowlist = List.copyOf(toolAllowlist == null ? List.of() : toolAllowlist);
    }
}

package com.massimotter.weave.backend.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import java.util.List;

public record WeaverRuntimeProfileResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean enabled,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String posture,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String runtimeKind,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String runtimeProvider,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String modelProvider,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String toolProvider,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String generatedFrom,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String userRef,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String profileVersion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String runtimeProfileHash,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String signature,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String expiresAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean revoked,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String revocationStatus,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int revocationGeneration,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String previousProfileHash,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String rollbackProfileHash,
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
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"deny", "allowlist", "ask", "auto", "full"})
        String permissionMode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean auditRequired,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean forkRequired,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Map<String, Object> channelProjection,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Map<String, Object> mcpProjection,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Map<String, Object> credentialBrokerContract,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Map<String, Object> auditPolicy,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Map<String, Object> supportSafeProfileReceipt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String approvalPolicy,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String secretPosture,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String isolationBoundary,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String memberImpact) {

    public WeaverRuntimeProfileResponse {
        allowedCapabilities = List.copyOf(allowedCapabilities == null ? List.of() : allowedCapabilities);
        pluginAllowlist = List.copyOf(pluginAllowlist == null ? List.of() : pluginAllowlist);
        toolAllowlist = List.copyOf(toolAllowlist == null ? List.of() : toolAllowlist);
        permissionMode = permissionMode == null || permissionMode.isBlank() ? "deny" : permissionMode.trim();
        channelProjection = Map.copyOf(channelProjection == null ? Map.of() : channelProjection);
        mcpProjection = Map.copyOf(mcpProjection == null ? Map.of() : mcpProjection);
        credentialBrokerContract = Map.copyOf(credentialBrokerContract == null ? Map.of() : credentialBrokerContract);
        auditPolicy = Map.copyOf(auditPolicy == null ? Map.of() : auditPolicy);
        supportSafeProfileReceipt = Map.copyOf(supportSafeProfileReceipt == null ? Map.of() : supportSafeProfileReceipt);
    }
}

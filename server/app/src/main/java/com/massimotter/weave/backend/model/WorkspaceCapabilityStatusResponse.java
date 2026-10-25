package com.massimotter.weave.backend.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Availability and readiness state for one workspace capability.")
public record WorkspaceCapabilityStatusResponse(
        @Schema(description = "Whether the capability is enabled for this workspace.", example = "true")
        boolean enabled,
        @Schema(description = "Current readiness state for this capability.")
        WorkspaceCapabilityReadiness readiness,
        @Schema(description = "Support-safe effective policy state for the authenticated principal.")
        WorkspaceCapabilityPolicyState policyState,
        @Schema(description = "Policy profile key that decided this capability. Does not expose provider internals.")
        String profileKey,
        @Schema(description = "Member-safe impact or fallback copy for this capability.")
        String memberImpact,
        @Schema(description = "Support-safe reference for this capability state. Does not expose provider internals.")
        String supportRef,
        @Schema(description = "Category-level capability identifiers granted to the authenticated principal.")
        List<String> grantedCapabilities) {

    public WorkspaceCapabilityStatusResponse(
            boolean enabled,
            WorkspaceCapabilityReadiness readiness,
            WorkspaceCapabilityPolicyState policyState,
            String profileKey,
            String memberImpact,
            List<String> grantedCapabilities) {
        this(enabled, readiness, policyState, profileKey, memberImpact, null, grantedCapabilities);
    }

    public WorkspaceCapabilityStatusResponse {
        grantedCapabilities = grantedCapabilities == null ? List.of() : List.copyOf(grantedCapabilities);
    }
}

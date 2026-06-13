package com.massimotter.weave.backend.model.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Support-safe preview of Weaver policy plus weaver-group eligibility before runtime provisioning.")
public record WeaverEligibilityPreviewResponse(
        boolean policyEnabled,
        boolean groupMembershipRequired,
        List<String> requiredGroups,
        List<String> eligibleCapabilities,
        String memberStateWithoutPolicy,
        String memberStateWithoutGroup,
        String memberStateWhenEligible,
        List<String> blockedReasons,
        List<String> nextActions,
        List<String> auditRefs) {
    public WeaverEligibilityPreviewResponse {
        requiredGroups = requiredGroups == null ? List.of() : List.copyOf(requiredGroups);
        eligibleCapabilities = eligibleCapabilities == null ? List.of() : List.copyOf(eligibleCapabilities);
        blockedReasons = blockedReasons == null ? List.of() : List.copyOf(blockedReasons);
        nextActions = nextActions == null ? List.of() : List.copyOf(nextActions);
        auditRefs = auditRefs == null ? List.of() : List.copyOf(auditRefs);
    }
}

package com.massimotter.weave.backend.model.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Support-safe organization go-live readiness summary for owners/admins.")
public record GoLiveReadinessResponse(
        String state,
        String memberPreviewState,
        List<String> blockers,
        List<String> adminActions,
        List<String> auditRefs,
        boolean supportSafe,
        boolean normalMembersMayAccessSetupControls,
        boolean rawProviderDiagnosticsExposed) {
    public GoLiveReadinessResponse {
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        adminActions = adminActions == null ? List.of() : List.copyOf(adminActions);
        auditRefs = auditRefs == null ? List.of() : List.copyOf(auditRefs);
    }
}

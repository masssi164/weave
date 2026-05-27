package com.massimotter.weave.backend.identity.realm;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Support-safe guarded identity realm apply decision and audit receipt.")
public record IdentityRealmApplyReport(
        String providerKey,
        String realmId,
        String dryRunId,
        String decision,
        String executionMode,
        boolean applied,
        boolean providerMutationPerformed,
        boolean supportSafe,
        boolean rawSecretExposed,
        boolean lastAdminGuardPassed,
        boolean rollbackEvidenceRequired,
        boolean rollbackEvidenceAccepted,
        List<String> blockedReasons,
        List<IdentityRealmDryRunReport.ChangeRecord> changes,
        List<String> nextActions,
        List<String> auditRefs) {

    public IdentityRealmApplyReport {
        blockedReasons = blockedReasons == null ? List.of() : List.copyOf(blockedReasons);
        changes = changes == null ? List.of() : List.copyOf(changes);
        nextActions = nextActions == null ? List.of() : List.copyOf(nextActions);
        auditRefs = auditRefs == null ? List.of() : List.copyOf(auditRefs);
    }
}

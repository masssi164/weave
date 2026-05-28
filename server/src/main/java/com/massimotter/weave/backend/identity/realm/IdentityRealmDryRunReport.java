package com.massimotter.weave.backend.identity.realm;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Support-safe, deterministic realm dry-run report. No provider bodies, secrets, credential URLs, private IDs, or tokens are returned.")
public record IdentityRealmDryRunReport(
        String providerKey,
        String realmId,
        String dryRunId,
        String operation,
        String readiness,
        boolean destructiveApplyAvailable,
        boolean supportSafe,
        boolean rawSecretExposed,
        List<ChangeRecord> changes,
        List<ReadinessCheck> readinessChecks,
        List<String> diff,
        List<String> warnings,
        List<String> blockers,
        List<String> nextActions,
        List<String> auditRefs) {

    public IdentityRealmDryRunReport {
        changes = changes == null ? List.of() : List.copyOf(changes);
        readinessChecks = readinessChecks == null ? List.of() : List.copyOf(readinessChecks);
        diff = diff == null ? List.of() : List.copyOf(diff);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        nextActions = nextActions == null ? List.of() : List.copyOf(nextActions);
        auditRefs = auditRefs == null ? List.of() : List.copyOf(auditRefs);
    }

    public record ChangeRecord(
            String path,
            String action,
            String classification,
            String reasonCode,
            String beforeValue,
            String afterValue,
            String memberImpact,
            boolean applyBlocked) {
    }

    public record ReadinessCheck(
            String key,
            String state,
            String reasonCode,
            String remediation) {
    }
}

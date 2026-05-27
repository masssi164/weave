package com.massimotter.weave.backend.identity.realm;

import java.util.List;

public record IdentityRealmDryRunReport(
        String providerKey,
        String realmId,
        String operation,
        String readiness,
        boolean destructiveApplyAvailable,
        boolean supportSafe,
        boolean rawSecretExposed,
        List<String> diff,
        List<String> warnings,
        List<String> blockers,
        List<String> nextActions) {

    public IdentityRealmDryRunReport {
        diff = diff == null ? List.of() : List.copyOf(diff);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        nextActions = nextActions == null ? List.of() : List.copyOf(nextActions);
    }
}

package com.massimotter.weave.backend.identity.realm;

import java.util.List;

public interface IdentityRealmLiveApplyAdapter {

    String providerKey();

    IdentityRealmLiveApplyResult apply(IdentityRealmDryRunEvidence dryRunEvidence, IdentityRealmApplyRequest request);

    record IdentityRealmLiveApplyResult(
            boolean applied,
            boolean providerMutationPerformed,
            String executionMode,
            List<String> blockedReasons,
            List<String> nextActions) {

        public IdentityRealmLiveApplyResult {
            blockedReasons = blockedReasons == null ? List.of() : List.copyOf(blockedReasons);
            nextActions = nextActions == null ? List.of() : List.copyOf(nextActions);
        }
    }
}

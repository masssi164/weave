package com.massimotter.weave.backend.identity.realm;

public interface IdentityRealmProvider {

    String providerKey();

    IdentityRealmDryRunReport dryRun(IdentityRealmDryRunRequest request);

    default IdentityRealmDryRunReport dryRun(IdentityRealmDesiredState desiredState) {
        return dryRun(new IdentityRealmDryRunRequest(null, desiredState, null));
    }

    default boolean destructiveApplyAvailable() {
        return false;
    }
}

package com.massimotter.weave.backend.identity.realm;

public interface IdentityRealmProvider {

    String providerKey();

    IdentityRealmDryRunReport dryRun(IdentityRealmDesiredState desiredState);

    default boolean destructiveApplyAvailable() {
        return false;
    }
}

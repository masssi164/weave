package com.massimotter.weave.backend.identity.realm;

public interface KeycloakRealmAdminClient {

    ApplySummary applyDesiredState(IdentityRealmDesiredState desiredState);

    record ApplySummary(boolean providerMutationPerformed, int verifiedOperationCount) {
    }
}

package com.massimotter.weave.backend.identity.realm;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Guarded admin request to apply an identity realm desired state after dry-run review.")
public record IdentityRealmApplyRequest(
        IdentityRealmDesiredState currentState,
        IdentityRealmDesiredState desiredState,
        String dryRunId,
        String policySimulationRef,
        String confirmationPhrase,
        boolean approveRisky,
        boolean approveDestructive,
        List<String> retainedAdminPrimaryIdentityKeys,
        String rollbackEvidenceRef,
        String reason) {

    public IdentityRealmApplyRequest(
            IdentityRealmDesiredState currentState,
            IdentityRealmDesiredState desiredState,
            String confirmationPhrase,
            boolean approveRisky,
            boolean approveDestructive,
            List<String> retainedAdminPrimaryIdentityKeys,
            String rollbackEvidenceRef,
            String reason) {
        this(currentState, desiredState, null, null, confirmationPhrase, approveRisky, approveDestructive, retainedAdminPrimaryIdentityKeys, rollbackEvidenceRef, reason);
    }

    public IdentityRealmApplyRequest {
        retainedAdminPrimaryIdentityKeys = retainedAdminPrimaryIdentityKeys == null ? List.of() : List.copyOf(retainedAdminPrimaryIdentityKeys);
    }

    public IdentityRealmDryRunRequest dryRunRequest() {
        return new IdentityRealmDryRunRequest(currentState, desiredState, reason);
    }
}

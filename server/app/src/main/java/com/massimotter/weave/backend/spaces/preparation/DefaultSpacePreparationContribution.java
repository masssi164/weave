package com.massimotter.weave.backend.spaces.preparation;

import com.massimotter.weave.backend.model.WorkspaceCapabilitiesResponse;
import com.massimotter.weave.backend.model.WorkspaceCapabilityPolicyState;
import com.massimotter.weave.backend.model.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.model.WorkspaceCapabilityStatusResponse;
import com.massimotter.weave.backend.service.OrganizationIdentityContext;
import java.util.List;
import java.util.Optional;

/** Preconditions contributed only by domains installed in the Space preparation runtime. */
public interface DefaultSpacePreparationContribution {
    String domain();
    List<String> requiredCapabilities();
    boolean ready(WorkspaceCapabilitiesResponse snapshot);
    Optional<Runnable> captureAuthorityRecheck(OrganizationIdentityContext owner);

    static boolean ready(WorkspaceCapabilityStatusResponse status) {
        return status != null && status.enabled() && status.policyState() == WorkspaceCapabilityPolicyState.ALLOWED
                && status.readiness() == WorkspaceCapabilityReadiness.READY;
    }
}

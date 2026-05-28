package com.massimotter.weave.backend.model.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Support-safe effective capability policy simulation for admins/operators.")
public record EffectivePolicySimulationResponse(
        String subject,
        String organizationId,
        List<String> roles,
        List<String> groups,
        List<String> requestedCapabilities,
        List<String> grantedCapabilities,
        List<String> deniedInputs,
        boolean unknownInputsFailClosed,
        boolean weaverDefaultDisabled,
        boolean supportSafe,
        List<CapabilityState> capabilityStates,
        List<String> nextActions,
        List<String> auditRefs) {

    public EffectivePolicySimulationResponse {
        roles = roles == null ? List.of() : List.copyOf(roles);
        groups = groups == null ? List.of() : List.copyOf(groups);
        requestedCapabilities = requestedCapabilities == null ? List.of() : List.copyOf(requestedCapabilities);
        grantedCapabilities = grantedCapabilities == null ? List.of() : List.copyOf(grantedCapabilities);
        deniedInputs = deniedInputs == null ? List.of() : List.copyOf(deniedInputs);
        capabilityStates = capabilityStates == null ? List.of() : List.copyOf(capabilityStates);
        nextActions = nextActions == null ? List.of() : List.copyOf(nextActions);
        auditRefs = auditRefs == null ? List.of() : List.copyOf(auditRefs);
    }

    public record CapabilityState(
            String capability,
            String state,
            String reasonCode,
            String memberImpact) {
    }
}

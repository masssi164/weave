package com.massimotter.weave.backend.agentruntime.capability;

import com.massimotter.weave.backend.capability.config.CapabilitySettings;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.capability.port.WorkspaceCapabilityContribution;
import com.massimotter.weave.backend.capability.port.WorkspaceCapabilityDefinition;
import com.massimotter.weave.backend.capability.readiness.CapabilityAvailability;
import com.massimotter.weave.backend.security.workload.config.WorkloadEntitlementProperties;
import java.util.List;
import java.util.Map;

/** Owns AgentRuntime capability declarations and support-safe readiness policy. */
public final class AgentRuntimeWorkspaceCapabilities {

    private AgentRuntimeWorkspaceCapabilities() {}

    public static WorkspaceCapabilityContribution contribution(
            AgentRuntimeWorkspaceCapabilityProperties properties,
            WorkloadEntitlementProperties entitlement) {
        CapabilitySettings settings = properties.agentRuntimeControl();
        var definition = new WorkspaceCapabilityDefinition(
                "agent-runtime", "Agent Runtime Control", settings,
                List.of("agent-runtime.entitled"),
                List.of(), List.of(),
                List.of(new WorkspaceCapabilityDefinition.GroupGrant(
                        "/capabilities/weaver", "group:/capabilities/weaver",
                        entitlement.enabled() ? List.of("agent-runtime.entitled") : List.of())),
                Map.of("agent-runtime.entitled",
                        "Agent Runtime Control requires current /capabilities/weaver organization membership"),
                entitlement.enabled()
                        ? "ARC entitlement is derived from the configured Keycloak group; profile and cell authority remain server-owned"
                        : "disabled-by-default; Agent Runtime Control requires explicit Keycloak entitlement configuration",
                List.of(), 100);
        return new WorkspaceCapabilityContribution(
                definition,
                shell -> CapabilityAvailability.standalone(
                        settings, WorkspaceCapabilityReadiness.UNAVAILABLE,
                        "Agent Runtime Control is optional and requires a current Keycloak entitlement."));
    }
}

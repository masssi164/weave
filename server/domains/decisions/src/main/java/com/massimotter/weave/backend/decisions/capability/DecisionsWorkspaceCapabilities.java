package com.massimotter.weave.backend.decisions.capability;

import com.massimotter.weave.backend.capability.config.CapabilitySettings;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.capability.port.WorkspaceCapabilityContribution;
import com.massimotter.weave.backend.capability.port.WorkspaceCapabilityDefinition;
import com.massimotter.weave.backend.capability.readiness.CapabilityAvailability;
import java.util.List;
import java.util.Map;

/** Owns Decisions capability declarations and support-safe readiness policy. */
public final class DecisionsWorkspaceCapabilities {

    private DecisionsWorkspaceCapabilities() {}

    public static WorkspaceCapabilityContribution contribution(
            DecisionsWorkspaceCapabilityProperties properties) {
        CapabilitySettings settings = properties.decisionsEvidence();
        var definition = new WorkspaceCapabilityDefinition(
                "decisions", "decisions/evidence", settings,
                List.of("decisions.read", "decisions.record"),
                List.of("decisions.read", "decisions.record"), List.of("decisions.read"),
                List.of(), Map.of(),
                null, List.of(), 70);
        return new WorkspaceCapabilityContribution(
                definition,
                shell -> CapabilityAvailability.standalone(
                        settings, WorkspaceCapabilityReadiness.READY,
                        "Decisions and evidence are available through Weave."));
    }
}

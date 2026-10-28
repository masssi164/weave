package com.massimotter.weave.backend.devops.capability;

import com.massimotter.weave.backend.capability.config.CapabilitySettings;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.capability.port.WorkspaceCapabilityContribution;
import com.massimotter.weave.backend.capability.port.WorkspaceCapabilityDefinition;
import com.massimotter.weave.backend.capability.readiness.CapabilityAvailability;
import java.util.List;
import java.util.Map;

/** Owns Devops capability declarations and support-safe readiness policy. */
public final class DevopsWorkspaceCapabilities {

    private DevopsWorkspaceCapabilities() {}

    public static WorkspaceCapabilityContribution contribution(
            DevopsWorkspaceCapabilityProperties properties) {
        CapabilitySettings settings = properties.releaseEvidence();
        var definition = new WorkspaceCapabilityDefinition(
                "devops", "release evidence", settings,
                List.of("release_evidence.read", "release_evidence.manage"),
                List.of("release_evidence.read", "release_evidence.manage"), List.of("release_evidence.read"),
                List.of(), Map.of(),
                null, List.of(), 90);
        return new WorkspaceCapabilityContribution(
                definition,
                shell -> CapabilityAvailability.standalone(
                        settings, WorkspaceCapabilityReadiness.READY,
                        "Release evidence is available through Weave."));
    }
}

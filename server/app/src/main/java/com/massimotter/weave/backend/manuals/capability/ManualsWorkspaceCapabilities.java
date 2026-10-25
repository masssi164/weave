package com.massimotter.weave.backend.manuals.capability;

import com.massimotter.weave.backend.capability.config.CapabilitySettings;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.capability.port.WorkspaceCapabilityContribution;
import com.massimotter.weave.backend.capability.port.WorkspaceCapabilityDefinition;
import com.massimotter.weave.backend.capability.readiness.CapabilityAvailability;
import java.util.List;
import java.util.Map;

/** Owns Manuals capability declarations and support-safe readiness policy. */
public final class ManualsWorkspaceCapabilities {

    private ManualsWorkspaceCapabilities() {}

    public static WorkspaceCapabilityContribution contribution(
            ManualsWorkspaceCapabilityProperties properties) {
        CapabilitySettings settings = properties.manualsHelp();
        var definition = new WorkspaceCapabilityDefinition(
                "manuals", "manuals/help", settings,
                List.of("manuals.read", "manuals.admin"),
                List.of("manuals.read", "manuals.admin"), List.of("manuals.read"),
                List.of(), Map.of(),
                null, List.of(), 80);
        return new WorkspaceCapabilityContribution(
                definition,
                shell -> CapabilityAvailability.standalone(
                        settings, WorkspaceCapabilityReadiness.READY,
                        "Manuals and help are available through Weave."));
    }
}

package com.massimotter.weave.backend.office.capability;

import com.massimotter.weave.backend.capability.config.CapabilitySettings;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.capability.port.WorkspaceCapabilityContribution;
import com.massimotter.weave.backend.capability.port.WorkspaceCapabilityDefinition;
import com.massimotter.weave.backend.capability.readiness.CapabilityAvailability;
import java.util.List;
import java.util.Map;

/** Owns Office capability declarations and support-safe readiness policy. */
public final class OfficeWorkspaceCapabilities {

    private OfficeWorkspaceCapabilities() {}

    public static WorkspaceCapabilityContribution contribution(
            OfficeWorkspaceCapabilityProperties properties) {
        CapabilitySettings settings = properties.documentsCollaboration();
        var definition = new WorkspaceCapabilityDefinition(
                "office", "documents/collaboration", settings,
                List.of("documents.view", "documents.edit"),
                List.of("documents.view", "documents.edit"), List.of("documents.view"),
                List.of(), Map.of(),
                null, List.of(), 60);
        return new WorkspaceCapabilityContribution(
                definition,
                shell -> CapabilityAvailability.standalone(
                        settings, WorkspaceCapabilityReadiness.UNAVAILABLE,
                        "Documents and collaboration are available through Weave."));
    }
}

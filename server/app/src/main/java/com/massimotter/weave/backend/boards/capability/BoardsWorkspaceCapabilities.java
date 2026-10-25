package com.massimotter.weave.backend.boards.capability;

import com.massimotter.weave.backend.capability.config.CapabilitySettings;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.capability.port.WorkspaceCapabilityContribution;
import com.massimotter.weave.backend.capability.port.WorkspaceCapabilityDefinition;
import com.massimotter.weave.backend.capability.readiness.CapabilityAvailability;
import java.util.List;
import java.util.Map;

/** Owns Boards capability declarations and support-safe readiness policy. */
public final class BoardsWorkspaceCapabilities {

    private BoardsWorkspaceCapabilities() {}

    public static WorkspaceCapabilityContribution contribution(
            BoardsWorkspaceCapabilityProperties properties) {
        CapabilitySettings settings = properties.boards();
        var definition = new WorkspaceCapabilityDefinition(
                "boards", "boards/tasks", settings,
                List.of("boards.read", "boards.update_task"),
                List.of("boards.read", "boards.update_task"), List.of("boards.read"),
                List.of(), Map.of(),
                null, List.of("boards.read", "boards.update_task", "boards.sync_workspace", "boards.link_decision"), 40);
        return new WorkspaceCapabilityContribution(
                definition,
                shell -> CapabilityAvailability.standalone(
                        settings, WorkspaceCapabilityReadiness.UNAVAILABLE,
                        "Boards and tasks are available through Weave."));
    }
}

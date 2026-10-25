package com.massimotter.weave.backend.meetings.capability;

import com.massimotter.weave.backend.capability.config.CapabilitySettings;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.capability.port.WorkspaceCapabilityContribution;
import com.massimotter.weave.backend.capability.port.WorkspaceCapabilityDefinition;
import com.massimotter.weave.backend.capability.readiness.CapabilityAvailability;
import java.util.List;
import java.util.Map;

/** Owns Meetings capability declarations and support-safe readiness policy. */
public final class MeetingsWorkspaceCapabilities {

    private MeetingsWorkspaceCapabilities() {}

    public static WorkspaceCapabilityContribution contribution(
            MeetingsWorkspaceCapabilityProperties properties) {
        CapabilitySettings settings = properties.meetingsCalls();
        var definition = new WorkspaceCapabilityDefinition(
                "meetings", "meetings/calls", settings,
                List.of("meetings.join", "meetings.host"),
                List.of("meetings.join", "meetings.host"), List.of("meetings.join"),
                List.of(), Map.of(),
                null, List.of(), 50);
        return new WorkspaceCapabilityContribution(
                definition,
                shell -> CapabilityAvailability.standalone(
                        settings, WorkspaceCapabilityReadiness.UNAVAILABLE,
                        "Meetings and calls are available through Weave."));
    }
}

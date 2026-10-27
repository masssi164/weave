package com.massimotter.weave.backend.calendar.capability;

import com.massimotter.weave.backend.capability.config.CapabilitySettings;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.capability.port.WorkspaceCapabilityContribution;
import com.massimotter.weave.backend.capability.port.WorkspaceCapabilityDefinition;
import com.massimotter.weave.backend.capability.readiness.CapabilityAvailability;
import com.massimotter.weave.backend.health.ProviderCapabilityHealthService;
import java.util.List;
import java.util.Map;

/** Owns Calendar capability declarations and support-safe readiness policy. */
public final class CalendarWorkspaceCapabilities {

    private CalendarWorkspaceCapabilities() {}

    public static WorkspaceCapabilityContribution contribution(
            CalendarWorkspaceCapabilityProperties properties,
            ProviderCapabilityHealthService health) {
        CapabilitySettings settings = properties.calendar();
        var definition = new WorkspaceCapabilityDefinition(
                "calendar", "calendar", settings,
                List.of("calendar.read", "calendar.manage_events"),
                List.of("calendar.read", "calendar.manage_events"), List.of("calendar.read", "calendar.manage_events"),
                List.of(), Map.of(),
                null, List.of("calendar.read", "calendar.manage_events", "calendar.thread_refs"), 30);
        return new WorkspaceCapabilityContribution(
                definition,
                shell -> CapabilityAvailability.providerBacked(
                        settings, WorkspaceCapabilityReadiness.UNAVAILABLE,
                        health.cached("calendar").orElse(null), "Calendar is available through the Weave workspace."));
    }
}

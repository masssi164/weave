package com.massimotter.weave.backend.calendar.discovery;

import com.massimotter.weave.backend.capability.WorkspaceCapabilityService;
import com.massimotter.weave.backend.discovery.api.PlatformStatusResponse;
import com.massimotter.weave.backend.discovery.application.ModulePlatformStatus;
import com.massimotter.weave.backend.discovery.port.PlatformDiagnosticContribution;

/** Public diagnostics for the installed Calendar runtime. */
public final class CalendarPlatformDiagnostics implements PlatformDiagnosticContribution {
    private final WorkspaceCapabilityService capabilities;

    public CalendarPlatformDiagnostics(WorkspaceCapabilityService capabilities) {
        this.capabilities = capabilities;
    }

    @Override
    public String id() {
        return "calendar";
    }

    @Override
    public int order() {
        return 4;
    }

    @Override
    public String label() {
        return "Calendar module";
    }

    @Override
    public PlatformStatusResponse.DiagnosticStatus status(PlatformStatusResponse.DiagnosticStatus auth) {
        return ModulePlatformStatus.evaluate(
                "calendar", "Calendar",
                capabilities.settings("calendar").orElseThrow(
                        () -> new IllegalStateException("Installed Calendar has no capability declaration")),
                auth,
                "Set WEAVE_CALDAV_BASE_URL or WEAVE_NEXTCLOUD_BASE_URL so calendar can reach Nextcloud CalDAV.",
                "Enable WEAVE_WORKSPACE_CALENDAR_ENABLED when calendar should be available.");
    }
}

package com.massimotter.weave.backend.files.discovery;

import com.massimotter.weave.backend.capability.WorkspaceCapabilityService;
import com.massimotter.weave.backend.discovery.api.PlatformStatusResponse;
import com.massimotter.weave.backend.discovery.application.ModulePlatformStatus;
import com.massimotter.weave.backend.discovery.port.PlatformDiagnosticContribution;

/** Public diagnostics for the installed Files runtime. */
public final class FilesPlatformDiagnostics implements PlatformDiagnosticContribution {
    private final WorkspaceCapabilityService capabilities;

    public FilesPlatformDiagnostics(WorkspaceCapabilityService capabilities) {
        this.capabilities = capabilities;
    }

    @Override
    public String id() {
        return "files";
    }

    @Override
    public int order() {
        return 3;
    }

    @Override
    public String label() {
        return "Files module";
    }

    @Override
    public PlatformStatusResponse.DiagnosticStatus status(PlatformStatusResponse.DiagnosticStatus auth) {
        return ModulePlatformStatus.evaluate(
                "files", "Files",
                capabilities.settings("files").orElseThrow(
                        () -> new IllegalStateException("Installed Files has no capability declaration")),
                auth,
                "Set WEAVE_NEXTCLOUD_BASE_URL to the canonical Nextcloud URL, for example https://files.weave.test.",
                "Enable WEAVE_WORKSPACE_FILES_ENABLED when files should be available.");
    }
}

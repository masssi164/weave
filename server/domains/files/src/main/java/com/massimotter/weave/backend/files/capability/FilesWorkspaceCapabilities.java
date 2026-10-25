package com.massimotter.weave.backend.files.capability;

import com.massimotter.weave.backend.capability.config.CapabilitySettings;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityStatusResponse;
import com.massimotter.weave.backend.capability.api.WorkspaceReleaseReadinessCheckResponse;
import com.massimotter.weave.backend.capability.port.WorkspaceCapabilityContribution;
import com.massimotter.weave.backend.capability.port.WorkspaceCapabilityDefinition;
import com.massimotter.weave.backend.capability.readiness.CapabilityAvailability;
import com.massimotter.weave.backend.health.ProviderCapabilityHealthService;
import java.util.List;
import java.util.Map;

/** Owns Files capability declarations and support-safe readiness policy. */
public final class FilesWorkspaceCapabilities {

    private FilesWorkspaceCapabilities() {}

    public static WorkspaceCapabilityContribution contribution(
            FilesWorkspaceCapabilityProperties properties,
            ProviderCapabilityHealthService health) {
        CapabilitySettings settings = properties.files();
        var definition = new WorkspaceCapabilityDefinition(
                "files", "files", settings,
                List.of("files.read", "files.upload"),
                List.of("files.read", "files.upload"), List.of("files.read", "files.upload"),
                List.of(), Map.of(),
                null, List.of("files.read", "files.upload", "files.download", "files.delete", "files.share"), 20);
        return new WorkspaceCapabilityContribution(
                definition,
                shell -> CapabilityAvailability.dependent(
                        settings, shell, health.cached("files").orElse(null),
                        "Files are available through Weave.", "Files need admin attention before members can use them reliably. "),

                status -> java.util.Optional.of(releaseCheck(status, settings)));
    }

    private static WorkspaceReleaseReadinessCheckResponse releaseCheck(
            WorkspaceCapabilityStatusResponse status,
            CapabilitySettings settings) {
        WorkspaceCapabilityReadiness readiness = status.readiness();
        String dependencyUrl = settings.dependencyUrl();
        return switch (readiness) {
            case READY -> new WorkspaceReleaseReadinessCheckResponse(
                    "files",
                    "Files capability configuration",
                    readiness,
                    "Files capability configuration is ready. Inspect current binding status and operation evidence separately.",
                    null);
            case DEGRADED -> new WorkspaceReleaseReadinessCheckResponse(
                    "files",
                    "Files capability configuration",
                    readiness,
                    "Files capability configuration is incomplete or its cached availability is limited.",
                    "Review Files capability configuration and current binding diagnostics at /api/admin/providers/status.");
            case BLOCKED -> new WorkspaceReleaseReadinessCheckResponse(
                    "files",
                    "Files capability configuration",
                    readiness,
                    "Files depend on shell access, which is currently blocked by the auth contract.",
                    "Fix the first-party auth contract first, then re-check files readiness.");
            case UNAVAILABLE -> new WorkspaceReleaseReadinessCheckResponse(
                    "files",
                    "Files capability configuration",
                    readiness,
                    dependencyUrl != null && !dependencyUrl.isBlank()
                            ? "Files are disabled even though a dependency is configured."
                            : "Files are disabled for this workspace snapshot.",
                    "Enable WEAVE_WORKSPACE_FILES_ENABLED for the workspace when files should ship.");
        };
    }
}

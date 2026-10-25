package com.massimotter.weave.backend.chat.capability;

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

/** Owns Chat capability declarations and support-safe readiness policy. */
public final class ChatWorkspaceCapabilities {

    private ChatWorkspaceCapabilities() {}

    public static WorkspaceCapabilityContribution contribution(
            ChatWorkspaceCapabilityProperties properties,
            ProviderCapabilityHealthService health) {
        CapabilitySettings settings = properties.chat();
        var definition = new WorkspaceCapabilityDefinition(
                "chat", "chat", settings,
                List.of("chat.read", "chat.send"),
                List.of("chat.read", "chat.send"), List.of("chat.read", "chat.send"),
                List.of(), Map.of(),
                null, List.of("chat.read", "chat.send", "chat.channels", "chat.moderate"), 10);
        return new WorkspaceCapabilityContribution(
                definition,
                shell -> CapabilityAvailability.dependent(
                        settings, shell, health.cached("chat").orElse(null),
                        "Chat is available through the Weave workspace.", "Chat needs admin attention before members can use it reliably. "),

                status -> java.util.Optional.of(releaseCheck(status, settings)));
    }

    private static WorkspaceReleaseReadinessCheckResponse releaseCheck(
            WorkspaceCapabilityStatusResponse status,
            CapabilitySettings settings) {
        WorkspaceCapabilityReadiness readiness = status.readiness();
        String dependencyUrl = settings.dependencyUrl();
        return switch (readiness) {
            case READY -> new WorkspaceReleaseReadinessCheckResponse(
                    "chat",
                    "Matrix chat route",
                    readiness,
                    "Chat capability configuration is ready. This snapshot does not verify live Matrix operations.",
                    null);
            case DEGRADED -> new WorkspaceReleaseReadinessCheckResponse(
                    "chat",
                    "Matrix chat route",
                    readiness,
                    "Chat is enabled but no Matrix route is configured yet.",
                    "Set WEAVE_MATRIX_BASE_URL to the southbound Matrix provider URL; clients receive the Weave facade from the API origin.");
            case BLOCKED -> new WorkspaceReleaseReadinessCheckResponse(
                    "chat",
                    "Matrix chat route",
                    readiness,
                    "Chat depends on shell access, which is currently blocked by the auth contract.",
                    "Fix the first-party auth contract first, then re-check chat readiness.");
            case UNAVAILABLE -> new WorkspaceReleaseReadinessCheckResponse(
                    "chat",
                    "Matrix chat route",
                    readiness,
                    dependencyUrl != null && !dependencyUrl.isBlank()
                            ? "Chat is disabled even though a Matrix route is configured."
                            : "Chat is disabled for this workspace snapshot.",
                    "Enable WEAVE_WORKSPACE_CHAT_ENABLED for the workspace when chat should ship.");
        };
    }
}

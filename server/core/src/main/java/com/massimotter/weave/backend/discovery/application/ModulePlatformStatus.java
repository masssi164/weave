package com.massimotter.weave.backend.discovery.application;

import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.capability.config.CapabilitySettings;
import com.massimotter.weave.backend.discovery.api.PlatformStatusResponse;

/** Shared evaluation of an installed module's explicit diagnostic settings. */
public final class ModulePlatformStatus {
    private ModulePlatformStatus() {}

    public static PlatformStatusResponse.DiagnosticStatus evaluate(
            String key,
            String label,
            CapabilitySettings capability,
            PlatformStatusResponse.DiagnosticStatus auth,
            String missingRouteAction,
            String disabledAction) {
        if (capability == null) return null;
        if (!capability.enabled()) {
            return new PlatformStatusResponse.DiagnosticStatus(
                    "disabled",
                    "unavailable",
                    label + " is disabled for this workspace snapshot.",
                    disabledAction);
        }
        if ("blocked".equals(auth.readiness())) {
            return new PlatformStatusResponse.DiagnosticStatus(
                    "blocked",
                    "blocked",
                    label + " depends on shell access, which is currently blocked by the auth contract.",
                    "Fix the first-party auth contract first, then re-check " + key + " readiness.");
        }
        if (capability.readiness() != null) {
            return overriddenStatus(label, capability.readiness());
        }
        if (hasText(capability.dependencyUrl())) {
            return new PlatformStatusResponse.DiagnosticStatus(
                    "up",
                    "ready",
                    label + " has a configured public dependency route.",
                    null);
        }
        return new PlatformStatusResponse.DiagnosticStatus(
                "degraded",
                "degraded",
                label + " is enabled but no dependency route is configured.",
                missingRouteAction);
    }

    private static PlatformStatusResponse.DiagnosticStatus overriddenStatus(
            String label,
            WorkspaceCapabilityReadiness readiness) {
        String normalized = readiness.name().toLowerCase();
        String status = switch (readiness) {
            case READY -> "up";
            case DEGRADED -> "degraded";
            case BLOCKED -> "blocked";
            case UNAVAILABLE -> "disabled";
        };
        String action = readiness == WorkspaceCapabilityReadiness.READY
                ? null
                : "Review the configured " + label + " readiness override and downstream service wiring.";
        return new PlatformStatusResponse.DiagnosticStatus(
                status,
                normalized,
                label + " readiness is set by an explicit backend runtime override.",
                action);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

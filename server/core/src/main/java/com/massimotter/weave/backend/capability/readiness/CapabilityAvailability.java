package com.massimotter.weave.backend.capability.readiness;

import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.capability.config.CapabilitySettings;
import com.massimotter.weave.backend.health.api.ProviderCapabilityHealthResponse.CapabilityHealth;
import static com.massimotter.weave.backend.capability.api.WorkspaceCapabilityReadiness.*;

/** Shared readiness combination rules; business declarations and member-facing copy belong to domains. */
public record CapabilityAvailability(
        WorkspaceCapabilityReadiness readiness,
        String memberImpact,
        String supportSafeCode) {
    public CapabilityAvailability(WorkspaceCapabilityReadiness readiness, String memberImpact) {
        this(readiness, memberImpact, null);
    }

    public static CapabilityAvailability standalone(CapabilitySettings settings,
            WorkspaceCapabilityReadiness defaultReadiness, String readyImpact) {
        if (!settings.enabled()) {
            return disabled();
        }
        if (settings.readiness() != null) {
            return new CapabilityAvailability(settings.readiness(), readyImpact);
        }
        return new CapabilityAvailability(defaultReadiness, defaultReadiness == UNAVAILABLE
                ? "This capability is not ready for members in this workspace. Ask an admin to review Workspace Health."
                : readyImpact);
    }

    public static CapabilityAvailability dependent(CapabilitySettings settings,
            WorkspaceCapabilityReadiness shellReadiness, CapabilityHealth health,
            String readyImpact, String providerAttention) {
        if (!settings.enabled()) {
            return disabled();
        }
        if (shellReadiness == BLOCKED) {
            return new CapabilityAvailability(BLOCKED,
                "Sign-in or workspace SSO must be ready before this capability can be used.");
        }
        if (health != null) {
            var readiness = effective(settings.readiness(), providerReadiness(health.state()));
            return new CapabilityAvailability(readiness, readiness == READY ? readyImpact
                    : providerAttention + "Ask an admin to inspect Workspace Health.", health.supportSafeCode());
        }
        if (settings.readiness() != null) {
            return new CapabilityAvailability(settings.readiness(), readyImpact);
        }
        if (settings.dependencyUrl() != null && !settings.dependencyUrl().isBlank()) {
            return new CapabilityAvailability(READY,
                readyImpact);
        }
        return new CapabilityAvailability(DEGRADED, "This capability is degraded. Ask an admin to inspect Workspace Health.");
    }

    public static CapabilityAvailability providerBacked(CapabilitySettings settings,
            WorkspaceCapabilityReadiness defaultReadiness, CapabilityHealth health, String readyImpact) {
        if (!settings.enabled() || health == null) {
            return standalone(settings, defaultReadiness, readyImpact);
        }
        var readiness = effective(settings.readiness(), providerReadiness(health.state()));
        return new CapabilityAvailability(readiness, readiness == READY ? readyImpact
                : "This capability needs admin attention. Other workspace areas remain available while it recovers.",
                health.supportSafeCode());
    }

    private static CapabilityAvailability disabled() {
        return new CapabilityAvailability(UNAVAILABLE, "This capability is disabled by workspace policy.");
    }

    private static WorkspaceCapabilityReadiness effective(WorkspaceCapabilityReadiness configured,
                WorkspaceCapabilityReadiness observed) {
        if (configured == BLOCKED || configured == UNAVAILABLE) {
            return configured;
        }
        if (configured == DEGRADED) {
            return observed == UNAVAILABLE ? UNAVAILABLE : DEGRADED;
        }
        return observed;
    }

    private static WorkspaceCapabilityReadiness providerReadiness(String state) {
        return switch (state == null ? "" : state) {
            case "available" -> READY;
            case "unavailable" -> UNAVAILABLE;
            default -> DEGRADED;
        };
    }
}

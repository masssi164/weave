package com.massimotter.weave.backend.files.discovery;

import com.massimotter.weave.backend.discovery.api.PlatformStatusResponse;
import com.massimotter.weave.backend.discovery.config.PlatformContractProperties;
import com.massimotter.weave.backend.discovery.port.PlatformDiagnosticContribution;

/** Support-route metadata only; this observation performs no remote readiness probe. */
public final class NextcloudPlatformDiagnostics implements PlatformDiagnosticContribution {
    private final PlatformContractProperties platformProperties;

    public NextcloudPlatformDiagnostics(PlatformContractProperties platformProperties) {
        this.platformProperties = platformProperties;
    }

    @Override
    public String id() {
        return "nextcloud";
    }

    @Override
    public int order() {
        return 5;
    }

    @Override
    public String label() {
        return "Nextcloud route";
    }

    @Override
    public PlatformStatusResponse.DiagnosticStatus status(PlatformStatusResponse.DiagnosticStatus auth) {
        return nextcloudStatus();
    }

    private PlatformStatusResponse.DiagnosticStatus nextcloudStatus() {
        if (hasText(platformProperties.nextcloudBaseUrl())) {
            return new PlatformStatusResponse.DiagnosticStatus(
                    "up",
                    "ready",
                    "The support-safe public Nextcloud fallback route is configured for files/calendar diagnostics.",
                    null);
        }
        return new PlatformStatusResponse.DiagnosticStatus(
                "degraded",
                "degraded",
                "The support-safe public Nextcloud fallback route is not configured.",
                "Set WEAVE_NEXTCLOUD_PUBLIC_BASE_URL to the public Nextcloud/admin/protocol origin, for example https://files.weave.test.");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

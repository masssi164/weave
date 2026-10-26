package com.massimotter.weave.backend.chat.discovery;

import com.massimotter.weave.backend.api.error.ApiErrorException;
import com.massimotter.weave.backend.capability.WorkspaceCapabilityService;
import com.massimotter.weave.backend.capability.config.CapabilitySettings;
import com.massimotter.weave.backend.chat.config.MatrixChatProperties;
import com.massimotter.weave.backend.discovery.api.PlatformStatusResponse;
import com.massimotter.weave.backend.discovery.application.ModulePlatformStatus;
import com.massimotter.weave.backend.discovery.config.PlatformContractProperties;
import com.massimotter.weave.backend.discovery.port.MatrixPlatformContribution;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Matrix facade origins and cryptographic diagnostics belong to Chat. No provider is contacted. */
public final class ChatMatrixPlatformContribution implements MatrixPlatformContribution {
    private final PlatformContractProperties platformProperties;
    private final MatrixChatProperties matrixProperties;
    private final WorkspaceCapabilityService capabilities;
    private final String matrixFacadeBaseUrl;
    private final String matrixFacadeServerName;

    public ChatMatrixPlatformContribution(
            PlatformContractProperties platformProperties,
            MatrixChatProperties matrixProperties,
            WorkspaceCapabilityService capabilities,
            String matrixFacadeBaseUrl,
            String matrixFacadeServerName) {
        this.platformProperties = platformProperties;
        this.matrixProperties = matrixProperties;
        this.capabilities = capabilities;
        this.matrixFacadeBaseUrl = matrixFacadeBaseUrl;
        this.matrixFacadeServerName = matrixFacadeServerName;
    }

    @Override
    public String advertisedBaseUrl() {
        try {
            matrixFacadeOrigin(platformProperties.matrixHomeserverUrl());
            return matrixFacadeOrigin(matrixFacadeBaseUrl);
        } catch (IllegalArgumentException invalidRoute) {
            throw new ApiErrorException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "matrix-facade-origin-unavailable",
                    "The Weave Matrix Client-Server origin is not configured safely.",
                    Map.of("module", "chat", "diagnosticsRedacted", true));
        }
    }

    @Override
    public PlatformStatusResponse.MatrixStatus status(PlatformStatusResponse.DiagnosticStatus auth) {
        PlatformStatusResponse.DiagnosticStatus status = ModulePlatformStatus.evaluate(
                "matrix",
                "Matrix chat",
                capabilities.settings("chat").orElseThrow(() -> new IllegalStateException("Installed Chat has no capability declaration")),
                auth,
                "Set WEAVE_MATRIX_BASE_URL to the southbound Matrix provider URL; clients receive the Weave facade from the API origin.",
                "Enable WEAVE_WORKSPACE_CHAT_ENABLED when chat should be available.");
        if (capabilities.settings("chat").map(CapabilitySettings::enabled).orElse(false) && !matrixFacadeAuthorityMatches()) {
            status = new PlatformStatusResponse.DiagnosticStatus(
                    "blocked",
                    "blocked",
                    "The advertised Weave Matrix URL, routed facade URL, and Matrix server name disagree.",
                    "Configure the public Weave Matrix facade origin and server name consistently; do not advertise a southbound provider URL.");
        }
        return new PlatformStatusResponse.MatrixStatus(
                status.status(),
                status.readiness(),
                status.message(),
                status.action(),
                matrixProperties.federationEnabled(),
                "ready".equals(status.readiness()) && matrixProperties.e2ee().fullyValidated(),
                e2eeStatus(),
                backendBoundary());
    }

    private boolean matrixFacadeAuthorityMatches() {
        try {
            URI advertised = URI.create(platformProperties.matrixHomeserverUrl());
            URI routed = URI.create(matrixFacadeBaseUrl);
            if (!isHttpsOrigin(advertised) || !isHttpsOrigin(routed)) {
                return false;
            }
            String advertisedOrigin = advertised.getScheme().toLowerCase(Locale.ROOT)
                    + "://" + advertised.getRawAuthority().toLowerCase(Locale.ROOT);
            String routedOrigin = routed.getScheme().toLowerCase(Locale.ROOT)
                    + "://" + routed.getRawAuthority().toLowerCase(Locale.ROOT);
            String serverName = matrixFacadeServerName.toLowerCase(Locale.ROOT);
            return advertisedOrigin.equals(routedOrigin)
                    && (serverName.equals(advertised.getHost().toLowerCase(Locale.ROOT))
                    || serverName.equals(advertised.getRawAuthority().toLowerCase(Locale.ROOT)));
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return false;
        }
    }

    private boolean isHttpsOrigin(URI url) {
        return "https".equalsIgnoreCase(url.getScheme())
                && url.getHost() != null
                && url.getRawUserInfo() == null
                && (url.getRawPath() == null || url.getRawPath().isEmpty() || "/".equals(url.getRawPath()))
                && url.getRawQuery() == null
                && url.getRawFragment() == null;
    }

    private PlatformStatusResponse.E2eeStatus e2eeStatus() {
        MatrixChatProperties.E2ee e2ee = matrixProperties.e2ee();
        boolean fullyValidated = e2ee.fullyValidated();
        return new PlatformStatusResponse.E2eeStatus(
                fullyValidated ? "validated" : "not_validated",
                e2ee.statusSource(),
                e2ee.encryptedRoomsValidated(),
                e2ee.deviceVerificationValidated(),
                e2ee.keyBackupValidated(),
                e2ee.lostDeviceRecoveryValidated(),
                e2ee.multiDeviceValidated(),
                e2ee.accessibilityReviewed(),
                fullyValidated
                        ? null
                        : "Do not claim Matrix chat E2EE complete until encrypted-room, device verification, key backup/recovery, lost-device, multi-device, and accessibility gates are validated.");
    }

    private PlatformStatusResponse.MatrixBackendBoundary backendBoundary() {
        MatrixChatProperties.BackendBoundary boundary = matrixProperties.backendBoundary();
        return new PlatformStatusResponse.MatrixBackendBoundary(
                false,
                List.of(
                        "room_id",
                        "event_id",
                        "sender_id",
                        "origin_server_ts",
                        "membership_state",
                        "room_encryption_algorithm",
                        "redacted_state"),
                "Encrypted Matrix message bodies are opaque to backend diagnostics and must not be required for product readiness.",
                boundary.agentParticipation(),
                boundary.connectorWritePolicy());
    }

    private static String matrixFacadeOrigin(String value) {
        URI facade = URI.create(value);
        if (!"https".equalsIgnoreCase(facade.getScheme()) || facade.getHost() == null
                || facade.getRawAuthority() == null || facade.getRawUserInfo() != null
                || facade.getRawQuery() != null || facade.getRawFragment() != null
                || (facade.getRawPath() != null && !facade.getRawPath().isEmpty()
                && !"/".equals(facade.getRawPath()))) {
            throw new IllegalArgumentException("The advertised Weave Matrix facade must be a credential-free HTTPS origin");
        }
        return "https://" + facade.getRawAuthority();
    }

}

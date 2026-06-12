package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.config.MatrixChatProperties;
import com.massimotter.weave.backend.config.PlatformContractProperties;
import com.massimotter.weave.backend.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.model.PlatformConfigResponse;
import com.massimotter.weave.backend.model.PlatformStatusResponse;
import com.massimotter.weave.backend.model.WorkspaceCapabilityReadiness;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.stereotype.Service;

@Service
public class PlatformContractService {

    private final OAuth2ResourceServerProperties resourceServerProperties;
    private final PlatformContractProperties platformProperties;
    private final MatrixChatProperties matrixProperties;
    private final WeaveSecurityProperties securityProperties;
    private final WorkspaceCapabilityProperties workspaceProperties;

    public PlatformContractService(
            OAuth2ResourceServerProperties resourceServerProperties,
            PlatformContractProperties platformProperties,
            MatrixChatProperties matrixProperties,
            WeaveSecurityProperties securityProperties,
            WorkspaceCapabilityProperties workspaceProperties) {
        this.resourceServerProperties = resourceServerProperties;
        this.platformProperties = platformProperties;
        this.matrixProperties = matrixProperties;
        this.securityProperties = securityProperties;
        this.workspaceProperties = workspaceProperties;
    }

    public PlatformConfigResponse config() {
        return new PlatformConfigResponse(
                platformProperties.publicBaseUrl(),
                platformProperties.apiBaseUrl(),
                platformProperties.authBaseUrl(),
                oidcIssuerUrl(),
                securityProperties.clientId(),
                platformProperties.matrixHomeserverUrl(),
                platformProperties.filesProductUrl(),
                platformProperties.calendarProductUrl(),
                platformProperties.nextcloudBaseUrl(),
                new PlatformConfigResponse.Targets(
                        platformProperties.targets().mobile(),
                        platformProperties.targets().desktop(),
                        platformProperties.targets().web()),
                new PlatformConfigResponse.Features(
                        workspaceProperties.chat().enabled(),
                        workspaceProperties.chat().enabled() && matrixProperties.e2ee().fullyValidated(),
                        matrixProperties.federationEnabled(),
                        workspaceProperties.files().enabled(),
                        workspaceProperties.calendar().enabled()));
    }

    private String oidcIssuerUrl() {
        String configuredIssuer = resourceServerProperties.getJwt().getIssuerUri();
        if (hasText(configuredIssuer)) {
            return configuredIssuer;
        }
        return joinUrlPath(platformProperties.authBaseUrl(), "/realms/weave");
    }

    private String joinUrlPath(String baseUrl, String path) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1) + path;
        }
        return baseUrl + path;
    }

    public PlatformStatusResponse status(String requestId) {
        PlatformStatusResponse.DiagnosticStatus backend = new PlatformStatusResponse.DiagnosticStatus(
                "up",
                "ready",
                "The Weave backend process is serving product API diagnostics.",
                null);
        PlatformStatusResponse.DiagnosticStatus auth = authStatus();
        PlatformStatusResponse.MatrixStatus matrix = matrixStatus(auth);
        PlatformStatusResponse.DiagnosticStatus files = moduleStatus(
                "files",
                "Files",
                workspaceProperties.files(),
                auth,
                "Set WEAVE_NEXTCLOUD_BASE_URL to the canonical Nextcloud URL, for example https://files.weave.test.",
                "Enable WEAVE_WORKSPACE_FILES_ENABLED when files should be available.");
        PlatformStatusResponse.DiagnosticStatus calendar = moduleStatus(
                "calendar",
                "Calendar",
                workspaceProperties.calendar(),
                auth,
                "Set WEAVE_CALDAV_BASE_URL or WEAVE_NEXTCLOUD_BASE_URL so calendar can reach Nextcloud CalDAV.",
                "Enable WEAVE_WORKSPACE_CALENDAR_ENABLED when calendar should be available.");
        PlatformStatusResponse.DiagnosticStatus nextcloud = nextcloudStatus();

        List<PlatformStatusResponse.DiagnosticCheck> checks = List.of(
                check("backend", "Backend API", backend),
                check("auth", "Keycloak auth", auth),
                check("matrix", "Matrix chat", matrix),
                check("files", "Files module", files),
                check("calendar", "Calendar module", calendar),
                check("nextcloud", "Nextcloud route", nextcloud));

        return new PlatformStatusResponse(
                requestId,
                backend,
                auth,
                matrix,
                files,
                calendar,
                nextcloud,
                checks,
                actions(checks));
    }

    private PlatformStatusResponse.DiagnosticStatus authStatus() {
        if (!workspaceProperties.shellAccess().enabled()) {
            return new PlatformStatusResponse.DiagnosticStatus(
                    "disabled",
                    "unavailable",
                    "Shell access is disabled for this backend runtime.",
                    "Enable WEAVE_WORKSPACE_SHELL_ACCESS_ENABLED when authenticated Weave product APIs should serve traffic.");
        }

        List<String> missing = new ArrayList<>();
        if (!hasText(resourceServerProperties.getJwt().getIssuerUri())) {
            missing.add("WEAVE_OIDC_ISSUER_URI");
        }
        if (!hasText(securityProperties.requiredAudience())) {
            missing.add("WEAVE_OIDC_REQUIRED_AUDIENCE");
        }
        if (!hasText(securityProperties.clientId())) {
            missing.add("WEAVE_CLIENT_ID");
        }

        if (missing.isEmpty()) {
            return new PlatformStatusResponse.DiagnosticStatus(
                    "up",
                    "ready",
                    "JWT issuer, audience, client, and workspace-scope validation are configured.",
                    null);
        }

        return new PlatformStatusResponse.DiagnosticStatus(
                "blocked",
                "blocked",
                "Missing auth runtime inputs: " + String.join(", ", missing) + ".",
                "Provide the missing auth runtime inputs for the backend: " + String.join(", ", missing) + ".");
    }

    private PlatformStatusResponse.MatrixStatus matrixStatus(PlatformStatusResponse.DiagnosticStatus auth) {
        PlatformStatusResponse.DiagnosticStatus status = moduleStatus(
                "matrix",
                "Matrix chat",
                workspaceProperties.chat(),
                auth,
                "Set WEAVE_MATRIX_HOMESERVER_URL to the public Matrix base URL, for example https://matrix.weave.test.",
                "Enable WEAVE_WORKSPACE_CHAT_ENABLED when chat should be available.");
        return new PlatformStatusResponse.MatrixStatus(
                status.status(),
                status.readiness(),
                status.message(),
                status.action(),
                matrixProperties.federationEnabled(),
                workspaceProperties.chat().enabled() && matrixProperties.e2ee().fullyValidated(),
                e2eeStatus(),
                backendBoundary());
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

    private PlatformStatusResponse.DiagnosticStatus moduleStatus(
            String key,
            String label,
            WorkspaceCapabilityProperties.Capability capability,
            PlatformStatusResponse.DiagnosticStatus auth,
            String missingRouteAction,
            String disabledAction) {
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

    private PlatformStatusResponse.DiagnosticStatus overriddenStatus(
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

    private PlatformStatusResponse.DiagnosticStatus nextcloudStatus() {
        if (hasText(platformProperties.nextcloudBaseUrl())) {
            return new PlatformStatusResponse.DiagnosticStatus(
                    "up",
                    "ready",
                    "The canonical Nextcloud technical route is configured for backend files/calendar diagnostics.",
                    null);
        }
        return new PlatformStatusResponse.DiagnosticStatus(
                "degraded",
                "degraded",
                "The canonical Nextcloud technical route is not configured.",
                "Set WEAVE_NEXTCLOUD_BASE_URL to the raw Nextcloud/admin/protocol origin, for example https://files.weave.test.");
    }

    private PlatformStatusResponse.DiagnosticCheck check(
            String key,
            String label,
            PlatformStatusResponse.DiagnosticStatus status) {
        return new PlatformStatusResponse.DiagnosticCheck(
                key,
                label,
                status.status(),
                status.readiness(),
                status.message(),
                status.action());
    }

    private PlatformStatusResponse.DiagnosticCheck check(
            String key,
            String label,
            PlatformStatusResponse.MatrixStatus status) {
        return new PlatformStatusResponse.DiagnosticCheck(
                key,
                label,
                status.status(),
                status.readiness(),
                status.message(),
                status.action());
    }

    private List<String> actions(List<PlatformStatusResponse.DiagnosticCheck> checks) {
        return checks.stream()
                .map(PlatformStatusResponse.DiagnosticCheck::action)
                .filter(this::hasText)
                .distinct()
                .toList();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

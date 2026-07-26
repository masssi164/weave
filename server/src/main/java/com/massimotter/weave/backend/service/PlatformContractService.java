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
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PlatformContractService {

    private final OAuth2ResourceServerProperties resourceServerProperties;
    private final PlatformContractProperties platformProperties;
    private final MatrixChatProperties matrixProperties;
    private final WeaveSecurityProperties securityProperties;
    private final WorkspaceCapabilityProperties workspaceProperties;

    @Value("${weave.platform.release-posture:dogfood}")
    private String releasePosture = "dogfood";

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
                1,
                platformProperties.publicBaseUrl(),
                platformProperties.apiBaseUrl(),
                new PlatformConfigResponse.Oidc(oidcIssuerUrl(), securityProperties.clientId()),
                new PlatformConfigResponse.Protocols(
                        platformProperties.matrixHomeserverUrl(),
                        joinUrlPath(platformProperties.apiBaseUrl(), "/dav/files"),
                        joinUrlPath(platformProperties.apiBaseUrl(), "/caldav")),
                releasePosture(),
                List.of(
                        domain("platform-identity", true, List.of(
                                "identity.sign_in", "identity.groups", "identity.roles",
                                "policy.read", "policy.manage")),
                        domain("chat", workspaceProperties.chat().enabled(), List.of(
                                "chat.read", "chat.send", "chat.channels", "chat.moderate")),
                        domain("files", workspaceProperties.files().enabled(), List.of(
                                "files.read", "files.upload", "files.download", "files.delete", "files.share")),
                        domain("calendar", workspaceProperties.calendar().enabled(), List.of(
                                "calendar.read", "calendar.manage_events", "calendar.thread_refs")),
                        domain("boards", workspaceProperties.boards().enabled(), List.of(
                                "boards.read", "boards.update_task", "boards.sync_workspace", "boards.link_decision")),
                        domain("health", true, List.of(
                                "health.read", "health.run_diagnostic", "health.export_support_bundle",
                                "backup.restore.verify"))),
                List.of());
    }

    private String releasePosture() {
        String normalized = releasePosture == null
                ? "dogfood"
                : releasePosture.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "development", "dogfood", "release_candidate", "stable" -> normalized;
            default -> throw new IllegalStateException("Unsupported Weave release posture");
        };
    }

    private PlatformConfigResponse.DomainCapability domain(
            String domain,
            boolean enabled,
            List<String> capabilities) {
        return new PlatformConfigResponse.DomainCapability(
                domain,
                enabled ? "available" : "not_configured",
                enabled ? capabilities : List.of(),
                null);
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
                "Set WEAVE_MATRIX_BASE_URL to the southbound Matrix provider URL; clients receive the Weave facade from the API origin.",
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
                    "The support-safe public Nextcloud fallback route is configured for files/calendar diagnostics.",
                    null);
        }
        return new PlatformStatusResponse.DiagnosticStatus(
                "degraded",
                "degraded",
                "The support-safe public Nextcloud fallback route is not configured.",
                "Set WEAVE_NEXTCLOUD_PUBLIC_BASE_URL to the public Nextcloud/admin/protocol origin, for example https://files.weave.test.");
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

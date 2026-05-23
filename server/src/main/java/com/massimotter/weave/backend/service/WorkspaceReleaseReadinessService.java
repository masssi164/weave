package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.model.WorkspaceCapabilitiesResponse;
import com.massimotter.weave.backend.model.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.model.WorkspaceReleaseReadinessCheckResponse;
import com.massimotter.weave.backend.model.WorkspaceReleaseReadinessResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceReleaseReadinessService {

    private final OAuth2ResourceServerProperties resourceServerProperties;
    private final WeaveSecurityProperties weaveSecurityProperties;
    private final WorkspaceCapabilityProperties workspaceCapabilityProperties;
    private final WorkspaceCapabilityService workspaceCapabilityService;

    public WorkspaceReleaseReadinessService(
            OAuth2ResourceServerProperties resourceServerProperties,
            WeaveSecurityProperties weaveSecurityProperties,
            WorkspaceCapabilityProperties workspaceCapabilityProperties,
            WorkspaceCapabilityService workspaceCapabilityService) {
        this.resourceServerProperties = resourceServerProperties;
        this.weaveSecurityProperties = weaveSecurityProperties;
        this.workspaceCapabilityProperties = workspaceCapabilityProperties;
        this.workspaceCapabilityService = workspaceCapabilityService;
    }

    public WorkspaceReleaseReadinessResponse snapshot() {
        WorkspaceCapabilitiesResponse capabilities = workspaceCapabilityService.snapshot();

        List<WorkspaceReleaseReadinessCheckResponse> checks = List.of(
                authCheck(capabilities),
                chatCheck(capabilities),
                filesCheck(capabilities));

        List<String> actions = checks.stream()
                .map(WorkspaceReleaseReadinessCheckResponse::action)
                .filter(this::hasText)
                .distinct()
                .toList();

        WorkspaceCapabilityReadiness readiness = overallReadiness(checks);
        return new WorkspaceReleaseReadinessResponse(readiness, summaryFor(readiness, actions), checks, actions);
    }

    private WorkspaceReleaseReadinessCheckResponse authCheck(WorkspaceCapabilitiesResponse capabilities) {
        ArrayList<String> missing = new ArrayList<>();
        if (!hasText(resourceServerProperties.getJwt().getIssuerUri())) {
            missing.add("WEAVE_OIDC_ISSUER_URI");
        }
        if (!hasText(weaveSecurityProperties.requiredAudience())) {
            missing.add("WEAVE_OIDC_REQUIRED_AUDIENCE");
        }
        if (!hasText(weaveSecurityProperties.clientId())) {
            missing.add("WEAVE_CLIENT_ID");
        }

        WorkspaceCapabilityReadiness readiness = capabilities.shellAccess().readiness();
        if (readiness == WorkspaceCapabilityReadiness.READY) {
            return new WorkspaceReleaseReadinessCheckResponse(
                    "auth-contract",
                    "First-party auth contract",
                    readiness,
                    "JWT issuer, audience, client, and workspace scope validation are configured.",
                    null);
        }

        return new WorkspaceReleaseReadinessCheckResponse(
                "auth-contract",
                "First-party auth contract",
                readiness,
                missing.isEmpty()
                        ? "Shell access is enabled but the first-party auth contract is not fully ready."
                        : "Missing auth runtime inputs: " + String.join(", ", missing) + ".",
                missing.isEmpty()
                        ? "Review the backend auth configuration so shell access can validate first-party Weave tokens."
                        : "Provide the missing auth runtime inputs for the backend: " + String.join(", ", missing) + ".");
    }

    private WorkspaceReleaseReadinessCheckResponse chatCheck(WorkspaceCapabilitiesResponse capabilities) {
        WorkspaceCapabilityReadiness readiness = capabilities.chat().readiness();
        String dependencyUrl = workspaceCapabilityProperties.chat().dependencyUrl();
        return switch (readiness) {
            case READY -> new WorkspaceReleaseReadinessCheckResponse(
                    "chat",
                    "Matrix chat route",
                    readiness,
                    "The workspace advertises a reachable Matrix route for minimum viable chat.",
                    null);
            case DEGRADED -> new WorkspaceReleaseReadinessCheckResponse(
                    "chat",
                    "Matrix chat route",
                    readiness,
                    "Chat is enabled but no Matrix route is configured yet.",
                    "Set WEAVE_MATRIX_HOMESERVER_URL to the public Matrix base URL, for example https://matrix.weave.local.");
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
                    hasText(dependencyUrl)
                            ? "Chat is disabled even though a Matrix route is configured."
                            : "Chat is disabled for this workspace snapshot.",
                    "Enable WEAVE_WORKSPACE_CHAT_ENABLED for the workspace when chat should ship.");
        };
    }

    private WorkspaceReleaseReadinessCheckResponse filesCheck(WorkspaceCapabilitiesResponse capabilities) {
        WorkspaceCapabilityReadiness readiness = capabilities.files().readiness();
        String dependencyUrl = workspaceCapabilityProperties.files().dependencyUrl();
        return switch (readiness) {
            case READY -> new WorkspaceReleaseReadinessCheckResponse(
                    "files",
                    "Nextcloud files route",
                    readiness,
                    "The workspace advertises a reachable Nextcloud route for files.",
                    null);
            case DEGRADED -> new WorkspaceReleaseReadinessCheckResponse(
                    "files",
                    "Nextcloud files route",
                    readiness,
                    "Files are enabled but no Nextcloud route is configured yet.",
                    "Set WEAVE_NEXTCLOUD_BASE_URL to the canonical Nextcloud URL, for example https://files.weave.local.");
            case BLOCKED -> new WorkspaceReleaseReadinessCheckResponse(
                    "files",
                    "Nextcloud files route",
                    readiness,
                    "Files depend on shell access, which is currently blocked by the auth contract.",
                    "Fix the first-party auth contract first, then re-check files readiness.");
            case UNAVAILABLE -> new WorkspaceReleaseReadinessCheckResponse(
                    "files",
                    "Nextcloud files route",
                    readiness,
                    hasText(dependencyUrl)
                            ? "Files are disabled even though a Nextcloud route is configured."
                            : "Files are disabled for this workspace snapshot.",
                    "Enable WEAVE_WORKSPACE_FILES_ENABLED for the workspace when files should ship.");
        };
    }

    private WorkspaceCapabilityReadiness overallReadiness(List<WorkspaceReleaseReadinessCheckResponse> checks) {
        boolean authBlocked = checks.stream()
                .anyMatch(check -> check.key().equals("auth-contract")
                        && check.readiness() == WorkspaceCapabilityReadiness.BLOCKED);
        if (authBlocked) {
            return WorkspaceCapabilityReadiness.BLOCKED;
        }
        boolean allReady = checks.stream().allMatch(check -> check.readiness() == WorkspaceCapabilityReadiness.READY);
        if (allReady) {
            return WorkspaceCapabilityReadiness.READY;
        }
        return WorkspaceCapabilityReadiness.DEGRADED;
    }

    private String summaryFor(WorkspaceCapabilityReadiness readiness, List<String> actions) {
        return switch (readiness) {
            case READY -> "workspace dependencies are configured and ready to ship.";
            case BLOCKED -> "Workspace readiness is blocked until the first-party auth contract is complete.";
            case DEGRADED -> "Workspace readiness is partially configured. " + actions.size()
                    + " operator action(s) remain before auth, chat, and files are all ready.";
            case UNAVAILABLE -> "workspace readiness is unavailable.";
        };
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

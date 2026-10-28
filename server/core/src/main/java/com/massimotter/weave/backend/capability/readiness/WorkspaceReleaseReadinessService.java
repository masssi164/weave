package com.massimotter.weave.backend.capability.readiness;

import com.massimotter.weave.backend.security.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.capability.WorkspaceCapabilityService;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilitiesResponse;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.capability.api.WorkspaceReleaseReadinessCheckResponse;
import com.massimotter.weave.backend.capability.api.WorkspaceReleaseReadinessResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.security.oauth2.jwt.Jwt;

public class WorkspaceReleaseReadinessService {

    private final OAuth2ResourceServerProperties resourceServerProperties;
    private final WeaveSecurityProperties weaveSecurityProperties;
    private final WorkspaceCapabilityService workspaceCapabilityService;

    public WorkspaceReleaseReadinessService(
            OAuth2ResourceServerProperties resourceServerProperties,
            WeaveSecurityProperties weaveSecurityProperties,
            WorkspaceCapabilityService workspaceCapabilityService) {
        this.resourceServerProperties = resourceServerProperties;
        this.weaveSecurityProperties = weaveSecurityProperties;
        this.workspaceCapabilityService = workspaceCapabilityService;
    }

    public WorkspaceReleaseReadinessResponse snapshot(Jwt jwt) {
        workspaceCapabilityService.requireCapability(jwt, "admin_control_plane.readiness_read",
                "workspace-release-readiness", "read");
        return supportSafeSnapshot();
    }

    private WorkspaceReleaseReadinessResponse supportSafeSnapshot() {
        WorkspaceCapabilitiesResponse capabilities = workspaceCapabilityService.snapshot();

        List<WorkspaceReleaseReadinessCheckResponse> checks = new ArrayList<>();
        checks.add(authCheck(capabilities));
        checks.addAll(workspaceCapabilityService.releaseReadinessChecks());

        List<String> actions = checks.stream()
                .map(WorkspaceReleaseReadinessCheckResponse::action)
                .filter(this::hasText)
                .distinct()
                .toList();

        WorkspaceCapabilityReadiness readiness = overallReadiness(checks);
        return new WorkspaceReleaseReadinessResponse(readiness, summaryFor(readiness, actions, checks), checks, actions);
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

    private String summaryFor(WorkspaceCapabilityReadiness readiness, List<String> actions,
                List<WorkspaceReleaseReadinessCheckResponse> checks) {
        return switch (readiness) {
            case READY -> "Core capability configuration is ready. Live provider operation and release verification are separate.";
            case BLOCKED -> "Workspace readiness is blocked until the first-party auth contract is complete.";
            case DEGRADED -> "Core capability configuration is incomplete or limited. " + actions.size()
                    + " operator action(s) remain before " + checkNames(checks) + " are all ready.";
            case UNAVAILABLE -> "workspace readiness is unavailable.";
        };
    }

    private String checkNames(List<WorkspaceReleaseReadinessCheckResponse> checks) {
        List<String> names = checks.stream().map(check -> check.key().equals("auth-contract") ? "auth" : check.key()).toList();
        if (names.size() == 1) {
            return names.getFirst();
        }
        if (names.size() == 2) {
            return String.join(" and ", names);
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + ", and " + names.getLast();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

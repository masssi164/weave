package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.model.OrganizationManifestResponse;
import com.massimotter.weave.backend.model.WorkspaceCapabilitiesResponse;
import com.massimotter.weave.backend.model.WorkspaceCapabilityPolicyState;
import com.massimotter.weave.backend.model.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.model.WorkspaceCapabilityStatusResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class OrganizationManifestService {

    private final OAuth2ResourceServerProperties resourceServerProperties;
    private final WorkspaceCapabilityService workspaceCapabilityService;
    private final Clock clock;

    @Autowired
    public OrganizationManifestService(
            OAuth2ResourceServerProperties resourceServerProperties,
            WorkspaceCapabilityService workspaceCapabilityService) {
        this(resourceServerProperties, workspaceCapabilityService, Clock.systemUTC());
    }

    OrganizationManifestService(
            OAuth2ResourceServerProperties resourceServerProperties,
            WorkspaceCapabilityService workspaceCapabilityService,
            Clock clock) {
        this.resourceServerProperties = resourceServerProperties;
        this.workspaceCapabilityService = workspaceCapabilityService;
        this.clock = clock;
    }

    public OrganizationManifestResponse manifestFor(Jwt jwt) {
        WorkspaceCapabilitiesResponse capabilities = workspaceCapabilityService.snapshot(jwt);
        return new OrganizationManifestResponse(
                "org-manifest-v1",
                "weave-dogfood",
                "Weave Dogfood",
                organizationAuthUrl(),
                Instant.now(clock),
                true,
                false,
                false,
                "organization-admin-console",
                List.of(
                        "accept organization auth URL, invite link, or deep link",
                        "complete SSO with the selected identity provider",
                        "consume effective organization manifest and capability states",
                        "render only ready, disabled, degraded, or policy-blocked member states"),
                List.of(
                        "create and bootstrap organizations",
                        "select and configure identity providers and category providers",
                        "manage provider endpoint URLs, rotation, readiness, and support-safe diagnostics",
                        "manage users, groups, roles, capability profiles, and deny-by-default policy",
                        "own provider, tool, and agent whitelisting plus privacy/compliance risk notes",
                        "audit organization-wide defaults and administrative changes"),
                memberStates(capabilities),
                capabilities);
    }

    private String organizationAuthUrl() {
        String issuerUri = resourceServerProperties.getJwt().getIssuerUri();
        if (issuerUri == null || issuerUri.isBlank()) {
            return "https://auth.not-configured.invalid";
        }
        return issuerUri;
    }

    private Map<String, String> memberStates(WorkspaceCapabilitiesResponse capabilities) {
        Map<String, String> states = new LinkedHashMap<>();
        states.put("identity-idm", memberState(capabilities.shellAccess()));
        states.put("chat", memberState(capabilities.chat()));
        states.put("files", memberState(capabilities.files()));
        states.put("calendar", memberState(capabilities.calendar()));
        states.put("boards-tasks", memberState(capabilities.boards()));
        states.put("weaver", memberState(capabilities.weaver()));
        return states;
    }

    private String memberState(WorkspaceCapabilityStatusResponse status) {
        if (status.policyState() == WorkspaceCapabilityPolicyState.POLICY_BLOCKED) {
            return "policy-blocked";
        }
        if (!status.enabled()
                || status.policyState() == WorkspaceCapabilityPolicyState.DISABLED
                || status.readiness() == WorkspaceCapabilityReadiness.UNAVAILABLE) {
            return "disabled";
        }
        if (status.readiness() == WorkspaceCapabilityReadiness.DEGRADED) {
            return "degraded";
        }
        if (status.readiness() == WorkspaceCapabilityReadiness.READY) {
            return "ready";
        }
        return "disabled";
    }
}

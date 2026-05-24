package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.model.OrganizationManifestResponse;
import com.massimotter.weave.backend.model.WorkspaceCapabilitiesResponse;
import com.massimotter.weave.backend.model.WorkspaceCapabilityPolicyState;
import com.massimotter.weave.backend.model.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.model.WorkspaceCapabilityStatusResponse;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class OrganizationManifestService {

    private final OAuth2ResourceServerProperties resourceServerProperties;
    private final WorkspaceCapabilityService workspaceCapabilityService;
    private final ContextAuthorizationProperties contextAuthorizationProperties;
    private final Clock clock;

    @Autowired
    public OrganizationManifestService(
            OAuth2ResourceServerProperties resourceServerProperties,
            WorkspaceCapabilityService workspaceCapabilityService,
            ContextAuthorizationProperties contextAuthorizationProperties) {
        this(resourceServerProperties, workspaceCapabilityService, contextAuthorizationProperties, Clock.systemUTC());
    }

    OrganizationManifestService(
            OAuth2ResourceServerProperties resourceServerProperties,
            WorkspaceCapabilityService workspaceCapabilityService,
            ContextAuthorizationProperties contextAuthorizationProperties,
            Clock clock) {
        this.resourceServerProperties = resourceServerProperties;
        this.workspaceCapabilityService = workspaceCapabilityService;
        this.contextAuthorizationProperties = contextAuthorizationProperties;
        this.clock = clock;
    }

    public OrganizationManifestResponse manifestFor(Jwt jwt) {
        WorkspaceCapabilitiesResponse capabilities = workspaceCapabilityService.snapshot(jwt);
        return new OrganizationManifestResponse(
                "org-manifest-v1",
                organizationId(jwt),
                organizationDisplayName(jwt),
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

    private String organizationId(Jwt jwt) {
        String tenantId = jwtClaim(jwt, contextAuthorizationProperties.tenantClaim());
        if (tenantId == null) {
            tenantId = jwtClaim(jwt, contextAuthorizationProperties.tenantFallbackClaim());
        }
        if (tenantId == null) {
            throw new ApiErrorException(
                    HttpStatus.UNAUTHORIZED,
                    "organization-manifest-unauthorized",
                    "Organization manifest requires an authenticated organization tenant.",
                    Map.of("reason", "tenant claim is missing"));
        }
        return tenantId;
    }

    private String organizationDisplayName(Jwt jwt) {
        String displayName = jwtClaim(jwt, "weave_organization_name");
        if (displayName == null) {
            displayName = jwtClaim(jwt, "organization_name");
        }
        if (displayName == null) {
            displayName = jwtClaim(jwt, "org_name");
        }
        if (displayName == null) {
            displayName = titleize(organizationId(jwt));
        }
        return displayName;
    }

    private String organizationAuthUrl() {
        String issuerUri = resourceServerProperties.getJwt().getIssuerUri();
        if (issuerUri == null || issuerUri.isBlank()) {
            return "https://auth.not-configured.invalid";
        }
        String normalized = issuerUri.trim();
        URI uri;
        try {
            uri = new URI(normalized);
        } catch (URISyntaxException exception) {
            throw invalidOrganizationAuthUrl();
        }
        String scheme = uri.getScheme();
        if (!uri.isAbsolute()
                || uri.getHost() == null
                || uri.getHost().isBlank()
                || (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme))
                || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            throw invalidOrganizationAuthUrl();
        }
        while (normalized.endsWith("/") && normalized.length() > (scheme.length() + 3 + uri.getHost().length())) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private ApiErrorException invalidOrganizationAuthUrl() {
        return new ApiErrorException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "organization-manifest-invalid-auth-url",
                "Organization auth URL is not configured as an absolute support-safe HTTP(S) URL.",
                Map.of("reason", "invalid organization auth URL"));
    }

    private String jwtClaim(Jwt jwt, String claimName) {
        if (jwt == null || claimName == null || claimName.isBlank()) {
            return null;
        }
        Object raw = jwt.getClaims().get(claimName);
        if (raw instanceof String value && !value.isBlank()) {
            return value.trim();
        }
        return null;
    }

    private String titleize(String value) {
        if (value == null || value.isBlank()) {
            return "Organization";
        }
        String[] parts = value.trim().split("[-_\\s]+");
        StringBuilder title = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (title.length() > 0) {
                title.append(' ');
            }
            title.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                title.append(part.substring(1));
            }
        }
        return title.length() == 0 ? "Organization" : title.toString();
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

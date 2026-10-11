package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.model.CapabilityManifestState;
import com.massimotter.weave.backend.model.ClientAccessCredentialLifecycleResponse;
import com.massimotter.weave.backend.model.ClientAccessDiscoveryResponse;
import com.massimotter.weave.backend.model.ClientAccessProtocolSurfaceResponse;
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
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class OrganizationManifestService {

    private final OAuth2ResourceServerProperties resourceServerProperties;
    private final WorkspaceCapabilityService workspaceCapabilityService;
    private final OrganizationIdentityContextResolver identityContexts;
    private final Clock clock;

    @Autowired
    public OrganizationManifestService(
            OAuth2ResourceServerProperties resourceServerProperties,
            WorkspaceCapabilityService workspaceCapabilityService,
            OrganizationIdentityContextResolver identityContexts) {
        this(resourceServerProperties, workspaceCapabilityService, identityContexts, Clock.systemUTC());
    }

    OrganizationManifestService(
            OAuth2ResourceServerProperties resourceServerProperties,
            WorkspaceCapabilityService workspaceCapabilityService,
            OrganizationIdentityContextResolver identityContexts,
            Clock clock) {
        this.resourceServerProperties = resourceServerProperties;
        this.workspaceCapabilityService = workspaceCapabilityService;
        this.identityContexts = java.util.Objects.requireNonNull(identityContexts, "identityContexts");
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
                        "complete OIDC Authorization Code with PKCE through the organization authority",
                        "consume effective organization manifest and capability states",
                        "render only currently authorized and available member capabilities"),
                List.of(
                        "create and bootstrap organizations",
                        "manage Keycloak identity, upstream federation, and selectable category providers",
                        "manage provider endpoint URLs, rotation, readiness, and support-safe diagnostics",
                        "manage users, groups, roles, capability profiles, and deny-by-default policy",
                        "own provider, tool, and agent whitelisting plus privacy/compliance risk notes",
                        "audit organization-wide defaults and administrative changes"),
                memberStates(capabilities),
                clientAccessDiscovery(),
                capabilities);
    }

    private String organizationId(Jwt jwt) {
        try {
            return identityContexts.resolve(jwt).organizationId();
        } catch (ApiErrorException exception) {
            throw new ApiErrorException(
                    HttpStatus.UNAUTHORIZED,
                    "organization-manifest-unauthorized",
                    "Organization manifest requires an authenticated organization tenant.",
                    Map.of("reason", "organization identity is missing"));
        }
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
            throw invalidOrganizationAuthUrl();
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

    private Map<String, CapabilityManifestState> memberStates(WorkspaceCapabilitiesResponse capabilities) {
        Map<String, CapabilityManifestState> states = new LinkedHashMap<>();
        states.put("platform-identity", memberState(capabilities.shellAccess()));
        states.put("chat-channels", memberState(capabilities.chat()));
        states.put("files-docs", memberState(capabilities.files()));
        states.put("boards-tasks", memberState(capabilities.boards()));
        states.put("calendar-events", memberState(capabilities.calendar()));
        return states;
    }

    private CapabilityManifestState memberState(WorkspaceCapabilityStatusResponse status) {
        if (status.policyState() == WorkspaceCapabilityPolicyState.POLICY_BLOCKED
                || status.policyState() == WorkspaceCapabilityPolicyState.DISABLED) {
            return CapabilityManifestState.DISABLED_BY_POLICY;
        }
        if (status.readiness() == WorkspaceCapabilityReadiness.READY) {
            return CapabilityManifestState.AVAILABLE;
        }
        if (status.readiness() == WorkspaceCapabilityReadiness.DEGRADED) {
            return CapabilityManifestState.DEGRADED;
        }
        if (status.readiness() == WorkspaceCapabilityReadiness.UNAVAILABLE
                || status.policyState() == WorkspaceCapabilityPolicyState.UNAVAILABLE) {
            return CapabilityManifestState.NOT_CONFIGURED;
        }
        return CapabilityManifestState.UNAVAILABLE;
    }

    private Map<String, ClientAccessDiscoveryResponse> clientAccessDiscovery() {
        Map<String, ClientAccessDiscoveryResponse> access = new LinkedHashMap<>();
        access.put("files", filesAccess());
        access.put("calendar", calendarAccess());
        access.put("chat", chatAccess());
        return access;
    }

    private ClientAccessDiscoveryResponse filesAccess() {
        return new ClientAccessDiscoveryResponse(
                "files",
                "/api/files",
                "Files",
                List.of(
                        surface("openapi", "Weave Files User API", "/api/files", "data_plane_guarded",
                                "Server-generated User operations are authorized by current member, organization, Space and resource rights. Available actions depend on the selected provider. Public WebDAV client access is outside this release.")),
                credentialLifecycle(
                        "member_oidc_session",
                        List.of(),
                        List.of()),
                true,
                false);
    }

    private ClientAccessDiscoveryResponse calendarAccess() {
        return new ClientAccessDiscoveryResponse(
                "calendar",
                "/api/calendar",
                "Calendar",
                List.of(
                        surface("openapi", "Weave Calendar User API", "/api/calendar", "data_plane_guarded",
                                "Calendar User operations require authorized scopes and exact event time semantics before readiness. CalDAV is private to the selected provider adapter in this release.")),
                credentialLifecycle(
                        "member_oidc_session",
                        List.of(),
                        List.of()),
                true,
                false);
    }

    private ClientAccessDiscoveryResponse chatAccess() {
        return new ClientAccessDiscoveryResponse(
                "chat",
                "/api/chat",
                "Chat domain",
                List.of(
                        surface("openapi", "Weave Chat control and context API", "/api/chat", "control_plane_available",
                                "Generated User operations expose Chat readiness and context; Matrix Client-Server is the separate conversation protocol."),
                        surface("standard-protocol", "Weave Matrix Client-Server projection", "/_matrix/client", "compatibility_guarded",
                                "The versioned Matrix support profile defines the bounded surface. Weave-owned client interoperability and policy-governed E2EE remain guarded until demonstrated; federation is disabled by default.")),
                credentialLifecycle(
                        "member_oidc_session_guarded",
                        List.of("/api/chat/readiness"),
                        List.of("Weave-owned Matrix client compatibility", "policy-governed E2EE qualification")),
                true,
                false);
    }

    private ClientAccessProtocolSurfaceResponse surface(
            String kind,
            String name,
            String setupPath,
            String readiness,
            String note) {
        return new ClientAccessProtocolSurfaceResponse(kind, name, setupPath, readiness, List.of(note));
    }

    private ClientAccessCredentialLifecycleResponse credentialLifecycle(
            String status,
            List<String> lifecyclePaths,
            List<String> blockedUntil) {
        return new ClientAccessCredentialLifecycleResponse(status, false, lifecyclePaths, blockedUntil);
    }
}

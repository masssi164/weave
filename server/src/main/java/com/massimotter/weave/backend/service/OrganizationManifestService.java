package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
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
                        "render only available, disabled_by_policy, not_configured, degraded, unavailable, or coming_later member states"),
                List.of(
                        "create and bootstrap organizations",
                        "select and configure identity providers and category providers",
                        "manage provider endpoint URLs, rotation, readiness, and support-safe diagnostics",
                        "manage users, groups, roles, capability profiles, and deny-by-default policy",
                        "own provider, tool, and agent whitelisting plus privacy/compliance risk notes",
                        "audit organization-wide defaults and administrative changes"),
                memberStates(capabilities),
                clientAccessDiscovery(),
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
        states.put("idm-rbac", memberState(capabilities.shellAccess()));
        states.put("chat-channels", memberState(capabilities.chat()));
        states.put("files-docs", memberState(capabilities.files()));
        states.put("boards-tasks", memberState(capabilities.boards()));
        states.put("calendar-events", memberState(capabilities.calendar()));
        states.put("meetings", memberState(capabilities.meetingsCalls()));
        states.put("forms-contacts", CapabilityManifestState.COMING_LATER);
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
        access.put("meetings-calls", meetingsCallsAccess());
        return access;
    }

    private ClientAccessDiscoveryResponse filesAccess() {
        return new ClientAccessDiscoveryResponse(
                "files",
                "/api/files",
                "Files",
                List.of(
                        surface("openapi", "Weave Files control API", "/api/files", "control_plane_available",
                                "Generated contract for discovery, readiness, setup, revoke, and credential lifecycle; Files list/read/write data-plane operations belong to the WebDAV facade."),
                        surface("standard-protocol", "Weave WebDAV projection", "/dav/files", "data_plane_read_write_available",
                                "OPTIONS, PROPFIND, GET, HEAD, PUT, DELETE, and MKCOL are exposed through Weave policy, audit, file IDs, ETags, and support-safe conflict/precondition/storage errors."),
                        surface("native-os", "iOS File Provider and Android DocumentsProvider setup", "/api/files/native-provider-setup", "contract_ready_implementation_blocked",
                                "Native providers call Weave file facade paths only and must prove device revocation before availability.")),
                credentialLifecycle(
                        "revocable_device_grants_available",
                        List.of(
                                "/api/files/client-setup/credentials",
                                "/api/files/native-provider-setup"),
                        List.of("physical native device proof")),
                true,
                false);
    }

    private ClientAccessDiscoveryResponse calendarAccess() {
        return new ClientAccessDiscoveryResponse(
                "calendar",
                "/api/calendar",
                "Calendar",
                List.of(
                        surface("openapi", "Weave Calendar control API", "/api/calendar", "control_plane_available",
                                "Generated contract for discovery, policy, setup, and credential lifecycle; Calendar event data-plane operations belong to the CalDAV/iCalendar facade."),
                        surface("standard-protocol", "Weave CalDAV/iCalendar projection", "/caldav", "data_plane_read_write_available",
                                "Discovery, query, multiget, sync, free-busy, event reads/writes, recurrence, and scoped device credentials run through Weave policy and canonical events."),
                        surface("native-os", "iOS CalDAV profile and Android SyncAdapter setup", "/api/calendar/native-sync-setup", "contract_ready_implementation_blocked",
                                "Native sync stays scoped to workspace, team, and channel calendars until platform integration and physical-device proof exist."),
                        surface("mcp", "Governed Calendar MCP tools", null, "planned_allowlist",
                                "MCP consumes semantic Weave event capabilities and audit receipts, not raw CalDAV credentials.")),
                credentialLifecycle(
                        "revocable_device_grants_available",
                        List.of(
                                "/api/calendar/client-setup/credentials",
                                "/api/calendar/client-setup/apple.mobileconfig"),
                        List.of("signed profile delivery", "native sync physical-device evidence")),
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
                                "Generated contract for readiness, decisions, meeting capsules, Weaver context, and migration review; conversation/message data-plane operations belong to the Matrix Client-Server facade."),
                        surface("standard-protocol", "Weave Matrix Client-Server projection", "/_matrix/client", "encrypted_data_plane_available",
                                "OIDC-gated room sync, encrypted timelines, sends, receipts, verification, and recovery project the canonical Chat domain through the client-owned Rust crypto core; federation stays disabled by default."),
                        surface("mcp", "Governed Chat MCP tools", null, "planned_allowlist",
                                "MCP receives semantic Weave chat operations, consented summaries, and decision references rather than raw Matrix access.")),
                credentialLifecycle(
                        "session_bound_no_raw_matrix_credentials",
                        List.of("/api/chat/readiness"),
                        List.of("decrypted-content consent gates", "retention and moderation policy proof", "federation isolation evidence")),
                true,
                false);
    }

    private ClientAccessDiscoveryResponse meetingsCallsAccess() {
        return new ClientAccessDiscoveryResponse(
                "meetings-calls",
                "/_matrix/client",
                "Calls",
                List.of(
                        surface("standard-protocol", "MatrixRTC Profile 0 signaling", "/_matrix/client", "experimental_guarded",
                                "Matrix v1.19 plus Weave MatrixRTC Profile 0 is the only member signaling shape; no member Calls REST API or proprietary join grant exists."),
                        surface("native-os", "CallKit and Android Core-Telecom boundary", null, "guarded_physical_device_evidence_required",
                                "Native call UI follows MatrixRTC invitation and membership state; provider transport credentials remain internal."),
                        surface("standard-protocol", "WebRTC media and meeting context", null, "rtc_authorizer_required",
                                "Calendar and chat link meeting context while an internal RTC Authorizer independently validates current Matrix room, slot, member, device, policy, nonce, audience, and expiry.")),
                credentialLifecycle(
                        "matrix_native_oauth_distinct_from_sfu_tokens",
                        List.of(
                                "/_matrix/client/v1/auth_metadata",
                                "/_matrix/client/v3/user/{userId}/openid/request_token"),
                        List.of("RTC Authorizer evidence", "MatrixRTC media E2EE", "native call UI proof", "TURN/reconnect evidence")),
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

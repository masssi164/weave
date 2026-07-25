package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.config.AgentRuntimeEntitlementProperties;
import com.massimotter.weave.backend.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.model.WorkspaceCapabilitiesResponse;
import com.massimotter.weave.backend.model.WorkspaceCapabilityPolicyResponse;
import com.massimotter.weave.backend.model.WorkspaceCapabilityPolicyState;
import com.massimotter.weave.backend.model.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.model.WorkspaceCapabilityStatusResponse;
import com.massimotter.weave.backend.model.admin.EffectivePolicyDenyResponse;
import com.massimotter.weave.backend.model.admin.EffectivePolicyResponse;
import com.massimotter.weave.backend.model.admin.ProviderCapabilityHealthResponse;
import com.massimotter.weave.backend.exception.ApiErrorException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceCapabilityService {

    private static final String WEAVER_CAPABILITY_GROUP_PATH = "/capabilities/weaver";
    private static final List<String> OWNER_ADMIN_CAPABILITIES = List.of(
            "chat.read",
            "chat.send",
            "files.read",
            "files.upload",
            "calendar.read",
            "calendar.manage_events",
            "boards.read",
            "boards.update_task",
            "meetings.join",
            "meetings.host",
            "documents.view",
            "documents.edit",
            "decisions.read",
            "decisions.record",
            "manuals.read",
            "manuals.admin",
            "release_evidence.read",
            "release_evidence.manage",
            "admin_control_plane.readiness_read",
            "admin.policy.edit",
            "admin.provider.configure");
    private static final List<String> MEMBER_CAPABILITIES = List.of(
            "chat.read",
            "chat.send",
            "files.read",
            "files.upload",
            "calendar.read",
            "boards.read",
            "meetings.join",
            "documents.view",
            "decisions.read",
            "manuals.read",
            "release_evidence.read");
    private final OAuth2ResourceServerProperties resourceServerProperties;
    private final WeaveSecurityProperties weaveSecurityProperties;
    private final WorkspaceCapabilityProperties workspaceCapabilityProperties;
    private final AgentRuntimeEntitlementProperties runtimeEntitlementProperties;
    private final ProviderCapabilityHealthService providerHealthService;

    public WorkspaceCapabilityService(
            OAuth2ResourceServerProperties resourceServerProperties,
            WeaveSecurityProperties weaveSecurityProperties,
            WorkspaceCapabilityProperties workspaceCapabilityProperties) {
        this(
                resourceServerProperties,
                weaveSecurityProperties,
                workspaceCapabilityProperties,
                AgentRuntimeEntitlementProperties.disabled(),
                (ProviderCapabilityHealthService) null);
    }

    @Autowired
    public WorkspaceCapabilityService(
            OAuth2ResourceServerProperties resourceServerProperties,
            WeaveSecurityProperties weaveSecurityProperties,
            WorkspaceCapabilityProperties workspaceCapabilityProperties,
            AgentRuntimeEntitlementProperties runtimeEntitlementProperties,
            ObjectProvider<ProviderCapabilityHealthService> providerHealthServiceProvider) {
        this(
                resourceServerProperties,
                weaveSecurityProperties,
                workspaceCapabilityProperties,
                runtimeEntitlementProperties,
                providerHealthServiceProvider == null ? null : providerHealthServiceProvider.getIfAvailable());
    }

    public WorkspaceCapabilityService(
            OAuth2ResourceServerProperties resourceServerProperties,
            WeaveSecurityProperties weaveSecurityProperties,
            WorkspaceCapabilityProperties workspaceCapabilityProperties,
            AgentRuntimeEntitlementProperties runtimeEntitlementProperties) {
        this(
                resourceServerProperties,
                weaveSecurityProperties,
                workspaceCapabilityProperties,
                runtimeEntitlementProperties,
                (ProviderCapabilityHealthService) null);
    }

    WorkspaceCapabilityService(
            OAuth2ResourceServerProperties resourceServerProperties,
            WeaveSecurityProperties weaveSecurityProperties,
            WorkspaceCapabilityProperties workspaceCapabilityProperties,
            AgentRuntimeEntitlementProperties runtimeEntitlementProperties,
            ProviderCapabilityHealthService providerHealthService) {
        this.resourceServerProperties = resourceServerProperties;
        this.weaveSecurityProperties = weaveSecurityProperties;
        this.workspaceCapabilityProperties = workspaceCapabilityProperties;
        this.runtimeEntitlementProperties = runtimeEntitlementProperties;
        this.providerHealthService = providerHealthService;
    }

    WorkspaceCapabilityService(
            OAuth2ResourceServerProperties resourceServerProperties,
            WeaveSecurityProperties weaveSecurityProperties,
            WorkspaceCapabilityProperties workspaceCapabilityProperties,
            ProviderCapabilityHealthService providerHealthService) {
        this(
                resourceServerProperties,
                weaveSecurityProperties,
                workspaceCapabilityProperties,
                AgentRuntimeEntitlementProperties.disabled(),
                providerHealthService);
    }

    public WorkspaceCapabilitiesResponse snapshot() {
        return snapshot(null);
    }

    public WorkspaceCapabilitiesResponse snapshot(Jwt jwt) {
        EffectivePolicy policy = effectivePolicy(jwt);
        WorkspaceCapabilityReadiness shellAccessReadiness = shellAccessReadiness();
        return new WorkspaceCapabilitiesResponse(
                status(
                        workspaceCapabilityProperties.shellAccess(),
                        shellAccessReadiness,
                        "identity/IDM",
                        List.of(),
                        policy,
                        "Weave SSO shell access is available."),
                dependentStatus(
                        workspaceCapabilityProperties.chat(),
                        shellAccessReadiness,
                        "chat",
                        List.of("chat.read", "chat.send"),
                        policy,
                        "Chat is available through the Weave workspace."),
                dependentStatus(
                        workspaceCapabilityProperties.files(),
                        shellAccessReadiness,
                        "files",
                        List.of("files.read", "files.upload"),
                        policy,
                        "Files are available through Weave."),
                providerBackedStatus(
                        workspaceCapabilityProperties.calendar(),
                        WorkspaceCapabilityReadiness.UNAVAILABLE,
                        "calendar",
                        List.of("calendar.read", "calendar.manage_events"),
                        policy,
                        "Calendar is available through the Weave workspace."),
                standaloneStatus(
                        workspaceCapabilityProperties.boards(),
                        WorkspaceCapabilityReadiness.UNAVAILABLE,
                        "boards/tasks",
                        List.of("boards.read", "boards.update_task"),
                        policy,
                        "Boards and tasks are available through Weave."),
                standaloneStatus(
                        workspaceCapabilityProperties.meetingsCalls(),
                        WorkspaceCapabilityReadiness.UNAVAILABLE,
                        "meetings/calls",
                        List.of("meetings.join", "meetings.host"),
                        policy,
                        "Meetings and calls are available through Weave."),
                standaloneStatus(
                        workspaceCapabilityProperties.documentsCollaboration(),
                        WorkspaceCapabilityReadiness.UNAVAILABLE,
                        "documents/collaboration",
                        List.of("documents.view", "documents.edit"),
                        policy,
                        "Documents and collaboration are available through Weave."),
                standaloneStatus(
                        workspaceCapabilityProperties.decisionsEvidence(),
                        WorkspaceCapabilityReadiness.READY,
                        "decisions/evidence",
                        List.of("decisions.read", "decisions.record"),
                        policy,
                        "Decisions and evidence are available through Weave."),
                standaloneStatus(
                        workspaceCapabilityProperties.manualsHelp(),
                        WorkspaceCapabilityReadiness.READY,
                        "manuals/help",
                        List.of("manuals.read", "manuals.admin"),
                        policy,
                        "Manuals and help are available through Weave."),
                standaloneStatus(
                        workspaceCapabilityProperties.releaseEvidence(),
                        WorkspaceCapabilityReadiness.READY,
                        "release evidence",
                        List.of("release_evidence.read", "release_evidence.manage"),
                        policy,
                        "Release evidence is available through Weave."),
                standaloneStatus(
                        workspaceCapabilityProperties.adminControlPlane(),
                        WorkspaceCapabilityReadiness.READY,
                        "admin control plane",
                        List.of("admin_control_plane.readiness_read"),
                        policy,
                        "Workspace Health exposes support-safe admin readiness without provider credentials."),
                standaloneStatus(
                        workspaceCapabilityProperties.agentRuntimeControl(),
                        WorkspaceCapabilityReadiness.UNAVAILABLE,
                        "Agent Runtime Control",
                        List.of("agent-runtime.entitled"),
                        policy,
                        "Agent Runtime Control is optional and requires a current Keycloak entitlement."));
    }

    public WorkspaceCapabilityPolicyResponse policySnapshot(Jwt jwt) {
        requireCapability(jwt, "admin_control_plane.readiness_read", "workspace-capability-policy", "read");
        EffectivePolicy policy = effectivePolicy(jwt);
        return new WorkspaceCapabilityPolicyResponse(
                "identity/IDM",
                "OIDC/SAML selected IDM",
                "OIDC/SAML adapter contract; Keycloak is only the dogfood default, not product truth",
                "OIDC role claims plus group claims from the selected IDM",
                policy.roles(),
                policy.groups(),
                policy.profileKeys(),
                policy.capabilities().stream().sorted().toList(),
                true,
                true,
                runtimeEntitlementProperties.enabled()
                        ? "ARC entitlement is derived from the configured Keycloak group; profile and cell authority remain server-owned"
                        : "disabled-by-default; Agent Runtime Control requires explicit Keycloak entitlement configuration");
    }

    public List<String> grantedCapabilities(Jwt jwt) {
        return effectivePolicy(jwt).capabilities().stream().sorted().toList();
    }

    public EffectivePolicyResponse effectivePolicySnapshot(Jwt jwt, String context) {
        OrganizationIdentityContext identity = jwt == null ? null : OrganizationIdentityContextFactory.fromJwt(jwt);
        EffectivePolicy policy = effectivePolicy(identity);
        String subject = identity == null ? "system" : identity.subject();
        String organization = identity == null ? "weave-dogfood" : identity.organizationId();
        String issuer = identity == null ? "unknown-issuer" : identity.issuer();
        String primaryIdentityKey = identity == null ? "issuer+subject:unknown-issuer#system" : identity.primaryIdentityKey();
        List<String> denies = List.of(
                "admin.policy.edit",
                "admin.provider.configure",
                "agent-runtime.entitled").stream()
                .filter(capability -> !policy.capabilities().contains(capability))
                .toList();
        return new EffectivePolicyResponse(
                subject,
                organization,
                context,
                List.of(issuer),
                policy.groups(),
                policy.roles(),
                identity == null ? List.of() : identity.contextRoles(),
                policy.providerRoleMappings(),
                policy.capabilities().stream().sorted().toList(),
                denies.stream()
                        .map(capability -> new EffectivePolicyDenyResponse(
                                capability,
                                denyReason(policy, capability),
                                "deny-by-default-capability-policy"))
                        .toList(),
                readinessImpact(policy),
                List.of("effective-policy-preview:" + subject),
                true,
                true,
                primaryIdentityKey,
                false);
    }

    public void requireCapability(Jwt jwt, String capability, String module, String operation) {
        if (jwt == null) {
            throw new ApiErrorException(
                    HttpStatus.UNAUTHORIZED,
                    "unauthorized",
                    "Authentication is required.",
                    Map.of("module", module, "operation", operation));
        }
        Set<String> grantedCapabilities = "device_credential".equals(jwt.getClaimAsString("weave_auth_method"))
                ? Set.copyOf(jwt.getClaimAsStringList("weave_capabilities") == null
                        ? List.of()
                        : jwt.getClaimAsStringList("weave_capabilities"))
                : effectivePolicy(jwt).capabilities();
        if (!grantedCapabilities.contains(capability)) {
            throw new ApiErrorException(
                    HttpStatus.FORBIDDEN,
                    "capability-policy-blocked",
                    "This action is blocked by workspace role or group policy.",
                    Map.of(
                            "module", module,
                            "operation", operation,
                            "requiredCapability", capability,
                            "policyState", WorkspaceCapabilityPolicyState.POLICY_BLOCKED.value(),
                            "diagnosticsRedacted", true));
        }
    }

    private WorkspaceCapabilityStatusResponse dependentStatus(
            WorkspaceCapabilityProperties.Capability capability,
            WorkspaceCapabilityReadiness shellAccessReadiness,
            String category,
            List<String> requiredCapabilities,
            EffectivePolicy policy,
            String readyImpact) {
        if (!capability.enabled()) {
            return status(capability, WorkspaceCapabilityReadiness.UNAVAILABLE, category, requiredCapabilities, policy,
                    "This capability is disabled by workspace policy.");
        }
        if (shellAccessReadiness == WorkspaceCapabilityReadiness.BLOCKED) {
            return status(capability, WorkspaceCapabilityReadiness.BLOCKED, category, requiredCapabilities, policy,
                    "Sign-in or workspace SSO must be ready before this capability can be used.");
        }
        if ("files".equals(category) || "chat".equals(category)) {
            ProviderCapabilityHealthResponse.CapabilityHealth providerHealth = cachedProviderHealth(category);
            if (providerHealth != null) {
                WorkspaceCapabilityReadiness readiness = effectiveProviderReadiness(
                        capability.readiness(),
                        providerReadiness(providerHealth.state()));
                String providerAttention = "files".equals(category)
                        ? "Files need admin attention before members can use them reliably. "
                        : "Chat needs admin attention before members can use it reliably. ";
                String memberImpact = readiness == WorkspaceCapabilityReadiness.READY
                        ? readyImpact
                        : providerAttention + "Ask an admin to inspect Workspace Health.";
                return status(
                        capability,
                        readiness,
                        category,
                        requiredCapabilities,
                        policy,
                        memberImpact,
                        providerHealth.supportSafeCode());
            }
        }
        if (capability.readiness() != null) {
            return status(capability, capability.readiness(), category, requiredCapabilities, policy, readyImpact);
        }
        if (hasText(capability.dependencyUrl())) {
            return status(capability, WorkspaceCapabilityReadiness.READY, category, requiredCapabilities, policy, readyImpact);
        }
        return status(capability, WorkspaceCapabilityReadiness.DEGRADED, category, requiredCapabilities, policy,
                "This capability is degraded. Ask an admin to inspect Workspace Health.");
    }

    private WorkspaceCapabilityStatusResponse standaloneStatus(
            WorkspaceCapabilityProperties.Capability capability,
            WorkspaceCapabilityReadiness defaultReadiness,
            String category,
            List<String> requiredCapabilities,
            EffectivePolicy policy,
            String readyImpact) {
        if (!capability.enabled()) {
            return status(capability, WorkspaceCapabilityReadiness.UNAVAILABLE, category, requiredCapabilities, policy,
                    "This capability is disabled by workspace policy.");
        }
        if (capability.readiness() != null) {
            return status(capability, capability.readiness(), category, requiredCapabilities, policy, readyImpact);
        }
        return status(capability, defaultReadiness, category, requiredCapabilities, policy,
                defaultReadiness == WorkspaceCapabilityReadiness.UNAVAILABLE
                        ? "This capability is not ready for members in this workspace. Ask an admin to review Workspace Health."
                        : readyImpact);
    }

    private WorkspaceCapabilityStatusResponse providerBackedStatus(
            WorkspaceCapabilityProperties.Capability capability,
            WorkspaceCapabilityReadiness defaultReadiness,
            String category,
            List<String> requiredCapabilities,
            EffectivePolicy policy,
            String readyImpact) {
        if (!capability.enabled()) {
            return standaloneStatus(
                    capability,
                    defaultReadiness,
                    category,
                    requiredCapabilities,
                    policy,
                    readyImpact);
        }
        ProviderCapabilityHealthResponse.CapabilityHealth providerHealth = cachedProviderHealth(category);
        if (providerHealth == null) {
            return standaloneStatus(
                    capability,
                    defaultReadiness,
                    category,
                    requiredCapabilities,
                    policy,
                    readyImpact);
        }
        WorkspaceCapabilityReadiness readiness = effectiveProviderReadiness(
                capability.readiness(),
                providerReadiness(providerHealth.state()));
        String memberImpact = readiness == WorkspaceCapabilityReadiness.READY
                ? readyImpact
                : "This capability needs admin attention. Other workspace areas remain available while it recovers.";
        return status(
                capability,
                readiness,
                category,
                requiredCapabilities,
                policy,
                memberImpact,
                providerHealth.supportSafeCode());
    }

    private WorkspaceCapabilityReadiness effectiveProviderReadiness(
            WorkspaceCapabilityReadiness configuredReadiness,
            WorkspaceCapabilityReadiness providerReadiness) {
        if (configuredReadiness == WorkspaceCapabilityReadiness.BLOCKED
                || configuredReadiness == WorkspaceCapabilityReadiness.UNAVAILABLE) {
            return configuredReadiness;
        }
        if (configuredReadiness == WorkspaceCapabilityReadiness.DEGRADED) {
            return providerReadiness == WorkspaceCapabilityReadiness.UNAVAILABLE
                    ? WorkspaceCapabilityReadiness.UNAVAILABLE
                    : WorkspaceCapabilityReadiness.DEGRADED;
        }
        return providerReadiness;
    }

    private ProviderCapabilityHealthResponse.CapabilityHealth cachedProviderHealth(String capability) {
        if (providerHealthService == null) {
            return null;
        }
        return providerHealthService.cached(capability).orElse(null);
    }

    private WorkspaceCapabilityReadiness providerReadiness(String state) {
        return switch (state == null ? "" : state) {
            case "available" -> WorkspaceCapabilityReadiness.READY;
            case "unavailable" -> WorkspaceCapabilityReadiness.UNAVAILABLE;
            default -> WorkspaceCapabilityReadiness.DEGRADED;
        };
    }

    private WorkspaceCapabilityReadiness shellAccessReadiness() {
        if (!workspaceCapabilityProperties.shellAccess().enabled()) {
            return WorkspaceCapabilityReadiness.UNAVAILABLE;
        }
        boolean hasIssuer = hasText(resourceServerProperties.getJwt().getIssuerUri());
        boolean hasAudience = hasText(weaveSecurityProperties.requiredAudience());
        boolean hasClientId = hasText(weaveSecurityProperties.clientId());
        return hasIssuer && hasAudience && hasClientId
                ? WorkspaceCapabilityReadiness.READY
                : WorkspaceCapabilityReadiness.BLOCKED;
    }

    private WorkspaceCapabilityStatusResponse status(
            WorkspaceCapabilityProperties.Capability capability,
            WorkspaceCapabilityReadiness readiness,
            String category,
            List<String> requiredCapabilities,
            EffectivePolicy policy,
            String defaultImpact) {
        return status(capability, readiness, category, requiredCapabilities, policy, defaultImpact, null);
    }

    private WorkspaceCapabilityStatusResponse status(
            WorkspaceCapabilityProperties.Capability capability,
            WorkspaceCapabilityReadiness readiness,
            String category,
            List<String> requiredCapabilities,
            EffectivePolicy policy,
            String defaultImpact,
            String supportSafeCode) {
        List<String> granted = requiredCapabilities.stream()
                .filter(policy.capabilities()::contains)
                .toList();
        boolean policyAllows = requiredCapabilities.isEmpty() || !granted.isEmpty();
        WorkspaceCapabilityPolicyState policyState;
        WorkspaceCapabilityReadiness effectiveReadiness = readiness;
        String memberImpact = defaultImpact;
        if (!capability.enabled()) {
            policyState = WorkspaceCapabilityPolicyState.DISABLED;
            effectiveReadiness = WorkspaceCapabilityReadiness.UNAVAILABLE;
            memberImpact = "This capability is disabled by workspace policy.";
        } else if (!policyAllows) {
            policyState = WorkspaceCapabilityPolicyState.POLICY_BLOCKED;
            effectiveReadiness = WorkspaceCapabilityReadiness.BLOCKED;
            memberImpact = "This capability is blocked by your role or group policy. Ask an admin if you need access.";
        } else if (effectiveReadiness == WorkspaceCapabilityReadiness.UNAVAILABLE) {
            policyState = WorkspaceCapabilityPolicyState.UNAVAILABLE;
        } else {
            policyState = WorkspaceCapabilityPolicyState.ALLOWED;
        }

        return new WorkspaceCapabilityStatusResponse(
                capability.enabled(),
                effectiveReadiness,
                policyState,
                policy.profileKeyFor(category),
                memberImpact,
                supportRef(category, effectiveReadiness, policyState, supportSafeCode),
                granted);
    }

    private String supportRef(
            String category,
            WorkspaceCapabilityReadiness readiness,
            WorkspaceCapabilityPolicyState policyState,
            String supportSafeCode) {
        String safeCategory = category == null ? "workspace" : category.replaceAll("[^a-zA-Z0-9-]+", "-").toLowerCase();
        String base = "support:workspace-capability:" + safeCategory + ":" + readiness.value() + ":" + policyState.value();
        if (supportSafeCode == null || supportSafeCode.isBlank()) {
            return base;
        }
        String safeCode = supportSafeCode.trim().replaceAll("[^a-zA-Z0-9-]+", "-").toLowerCase();
        return base + ":" + safeCode;
    }

    private EffectivePolicy effectivePolicy(Jwt jwt) {
        return effectivePolicy(jwt == null ? null : OrganizationIdentityContextFactory.fromJwt(jwt));
    }

    private EffectivePolicy effectivePolicy(OrganizationIdentityContext identity) {
        if (identity == null) {
            LinkedHashSet<String> systemCapabilities = new LinkedHashSet<>(OWNER_ADMIN_CAPABILITIES);
            return new EffectivePolicy(
                    List.of("system"),
                    List.of(),
                    List.of("system-internal-readiness"),
                    Set.copyOf(systemCapabilities),
                    List.of("system:internal-readiness"));
        }
        List<String> roles = identity.roles();
        List<String> groups = identity.groups();
        LinkedHashSet<String> capabilities = new LinkedHashSet<>();
        LinkedHashSet<String> profileKeys = new LinkedHashSet<>();

        if (roles.stream().anyMatch(role -> role.equals("owner") || role.equals("admin"))) {
            capabilities.addAll(OWNER_ADMIN_CAPABILITIES);
            profileKeys.add("workspace-admin");
        }
        if (roles.contains("member")) {
            capabilities.addAll(MEMBER_CAPABILITIES);
            profileKeys.add("member-default");
        }
        if (roles.contains("guest")) {
            profileKeys.add("guest-deny-default");
        }
        for (String group : groups) {
            if (WEAVER_CAPABILITY_GROUP_PATH.equals(group)) {
                profileKeys.add("group:" + group);
                if (runtimeEntitlementProperties.enabled()) {
                    capabilities.add("agent-runtime.entitled");
                }
            }
        }
        if (profileKeys.isEmpty()) {
            profileKeys.add("deny-by-default");
        }
        // This is a member-visible policy projection only. ARC independently re-reads
        // the exact native Organization group before provisioning or reconciliation.
        return new EffectivePolicy(
                roles,
                groups,
                List.copyOf(profileKeys),
                Set.copyOf(capabilities),
                identity.providerRoleMappings());
    }

    private List<String> readinessImpact(EffectivePolicy policy) {
        if (policy.profileKeys().contains("deny-by-default")) {
            return List.of("all: policy-blocked until a known org role or mapped group grants capabilities");
        }
        if (policy.roles().contains("guest")) {
            return List.of("guest: bounded external access; capabilities remain deny-by-default unless mapped");
        }
        return List.of("member-visible states remain available, disabled_by_policy, not_configured, degraded, unavailable, or coming_later");
    }

    private String denyReason(EffectivePolicy policy, String capability) {
        if (capability.equals("agent-runtime.entitled")) {
            return "Agent Runtime Control requires current /capabilities/weaver organization membership";
        }
        return "missing mapped org role, context role, or group capability";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record EffectivePolicy(
            List<String> roles,
            List<String> groups,
            List<String> profileKeys,
            Set<String> capabilities,
            List<String> providerRoleMappings) {
        String profileKeyFor(String category) {
            return Stream.concat(Stream.of(category), profileKeys.stream()).toList().toString();
        }
    }
}

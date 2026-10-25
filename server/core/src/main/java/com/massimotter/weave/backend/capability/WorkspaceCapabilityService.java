package com.massimotter.weave.backend.capability;

import com.massimotter.weave.backend.identity.OrganizationIdentityContextResolver;
import com.massimotter.weave.backend.identity.OrganizationIdentityContext;
import com.massimotter.weave.backend.security.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.capability.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilitiesResponse;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityPolicyResponse;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityPolicyState;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityStatusResponse;
import com.massimotter.weave.backend.capability.api.WorkspaceReleaseReadinessCheckResponse;
import com.massimotter.weave.backend.capability.api.EffectivePolicyDenyResponse;
import com.massimotter.weave.backend.capability.api.EffectivePolicyResponse;
import com.massimotter.weave.backend.api.error.ApiErrorException;
import com.massimotter.weave.backend.capability.config.CapabilitySettings;
import com.massimotter.weave.backend.capability.port.WorkspaceCapabilityContribution;
import com.massimotter.weave.backend.capability.port.WorkspaceCapabilityDefinition;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Objects;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.security.oauth2.jwt.Jwt;

public class WorkspaceCapabilityService {

    private static final List<String> OWNER_ADMIN_CAPABILITIES = List.of(
            "admin_control_plane.readiness_read", "admin.policy.edit", "admin.provider.configure");
    private final OAuth2ResourceServerProperties resourceServerProperties;
    private final WeaveSecurityProperties weaveSecurityProperties;
    private final WorkspaceCapabilityProperties workspaceCapabilityProperties;
    private final OrganizationIdentityContextResolver identityContexts;

    private final Map<String, WorkspaceCapabilityContribution> contributions;
    private final Map<String, WorkspaceCapabilityDefinition> capabilityOwners;

    public WorkspaceCapabilityService(
            OAuth2ResourceServerProperties resourceServerProperties,
            WeaveSecurityProperties security,
            WorkspaceCapabilityProperties workspace,
            OrganizationIdentityContextResolver identityContexts,
            List<WorkspaceCapabilityContribution> installedContributions) {
        this.resourceServerProperties = Objects.requireNonNull(resourceServerProperties);
        this.weaveSecurityProperties = Objects.requireNonNull(security);
        this.workspaceCapabilityProperties = Objects.requireNonNull(workspace);
        this.identityContexts = Objects.requireNonNull(identityContexts);
        Map<String, WorkspaceCapabilityContribution> installed = new LinkedHashMap<>();
        Map<String, WorkspaceCapabilityDefinition> owners = new LinkedHashMap<>();
        for (var contribution : installedContributions.stream()
                .sorted(Comparator.comparingInt(value -> value.definition().order())).toList()) {
            var definition = contribution.definition();
            if (installed.putIfAbsent(definition.domainId(), contribution) != null) {
                throw new IllegalArgumentException("Duplicate workspace capability domain: " + definition.domainId());
            }
            for (String capability : definition.capabilityKeys()) {
                if (owners.putIfAbsent(capability, definition) != null || OWNER_ADMIN_CAPABILITIES.contains(capability)) {
                    throw new IllegalArgumentException("Duplicate workspace capability ownership: " + capability);
                }
            }
        }
        this.contributions = java.util.Collections.unmodifiableMap(installed);
        this.capabilityOwners = Map.copyOf(owners);
    }

    public List<WorkspaceCapabilityDefinition> installedDefinitions() {
        return contributions.values().stream().map(WorkspaceCapabilityContribution::definition).toList();
    }

    public Optional<CapabilitySettings> settings(String domainId) {
        return Optional.ofNullable(contributions.get(domainId)).map(value -> value.definition().settings());
    }

    public Map<String, WorkspaceCapabilityStatusResponse> installedSnapshots(Jwt jwt) {
        return capabilityStates(effectivePolicy(jwt));
    }

    public List<WorkspaceReleaseReadinessCheckResponse> releaseReadinessChecks() {
        Map<String, WorkspaceCapabilityStatusResponse> states = capabilityStates(effectivePolicy((Jwt) null));
        List<WorkspaceReleaseReadinessCheckResponse> checks = new ArrayList<>();
        for (var contribution : contributions.values()) {
            contribution.releaseCheck().apply(states.get(contribution.definition().domainId())).ifPresent(checks::add);
        }
        return List.copyOf(checks);
    }

    public WorkspaceCapabilitiesResponse snapshot() {
        return snapshot(null);
    }

    public WorkspaceCapabilitiesResponse snapshot(Jwt jwt) {
        return snapshotForPolicy(effectivePolicy(jwt));
    }

    /** Uses an already verified live member context; does not authenticate or create a session. */
    public WorkspaceCapabilitiesResponse snapshotForVerifiedMember(OrganizationIdentityContext identity) {
        return snapshotForPolicy(effectivePolicy(java.util.Objects.requireNonNull(identity)));
    }

    private Map<String, WorkspaceCapabilityStatusResponse> capabilityStates(EffectivePolicy policy) {
        Map<String, WorkspaceCapabilityStatusResponse> states = new LinkedHashMap<>();
        WorkspaceCapabilityReadiness shell = shellAccessReadiness();
        for (var contribution : contributions.values()) {
            var definition = contribution.definition();
            var availability = contribution.readiness().apply(shell);
            states.put(definition.domainId(), status(definition.settings(), availability.readiness(),
                    definition.category(), definition.capabilityKeys(), policy,
                    availability.memberImpact(), availability.supportSafeCode()));
        }
        return java.util.Collections.unmodifiableMap(states);
    }

    private WorkspaceCapabilitiesResponse snapshotForPolicy(EffectivePolicy policy) {
        Map<String, WorkspaceCapabilityStatusResponse> states = capabilityStates(policy);
        // This is the accepted closed wire projection, not the installed domain catalog.
        // Absent domains stay absent internally; reduced wire activation remains specification-gated.
        return new WorkspaceCapabilitiesResponse(
                status(workspaceCapabilityProperties.shellAccess(), shellAccessReadiness(), "platform identity",
                        List.of(), policy, "Weave SSO shell access is available."),
                states.get("chat"), states.get("files"), states.get("calendar"), states.get("boards"),
                states.get("meetings"), states.get("office"), states.get("decisions"),
                states.get("manuals"), states.get("devops"),
                status(workspaceCapabilityProperties.adminControlPlane(),
                        workspaceCapabilityProperties.adminControlPlane().readiness() == null
                                ? WorkspaceCapabilityReadiness.READY : workspaceCapabilityProperties.adminControlPlane().readiness(),

                        "admin control plane", List.of("admin_control_plane.readiness_read"), policy,
                        "Workspace Health exposes support-safe admin readiness without provider credentials."),
                states.get("agent-runtime"));
    }

    public WorkspaceCapabilityPolicyResponse policySnapshot(Jwt jwt) {
        requireCapability(jwt, "admin_control_plane.readiness_read", "workspace-capability-policy", "read");
        EffectivePolicy policy = effectivePolicy(jwt);
        return new WorkspaceCapabilityPolicyResponse(
                "platform identity and security",
                "Keycloak",
                "Keycloak federation and brokering contract for upstream LDAP, Active Directory, OIDC, and SAML sources",
                "issuer, subject, role, and group claims issued by Keycloak",
                policy.roles(),
                policy.groups(),
                policy.profileKeys(),
                policy.capabilities().stream().sorted().toList(),
                true,
                true,
                Optional.ofNullable(contributions.get("agent-runtime"))
                        .map(value -> value.definition().policyPosture()).orElse("not-installed"));
    }

    public List<String> grantedCapabilities(Jwt jwt) {
        return effectivePolicy(jwt).capabilities().stream().sorted().toList();
    }

    public EffectivePolicyResponse effectivePolicySnapshot(Jwt jwt, String context) {
        OrganizationIdentityContext identity = jwt == null ? null : identityContexts.resolve(jwt);
        EffectivePolicy policy = effectivePolicy(identity);
        String subject = identity == null ? "system" : identity.subject();
        String organization = identity == null ? "weave-dogfood" : identity.organizationId();
        String issuer = identity == null ? "unknown-issuer" : identity.issuer();
        String primaryIdentityKey = identity == null ? "issuer+subject:unknown-issuer#system" : identity.primaryIdentityKey();
        LinkedHashSet<String> deniedCandidates = new LinkedHashSet<>(List.of("admin.policy.edit", "admin.provider.configure"));
        for (var definition : installedDefinitions()) deniedCandidates.addAll(definition.deniedCapabilityReasons().keySet());
        List<String> denies = deniedCandidates.stream().filter(capability -> !policy.capabilities().contains(capability)).toList();
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
        requireGranted(grantedCapabilities, capability, module, operation);
    }

    /** Shares normal member policy with bounded setup after authoritative identity verification. */
    public void requireCapabilityForVerifiedMember(OrganizationIdentityContext identity,
            String capability, String module, String operation) {
        requireGranted(effectivePolicy(java.util.Objects.requireNonNull(identity)).capabilities(),
                capability, module, operation);
    }

    private void requireGranted(Set<String> grantedCapabilities, String capability, String module, String operation) {
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
        if (!enabledByDomainPolicy(capability)) {
            throw new ApiErrorException(
                    HttpStatus.FORBIDDEN,
                    "capability-policy-blocked",
                    "This action is disabled by workspace domain policy.",
                    Map.of(
                            "module", module,
                            "operation", operation,
                            "requiredCapability", capability,
                            "policyState", WorkspaceCapabilityPolicyState.DISABLED.value(),
                            "diagnosticsRedacted", true));
        }
    }

    private boolean enabledByDomainPolicy(String capability) {
        WorkspaceCapabilityDefinition owner = capabilityOwners.get(capability);
        return owner == null ? OWNER_ADMIN_CAPABILITIES.contains(capability) : owner.settings().enabled();
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
            CapabilitySettings capability,
            WorkspaceCapabilityReadiness readiness,
            String category,
            List<String> requiredCapabilities,
            EffectivePolicy policy,
            String defaultImpact) {
        return status(capability, readiness, category, requiredCapabilities, policy, defaultImpact, null);
    }

    private WorkspaceCapabilityStatusResponse status(
            CapabilitySettings capability,
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
        return effectivePolicy(jwt == null ? null : identityContexts.resolve(jwt));
    }

    private EffectivePolicy effectivePolicy(OrganizationIdentityContext identity) {
        if (identity == null) {
            LinkedHashSet<String> systemCapabilities = new LinkedHashSet<>(OWNER_ADMIN_CAPABILITIES);
            for (var definition : installedDefinitions()) systemCapabilities.addAll(definition.administratorGrants());
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
            for (var definition : installedDefinitions()) capabilities.addAll(definition.administratorGrants());
            profileKeys.add("workspace-admin");
        }
        if (roles.contains("member")) {
            for (var definition : installedDefinitions()) capabilities.addAll(definition.memberGrants());
            profileKeys.add("member-default");
        }
        if (roles.contains("guest")) {
            profileKeys.add("guest-deny-default");
        }
        for (String group : groups) {
            for (var definition : installedDefinitions()) {
                for (var grant : definition.groupGrants()) {
                    if (grant.groupPath().equals(group)) {
                        profileKeys.add(grant.profileKey());
                        capabilities.addAll(grant.capabilities());
                    }
                }
            }
        }
        if (profileKeys.isEmpty()) {
            profileKeys.add("deny-by-default");
        }
        // This projects the verified Keycloak member. Domain authorities retain their
        // operation-specific current-state rechecks.
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
        for (var definition : installedDefinitions()) {
            String reason = definition.deniedCapabilityReasons().get(capability);
            if (reason != null) {
                return reason;
            }
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

package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.model.WorkspaceCapabilitiesResponse;
import com.massimotter.weave.backend.model.WorkspaceCapabilityPolicyResponse;
import com.massimotter.weave.backend.model.WorkspaceCapabilityPolicyState;
import com.massimotter.weave.backend.model.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.model.WorkspaceCapabilityStatusResponse;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceCapabilityService {

    private static final List<String> OWNER_ADMIN_CAPABILITIES = List.of(
            "chat.read",
            "chat.send",
            "files.read",
            "files.upload",
            "calendar.read",
            "calendar.manage_events",
            "boards.read",
            "boards.update_task",
            "weaver.exec_disabled");
    private static final List<String> MEMBER_CAPABILITIES = List.of(
            "chat.read",
            "chat.send",
            "files.read",
            "files.upload",
            "calendar.read",
            "boards.read",
            "weaver.exec_disabled");
    private static final Map<String, List<String>> GROUP_CAPABILITIES = Map.of(
            "weave-calendar-editors", List.of("calendar.manage_events"),
            "weave-board-editors", List.of("boards.update_task"),
            "weave-weaver-pilot", List.of("weaver.files_read", "weaver.exec_disabled"));

    private final OAuth2ResourceServerProperties resourceServerProperties;
    private final WeaveSecurityProperties weaveSecurityProperties;
    private final WorkspaceCapabilityProperties workspaceCapabilityProperties;

    public WorkspaceCapabilityService(
            OAuth2ResourceServerProperties resourceServerProperties,
            WeaveSecurityProperties weaveSecurityProperties,
            WorkspaceCapabilityProperties workspaceCapabilityProperties) {
        this.resourceServerProperties = resourceServerProperties;
        this.weaveSecurityProperties = weaveSecurityProperties;
        this.workspaceCapabilityProperties = workspaceCapabilityProperties;
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
                standaloneStatus(
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
                        workspaceCapabilityProperties.weaver(),
                        WorkspaceCapabilityReadiness.UNAVAILABLE,
                        "Weaver",
                        List.of("weaver.enabled", "weaver.files_read", "weaver.exec_disabled"),
                        policy,
                        "Weaver is disabled by default until an admin enables a governed runtime profile."));
    }

    public WorkspaceCapabilityPolicyResponse policySnapshot(Jwt jwt) {
        EffectivePolicy policy = effectivePolicy(jwt);
        return new WorkspaceCapabilityPolicyResponse(
                "identity/IDM",
                "Keycloak",
                "OIDC/SAML adapter contract; Keycloak is the self-hosted default, not a product lock-in",
                "JWT realm roles plus groups claims from the selected IDM",
                policy.roles(),
                policy.groups(),
                policy.profileKeys(),
                policy.capabilities().stream().sorted().toList(),
                true,
                true,
                "disabled-by-default; per-user Dockerized Weaver runtime may only be generated from org policy later");
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
                granted);
    }

    private EffectivePolicy effectivePolicy(Jwt jwt) {
        if (jwt == null) {
            LinkedHashSet<String> systemCapabilities = new LinkedHashSet<>(OWNER_ADMIN_CAPABILITIES);
            systemCapabilities.remove("weaver.enabled");
            return new EffectivePolicy(
                    List.of("system"),
                    List.of(),
                    List.of("system-internal-readiness"),
                    Set.copyOf(systemCapabilities));
        }
        List<String> roles = extractRealmRoles(jwt);
        List<String> groups = extractStringList(jwt, "groups");
        LinkedHashSet<String> capabilities = new LinkedHashSet<>();
        LinkedHashSet<String> profileKeys = new LinkedHashSet<>();

        if (roles.stream().anyMatch(role -> role.equals("owner") || role.equals("admin") || role.equals("operator"))) {
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
            List<String> groupCapabilities = GROUP_CAPABILITIES.get(group);
            if (groupCapabilities != null) {
                capabilities.addAll(groupCapabilities);
                profileKeys.add("group:" + group);
            }
        }
        if (profileKeys.isEmpty()) {
            profileKeys.add("deny-by-default");
        }
        // Weaver runtime enablement is deliberately absent from built-in profiles.
        // The placeholder may expose only constrained sub-capabilities such as files_read;
        // runtime start still requires a later explicit admin policy carrying weaver.enabled.
        capabilities.remove("weaver.enabled");
        return new EffectivePolicy(roles, groups, List.copyOf(profileKeys), Set.copyOf(capabilities));
    }

    private List<String> extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null) {
            return List.of();
        }

        Object roles = realmAccess.get("roles");
        if (!(roles instanceof Collection<?> roleValues)) {
            return List.of();
        }

        return roleValues.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(role -> role.trim().toLowerCase(Locale.ROOT))
                .filter(role -> !role.isEmpty())
                .sorted()
                .toList();
    }

    private List<String> extractStringList(Jwt jwt, String claimName) {
        Object claim = jwt.getClaims().get(claimName);
        if (!(claim instanceof Collection<?> values)) {
            return List.of();
        }

        return values.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .sorted()
                .toList();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record EffectivePolicy(List<String> roles, List<String> groups, List<String> profileKeys, Set<String> capabilities) {
        String profileKeyFor(String category) {
            return Stream.concat(Stream.of(category), profileKeys.stream()).toList().toString();
        }
    }
}

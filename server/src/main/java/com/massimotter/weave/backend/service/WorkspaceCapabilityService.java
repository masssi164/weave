package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.config.WeaverRuntimeProperties;
import com.massimotter.weave.backend.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.model.WorkspaceCapabilitiesResponse;
import com.massimotter.weave.backend.model.WorkspaceCapabilityPolicyResponse;
import com.massimotter.weave.backend.model.WorkspaceCapabilityPolicyState;
import com.massimotter.weave.backend.model.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.model.WorkspaceCapabilityStatusResponse;
import com.massimotter.weave.backend.model.admin.EffectivePolicyDenyResponse;
import com.massimotter.weave.backend.model.admin.EffectivePolicyResponse;
import com.massimotter.weave.backend.exception.ApiErrorException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
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
            "weaver.exec_disabled");
    private static final List<String> OPERATOR_CAPABILITIES = List.of(
            "admin_control_plane.readiness_read",
            "operator.support_bundle.create",
            "release_evidence.read",
            "manuals.admin",
            "manuals.read",
            "weaver.exec_disabled");
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
            "release_evidence.read",
            "weaver.exec_disabled");
    private static final Map<String, List<String>> GROUP_CAPABILITIES = Map.of(
            "weave-calendar-editors", List.of("calendar.manage_events"),
            "weave-board-editors", List.of("boards.update_task"),
            "weave-meeting-hosts", List.of("meetings.host"),
            "weave-document-editors", List.of("documents.edit"),
            "weave-decision-recorders", List.of("decisions.record"),
            "weave-weaver-pilot", List.of("weaver.files_read", "weaver.exec_disabled"));

    private final OAuth2ResourceServerProperties resourceServerProperties;
    private final WeaveSecurityProperties weaveSecurityProperties;
    private final WorkspaceCapabilityProperties workspaceCapabilityProperties;
    private final WeaverRuntimeProperties weaverRuntimeProperties;

    public WorkspaceCapabilityService(
            OAuth2ResourceServerProperties resourceServerProperties,
            WeaveSecurityProperties weaveSecurityProperties,
            WorkspaceCapabilityProperties workspaceCapabilityProperties) {
        this(
                resourceServerProperties,
                weaveSecurityProperties,
                workspaceCapabilityProperties,
                new WeaverRuntimeProperties(false, null, null, null, null, null, null, null, null, null, false, false, true, false));
    }

    @Autowired
    public WorkspaceCapabilityService(
            OAuth2ResourceServerProperties resourceServerProperties,
            WeaveSecurityProperties weaveSecurityProperties,
            WorkspaceCapabilityProperties workspaceCapabilityProperties,
            WeaverRuntimeProperties weaverRuntimeProperties) {
        this.resourceServerProperties = resourceServerProperties;
        this.weaveSecurityProperties = weaveSecurityProperties;
        this.workspaceCapabilityProperties = workspaceCapabilityProperties;
        this.weaverRuntimeProperties = weaverRuntimeProperties;
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
                "OIDC/SAML selected IDM",
                "OIDC/SAML adapter contract; Keycloak is only the dogfood default, not product truth",
                "OIDC role claims plus group claims from the selected IDM",
                policy.roles(),
                policy.groups(),
                policy.profileKeys(),
                policy.capabilities().stream().sorted().toList(),
                true,
                true,
                weaverRuntimeProperties.enabled()
                        ? "governed per-user Dockerized Weaver profiles are generated only when org policy grants weaver.enabled"
                        : "disabled-by-default; per-user Dockerized Weaver runtime may only be generated from org policy later");
    }

    public List<String> grantedCapabilities(Jwt jwt) {
        return effectivePolicy(jwt).capabilities().stream().sorted().toList();
    }

    public EffectivePolicyResponse effectivePolicySnapshot(Jwt jwt, String context) {
        EffectivePolicy policy = effectivePolicy(jwt);
        OrganizationIdentityContext identity = jwt == null ? null : OrganizationIdentityContextFactory.fromJwt(jwt);
        String subject = identity == null ? "system" : identity.subject();
        String organization = identity == null ? "weave-dogfood" : identity.organizationId();
        String issuer = identity == null ? "unknown-issuer" : identity.issuer();
        String primaryIdentityKey = identity == null ? "issuer+subject:unknown-issuer#system" : identity.primaryIdentityKey();
        List<String> denies = List.of(
                "admin.policy.edit",
                "admin.provider.configure",
                "weaver.enabled").stream()
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
        if (!effectivePolicy(jwt).capabilities().contains(capability)) {
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
                    Set.copyOf(systemCapabilities),
                    List.of("system:internal-readiness"));
        }
        OrganizationIdentityContext identity = OrganizationIdentityContextFactory.fromJwt(jwt);
        List<String> roles = identity.roles();
        List<String> groups = identity.groups();
        LinkedHashSet<String> capabilities = new LinkedHashSet<>();
        LinkedHashSet<String> profileKeys = new LinkedHashSet<>();

        if (roles.stream().anyMatch(role -> role.equals("owner") || role.equals("admin"))) {
            capabilities.addAll(OWNER_ADMIN_CAPABILITIES);
            capabilities.add("admin.provider.configure");
            capabilities.add("admin.policy.edit");
            profileKeys.add("workspace-admin");
        }
        if (roles.contains("operator")) {
            capabilities.addAll(OPERATOR_CAPABILITIES);
            profileKeys.add("workspace-operator");
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
            if (weaverRuntimeProperties.enabledGroups().contains(group)) {
                profileKeys.add("group:" + group);
                if (weaverRuntimeProperties.enabled()) {
                    capabilities.add("weaver.enabled");
                }
            }
        }
        if (profileKeys.isEmpty()) {
            profileKeys.add("deny-by-default");
        }
        // Weaver runtime enablement is deliberately absent from built-in role profiles.
        // Only explicit admin runtime policy groups may carry weaver.enabled, and only when
        // the runtime generator is enabled in organization configuration.
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
        return List.of("member-visible states remain ready, disabled, degraded, or policy-blocked");
    }

    private String denyReason(EffectivePolicy policy, String capability) {
        if (capability.equals("weaver.enabled")) {
            return "weaver runtime is disabled unless an organization policy group explicitly grants it";
        }
        if (policy.roles().contains("operator")) {
            return "operator role does not automatically grant user, provider, or policy administration";
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

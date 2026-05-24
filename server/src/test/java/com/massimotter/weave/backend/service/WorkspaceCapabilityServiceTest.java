package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.model.WorkspaceCapabilityPolicyState;
import com.massimotter.weave.backend.model.WorkspaceCapabilityReadiness;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkspaceCapabilityServiceTest {

    // V01_IDM_RBAC_CAPABILITY_POLICY

    @Test
    void marksChatAndFilesDegradedUntilTheirRoutesAreConfigured() {
        WorkspaceCapabilityService service = new WorkspaceCapabilityService(
                resourceServerProperties("https://auth.weave.local/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new WorkspaceCapabilityProperties(null, null, null, null, null, null));

        var snapshot = service.snapshot(jwt(List.of("member"), List.of("workspace-default")));

        assertThat(snapshot.shellAccess().readiness()).isEqualTo(WorkspaceCapabilityReadiness.READY);
        assertThat(snapshot.chat().enabled()).isTrue();
        assertThat(snapshot.chat().readiness()).isEqualTo(WorkspaceCapabilityReadiness.DEGRADED);
        assertThat(snapshot.chat().policyState()).isEqualTo(WorkspaceCapabilityPolicyState.ALLOWED);
        assertThat(snapshot.chat().grantedCapabilities()).containsExactly("chat.read", "chat.send");
        assertThat(snapshot.files().readiness()).isEqualTo(WorkspaceCapabilityReadiness.DEGRADED);
        assertThat(snapshot.calendar().readiness()).isEqualTo(WorkspaceCapabilityReadiness.UNAVAILABLE);
    }

    @Test
    void blocksDependentCapabilitiesWhenShellAccessCannotValidateTokens() {
        WorkspaceCapabilityService service = new WorkspaceCapabilityService(
                resourceServerProperties(null),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new WorkspaceCapabilityProperties(
                        new WorkspaceCapabilityProperties.Capability(true, null, null),
                        new WorkspaceCapabilityProperties.Capability(true, "https://matrix.weave.local", null),
                        new WorkspaceCapabilityProperties.Capability(true, "https://files.weave.local", null),
                        null,
                        null,
                        null));

        var snapshot = service.snapshot(jwt(List.of("member"), List.of("workspace-default")));

        assertThat(snapshot.shellAccess().readiness()).isEqualTo(WorkspaceCapabilityReadiness.BLOCKED);
        assertThat(snapshot.chat().readiness()).isEqualTo(WorkspaceCapabilityReadiness.BLOCKED);
        assertThat(snapshot.files().readiness()).isEqualTo(WorkspaceCapabilityReadiness.BLOCKED);
    }

    @Test
    void usesConfiguredReadinessOverridesForEnabledCapabilities() {
        WorkspaceCapabilityService service = new WorkspaceCapabilityService(
                resourceServerProperties("https://auth.weave.local/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new WorkspaceCapabilityProperties(
                        new WorkspaceCapabilityProperties.Capability(true, null, null),
                        new WorkspaceCapabilityProperties.Capability(true, "https://matrix.weave.local", WorkspaceCapabilityReadiness.DEGRADED),
                        new WorkspaceCapabilityProperties.Capability(true, "https://files.weave.local", WorkspaceCapabilityReadiness.BLOCKED),
                        new WorkspaceCapabilityProperties.Capability(true, null, WorkspaceCapabilityReadiness.READY),
                        new WorkspaceCapabilityProperties.Capability(false, null, WorkspaceCapabilityReadiness.READY),
                        null));

        var snapshot = service.snapshot(jwt(List.of("member"), List.of("workspace-default")));

        assertThat(snapshot.chat().readiness()).isEqualTo(WorkspaceCapabilityReadiness.DEGRADED);
        assertThat(snapshot.files().readiness()).isEqualTo(WorkspaceCapabilityReadiness.BLOCKED);
        assertThat(snapshot.calendar().readiness()).isEqualTo(WorkspaceCapabilityReadiness.READY);
        assertThat(snapshot.boards().readiness()).isEqualTo(WorkspaceCapabilityReadiness.UNAVAILABLE);
    }

    @Test
    void marksShellAccessUnavailableWhenTheCapabilityIsDisabled() {
        WorkspaceCapabilityService service = new WorkspaceCapabilityService(
                resourceServerProperties("https://auth.weave.local/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new WorkspaceCapabilityProperties(
                        new WorkspaceCapabilityProperties.Capability(false, null, null),
                        new WorkspaceCapabilityProperties.Capability(true, "https://matrix.weave.local", null),
                        new WorkspaceCapabilityProperties.Capability(true, "https://files.weave.local", null),
                        null,
                        null,
                        null));

        var snapshot = service.snapshot(jwt(List.of("member"), List.of("workspace-default")));

        assertThat(snapshot.shellAccess().enabled()).isFalse();
        assertThat(snapshot.shellAccess().readiness()).isEqualTo(WorkspaceCapabilityReadiness.UNAVAILABLE);
        assertThat(snapshot.chat().readiness()).isEqualTo(WorkspaceCapabilityReadiness.READY);
        assertThat(snapshot.files().readiness()).isEqualTo(WorkspaceCapabilityReadiness.READY);
    }

    @Test
    void deniesCapabilitiesByDefaultWhenNoIdmRoleOrGroupMapsToAProfile() {
        WorkspaceCapabilityService service = new WorkspaceCapabilityService(
                resourceServerProperties("https://auth.weave.local/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new WorkspaceCapabilityProperties(
                        new WorkspaceCapabilityProperties.Capability(true, null, null),
                        new WorkspaceCapabilityProperties.Capability(true, "https://matrix.weave.local", null),
                        new WorkspaceCapabilityProperties.Capability(true, "https://files.weave.local", null),
                        null,
                        null,
                        null));

        var snapshot = service.snapshot(jwt(List.of("unmapped-role"), List.of("unmapped-group")));

        assertThat(snapshot.chat().readiness()).isEqualTo(WorkspaceCapabilityReadiness.BLOCKED);
        assertThat(snapshot.chat().policyState()).isEqualTo(WorkspaceCapabilityPolicyState.POLICY_BLOCKED);
        assertThat(snapshot.chat().grantedCapabilities()).isEmpty();
        assertThat(snapshot.chat().memberImpact()).contains("blocked by your role or group policy");
    }

    @Test
    void exposesSupportSafeAdminPolicySnapshotFromIdmRolesAndGroups() {
        WorkspaceCapabilityService service = new WorkspaceCapabilityService(
                resourceServerProperties("https://auth.weave.local/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new WorkspaceCapabilityProperties(null, null, null, null, null, null));

        var policy = service.policySnapshot(jwt(List.of("admin"), List.of("weave-board-editors")));

        assertThat(policy.defaultIdmProvider()).isEqualTo("Keycloak");
        assertThat(policy.adapterContract()).contains("OIDC/SAML");
        assertThat(policy.roles()).containsExactly("admin");
        assertThat(policy.groups()).containsExactly("weave-board-editors");
        assertThat(policy.profileKeys()).contains("workspace-admin", "group:weave-board-editors");
        assertThat(policy.grantedCapabilities()).contains("chat.read", "files.upload", "boards.update_task", "weaver.exec_disabled");
        assertThat(policy.grantedCapabilities()).doesNotContain("weaver.enabled");
        assertThat(policy.denyByDefault()).isTrue();
        assertThat(policy.supportSafe()).isTrue();
        assertThat(policy.weaverRuntimePosture()).contains("disabled-by-default");
    }

    @Test
    void requireCapabilityFailsClosedForUnmappedMemberActions() {
        WorkspaceCapabilityService service = new WorkspaceCapabilityService(
                resourceServerProperties("https://auth.weave.local/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new WorkspaceCapabilityProperties(null, null, null, null, null, null));

        assertThatThrownBy(() -> service.requireCapability(
                jwt(List.of("member"), List.of()),
                "calendar.manage_events",
                "calendar",
                "create-event"))
                .isInstanceOfSatisfying(ApiErrorException.class,
                        exception -> assertThat(exception.code()).isEqualTo("capability-policy-blocked"));
    }

    @Test
    void requireCapabilityAllowsExplicitGroupGrants() {
        WorkspaceCapabilityService service = new WorkspaceCapabilityService(
                resourceServerProperties("https://auth.weave.local/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new WorkspaceCapabilityProperties(null, null, null, null, null, null));

        service.requireCapability(
                jwt(List.of("member"), List.of("weave-calendar-editors")),
                "calendar.manage_events",
                "calendar",
                "create-event");
    }

    @Test
    void keepsWeaverRuntimeDisabledByDefaultEvenForPilotGroups() {
        WorkspaceCapabilityService service = new WorkspaceCapabilityService(
                resourceServerProperties("https://auth.weave.local/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new WorkspaceCapabilityProperties(
                        null,
                        null,
                        null,
                        null,
                        null,
                        new WorkspaceCapabilityProperties.Capability(false, null, null)));

        var snapshot = service.snapshot(jwt(List.of("admin"), List.of("weave-weaver-pilot")));

        assertThat(snapshot.weaver().enabled()).isFalse();
        assertThat(snapshot.weaver().readiness()).isEqualTo(WorkspaceCapabilityReadiness.UNAVAILABLE);
        assertThat(snapshot.weaver().policyState()).isEqualTo(WorkspaceCapabilityPolicyState.DISABLED);
        assertThat(snapshot.weaver().grantedCapabilities()).contains("weaver.files_read", "weaver.exec_disabled");
        assertThat(snapshot.weaver().grantedCapabilities()).doesNotContain("weaver.enabled");
    }

    private OAuth2ResourceServerProperties resourceServerProperties(String issuerUri) {
        OAuth2ResourceServerProperties properties = new OAuth2ResourceServerProperties();
        properties.getJwt().setIssuerUri(issuerUri);
        return properties;
    }

    private Jwt jwt(List<String> roles, List<String> groups) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-1")
                .claim("realm_access", Map.of("roles", roles))
                .claim("groups", groups)
                .build();
    }
}

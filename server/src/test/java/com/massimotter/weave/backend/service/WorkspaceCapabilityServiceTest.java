package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.model.admin.ProviderCapabilityHealthResponse;
import com.massimotter.weave.backend.model.WorkspaceCapabilityPolicyState;
import com.massimotter.weave.backend.model.WorkspaceCapabilityReadiness;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkspaceCapabilityServiceTest {

    // V01_IDM_RBAC_CAPABILITY_POLICY

    @Test
    void delegatedMcpTokenRetainsMemberCapabilitiesThroughTheKeycloakRoleGroup() {
        WorkspaceCapabilityService service = new WorkspaceCapabilityService(
                resourceServerProperties("https://auth.weave.test/realms/weave"),
                new WeaveSecurityProperties("weave-backend", "weave-app"),
                new WorkspaceCapabilityProperties(null, null, null, null, null, null));
        Jwt delegated = Jwt.withTokenValue("delegated")
                .header("alg", "none")
                .subject("user-1")
                .issuer("https://auth.weave.test/realms/weave")
                .claim("azp", "weave-mcp-server")
                .claim("groups", List.of("workspace-members", "weave-weaver-runtime"))
                .build();

        assertThat(service.grantedCapabilities(delegated))
                .contains("chat.send", "files.read", "calendar.read")
                .doesNotContain("agent-runtime.entitled");
    }

    @Test
    void marksChatAndFilesDegradedUntilTheirRoutesAreConfigured() {
        WorkspaceCapabilityService service = new WorkspaceCapabilityService(
                resourceServerProperties("https://auth.weave.test/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new WorkspaceCapabilityProperties(null, null, null, null, null, null));

        var snapshot = service.snapshot(jwt(List.of("member"), List.of("workspace-default")));

        assertThat(snapshot.shellAccess().readiness()).isEqualTo(WorkspaceCapabilityReadiness.READY);
        assertThat(snapshot.chat().enabled()).isTrue();
        assertThat(snapshot.chat().readiness()).isEqualTo(WorkspaceCapabilityReadiness.DEGRADED);
        assertThat(snapshot.chat().policyState()).isEqualTo(WorkspaceCapabilityPolicyState.ALLOWED);
        assertThat(snapshot.chat().supportRef()).isEqualTo("support:workspace-capability:chat:degraded:allowed");
        assertThat(snapshot.chat().grantedCapabilities()).containsExactly("chat.read", "chat.send");
        assertThat(snapshot.files().readiness()).isEqualTo(WorkspaceCapabilityReadiness.DEGRADED);
        assertThat(snapshot.files().supportRef()).isEqualTo("support:workspace-capability:files:degraded:allowed");
        assertThat(snapshot.calendar().readiness()).isEqualTo(WorkspaceCapabilityReadiness.UNAVAILABLE);
    }

    @Test
    void blocksDependentCapabilitiesWhenShellAccessCannotValidateTokens() {
        WorkspaceCapabilityService service = new WorkspaceCapabilityService(
                resourceServerProperties(null),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new WorkspaceCapabilityProperties(
                        new WorkspaceCapabilityProperties.Capability(true, null, null),
                        new WorkspaceCapabilityProperties.Capability(true, "https://matrix.weave.test", null),
                        new WorkspaceCapabilityProperties.Capability(true, "https://files.weave.test", null),
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
                resourceServerProperties("https://auth.weave.test/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new WorkspaceCapabilityProperties(
                        new WorkspaceCapabilityProperties.Capability(true, null, null),
                        new WorkspaceCapabilityProperties.Capability(true, "https://matrix.weave.test", WorkspaceCapabilityReadiness.DEGRADED),
                        new WorkspaceCapabilityProperties.Capability(true, "https://files.weave.test", WorkspaceCapabilityReadiness.BLOCKED),
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
    void degradesOnlyFilesWhenTheCachedProviderObservationIsDegraded() {
        WorkspaceCapabilityService service = new WorkspaceCapabilityService(
                resourceServerProperties("https://auth.weave.test/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new WorkspaceCapabilityProperties(
                        new WorkspaceCapabilityProperties.Capability(true, null, null),
                        new WorkspaceCapabilityProperties.Capability(true, "https://matrix.weave.test", null),
                        new WorkspaceCapabilityProperties.Capability(true, "https://files.weave.test", null),
                        null,
                        null,
                        null),
                providerHealth("files", "degraded", "files-storage-backend-unavailable", false));

        var snapshot = service.snapshot(jwt(List.of("member"), List.of("workspace-default")));

        assertThat(snapshot.files().enabled()).isTrue();
        assertThat(snapshot.files().readiness()).isEqualTo(WorkspaceCapabilityReadiness.DEGRADED);
        assertThat(snapshot.files().policyState()).isEqualTo(WorkspaceCapabilityPolicyState.ALLOWED);
        assertThat(snapshot.files().memberImpact())
                .contains("Files need admin attention")
                .doesNotContain("Nextcloud")
                .doesNotContain("WebDAV");
        assertThat(snapshot.files().supportRef())
                .isEqualTo("support:workspace-capability:files:degraded:allowed:files-storage-backend-unavailable");
        assertThat(snapshot.files().grantedCapabilities()).containsExactly("files.read", "files.upload");
        assertThat(snapshot.chat().readiness()).isEqualTo(WorkspaceCapabilityReadiness.READY);
    }

    @Test
    void treatsAStaleProviderObservationAsDomainLocalDegradation() {
        WorkspaceCapabilityService service = new WorkspaceCapabilityService(
                resourceServerProperties("https://auth.weave.test/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new WorkspaceCapabilityProperties(
                        new WorkspaceCapabilityProperties.Capability(true, null, null),
                        new WorkspaceCapabilityProperties.Capability(true, "https://matrix.weave.test", null),
                        new WorkspaceCapabilityProperties.Capability(true, "https://files.weave.test", null),
                        null,
                        null,
                        null),
                providerHealth("files", "degraded", "files-health-cache-stale", true));

        var snapshot = service.snapshot(jwt(List.of("member"), List.of("workspace-default")));

        assertThat(snapshot.files().readiness()).isEqualTo(WorkspaceCapabilityReadiness.DEGRADED);
        assertThat(snapshot.files().memberImpact())
                .contains("Files need admin attention")
                .doesNotContain("Nextcloud");
        assertThat(snapshot.files().supportRef())
                .isEqualTo("support:workspace-capability:files:degraded:allowed:files-health-cache-stale");
    }

    @Test
    void keepsOtherCapabilitiesReadyWhenCalendarAloneIsUnavailable() {
        ProviderCapabilityHealthService providerHealth = mock(ProviderCapabilityHealthService.class);
        when(providerHealth.cached("files")).thenReturn(Optional.of(observation(
                "files", "available", "files-storage-ready", false)));
        when(providerHealth.cached("calendar")).thenReturn(Optional.of(observation(
                "calendar", "unavailable", "calendar-storage-auth-failed", false)));
        WorkspaceCapabilityService service = new WorkspaceCapabilityService(
                resourceServerProperties("https://auth.weave.test/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new WorkspaceCapabilityProperties(
                        new WorkspaceCapabilityProperties.Capability(true, null, null),
                        new WorkspaceCapabilityProperties.Capability(true, "https://matrix.weave.test", null),
                        new WorkspaceCapabilityProperties.Capability(true, "https://files.weave.test", null),
                        new WorkspaceCapabilityProperties.Capability(true, "https://files.weave.test", null),
                        null,
                        null),
                providerHealth);

        var snapshot = service.snapshot(jwt(List.of("member"), List.of("workspace-default")));

        assertThat(snapshot.shellAccess().readiness()).isEqualTo(WorkspaceCapabilityReadiness.READY);
        assertThat(snapshot.chat().readiness()).isEqualTo(WorkspaceCapabilityReadiness.READY);
        assertThat(snapshot.files().readiness()).isEqualTo(WorkspaceCapabilityReadiness.READY);
        assertThat(snapshot.calendar().readiness()).isEqualTo(WorkspaceCapabilityReadiness.UNAVAILABLE);
        assertThat(snapshot.calendar().memberImpact())
                .contains("Other workspace areas remain available")
                .doesNotContain("CalDAV")
                .doesNotContain("Nextcloud");
    }

    @Test
    void runtimeProviderHealthCanLowerAConfiguredReadyCapability() {
        ProviderCapabilityHealthService providerHealth = mock(ProviderCapabilityHealthService.class);
        when(providerHealth.cached("files")).thenReturn(Optional.of(observation(
                "files", "degraded", "files-storage-backend-unavailable", false)));
        when(providerHealth.cached("calendar")).thenReturn(Optional.of(observation(
                "calendar", "unavailable", "calendar-storage-auth-failed", false)));
        WorkspaceCapabilityService service = new WorkspaceCapabilityService(
                resourceServerProperties("https://auth.weave.test/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new WorkspaceCapabilityProperties(
                        new WorkspaceCapabilityProperties.Capability(true, null, null),
                        new WorkspaceCapabilityProperties.Capability(true, "https://matrix.weave.test", null),
                        new WorkspaceCapabilityProperties.Capability(true, "https://files.weave.test", WorkspaceCapabilityReadiness.READY),
                        new WorkspaceCapabilityProperties.Capability(true, "https://files.weave.test", WorkspaceCapabilityReadiness.READY),
                        null,
                        null),
                providerHealth);

        var snapshot = service.snapshot(jwt(List.of("member"), List.of("workspace-default")));

        assertThat(snapshot.shellAccess().readiness()).isEqualTo(WorkspaceCapabilityReadiness.READY);
        assertThat(snapshot.chat().readiness()).isEqualTo(WorkspaceCapabilityReadiness.READY);
        assertThat(snapshot.files().readiness()).isEqualTo(WorkspaceCapabilityReadiness.DEGRADED);
        assertThat(snapshot.calendar().readiness()).isEqualTo(WorkspaceCapabilityReadiness.UNAVAILABLE);
        assertThat(snapshot.files().supportRef()).contains("files-storage-backend-unavailable");
        assertThat(snapshot.calendar().supportRef()).contains("calendar-storage-auth-failed");
    }

    @Test
    void runtimeProviderHealthDoesNotPromoteAnExplicitlyDegradedCapability() {
        ProviderCapabilityHealthService providerHealth = mock(ProviderCapabilityHealthService.class);
        when(providerHealth.cached("calendar")).thenReturn(Optional.of(observation(
                "calendar", "available", "calendar-storage-ready", false)));
        WorkspaceCapabilityService service = new WorkspaceCapabilityService(
                resourceServerProperties("https://auth.weave.test/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new WorkspaceCapabilityProperties(
                        new WorkspaceCapabilityProperties.Capability(true, null, null),
                        new WorkspaceCapabilityProperties.Capability(true, "https://matrix.weave.test", null),
                        new WorkspaceCapabilityProperties.Capability(true, "https://files.weave.test", null),
                        new WorkspaceCapabilityProperties.Capability(true, "https://files.weave.test", WorkspaceCapabilityReadiness.DEGRADED),
                        null,
                        null),
                providerHealth);

        var snapshot = service.snapshot(jwt(List.of("member"), List.of("workspace-default")));

        assertThat(snapshot.calendar().readiness()).isEqualTo(WorkspaceCapabilityReadiness.DEGRADED);
    }

    @Test
    void marksShellAccessUnavailableWhenTheCapabilityIsDisabled() {
        WorkspaceCapabilityService service = new WorkspaceCapabilityService(
                resourceServerProperties("https://auth.weave.test/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new WorkspaceCapabilityProperties(
                        new WorkspaceCapabilityProperties.Capability(false, null, null),
                        new WorkspaceCapabilityProperties.Capability(true, "https://matrix.weave.test", null),
                        new WorkspaceCapabilityProperties.Capability(true, "https://files.weave.test", null),
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
                resourceServerProperties("https://auth.weave.test/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new WorkspaceCapabilityProperties(
                        new WorkspaceCapabilityProperties.Capability(true, null, null),
                        new WorkspaceCapabilityProperties.Capability(true, "https://matrix.weave.test", null),
                        new WorkspaceCapabilityProperties.Capability(true, "https://files.weave.test", null),
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
                resourceServerProperties("https://auth.weave.test/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new WorkspaceCapabilityProperties(null, null, null, null, null, null));

        var policy = service.policySnapshot(jwt(List.of("admin"), List.of("weave-board-editors")));

        assertThat(policy.defaultIdmProvider()).isEqualTo("OIDC/SAML selected IDM");
        assertThat(policy.adapterContract()).contains("OIDC/SAML");
        assertThat(policy.roles()).containsExactly("admin");
        assertThat(policy.groups()).containsExactly("weave-board-editors");
        assertThat(policy.profileKeys()).contains("workspace-admin", "group:weave-board-editors");
        assertThat(policy.grantedCapabilities()).contains("chat.read", "files.upload", "boards.update_task");
        assertThat(policy.grantedCapabilities()).doesNotContain("agent-runtime.entitled");
        assertThat(policy.denyByDefault()).isTrue();
        assertThat(policy.supportSafe()).isTrue();
        assertThat(policy.agentRuntimeControlPosture()).contains("disabled-by-default");
    }

    @Test
    void requireCapabilityFailsClosedForUnmappedMemberActions() {
        WorkspaceCapabilityService service = new WorkspaceCapabilityService(
                resourceServerProperties("https://auth.weave.test/realms/weave"),
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
    void ownerAndAdminCanConfigureProvidersAndPolicyButOperatorCannotMutate() {
        WorkspaceCapabilityService service = new WorkspaceCapabilityService(
                resourceServerProperties("https://auth.weave.test/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new WorkspaceCapabilityProperties(null, null, null, null, null, null));

        service.requireCapability(jwt(List.of("owner"), List.of()), "admin.provider.configure", "admin-control-plane", "select-provider");
        service.requireCapability(jwt(List.of("admin"), List.of()), "admin.policy.edit", "admin-control-plane", "update-capability-whitelist");

        assertThatThrownBy(() -> service.requireCapability(
                jwt(List.of("operator"), List.of()),
                "admin.provider.configure",
                "admin-control-plane",
                "select-provider"))
                .isInstanceOfSatisfying(ApiErrorException.class, exception -> {
                    assertThat(exception.status().value()).isEqualTo(403);
                    assertThat(exception.code()).isEqualTo("capability-policy-blocked");
                    assertThat(exception.details()).containsEntry("requiredCapability", "admin.provider.configure");
                    assertThat(exception.details()).containsEntry("diagnosticsRedacted", true);
                });
    }

    @Test
    void guestsUnknownRolesAndUnmappedGroupsFailClosedForRepresentativeAdminReads() {
        WorkspaceCapabilityService service = new WorkspaceCapabilityService(
                resourceServerProperties("https://auth.weave.test/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new WorkspaceCapabilityProperties(null, null, null, null, null, null));

        List<Jwt> deniedIdentities = List.of(
                jwt(List.of("guest"), List.of()),
                jwt(List.of("unknown"), List.of("unmapped-group")),
                jwt(List.of(), List.of("unmapped-group")));

        for (Jwt deniedIdentity : deniedIdentities) {
            assertThatThrownBy(() -> service.requireCapability(
                    deniedIdentity,
                    "admin_control_plane.readiness_read",
                    "admin-control-plane",
                    "overview"))
                    .isInstanceOfSatisfying(ApiErrorException.class, exception -> {
                        assertThat(exception.status().value()).isEqualTo(403);
                        assertThat(exception.code()).isEqualTo("capability-policy-blocked");
                        assertThat(exception.details()).containsEntry("policyState", "policy_blocked");
                        assertThat(exception.details()).containsEntry("diagnosticsRedacted", true);
                    });
        }
    }

    @Test
    void requireCapabilityAllowsExplicitGroupGrants() {
        WorkspaceCapabilityService service = new WorkspaceCapabilityService(
                resourceServerProperties("https://auth.weave.test/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new WorkspaceCapabilityProperties(null, null, null, null, null, null));

        service.requireCapability(
                jwt(List.of("member"), List.of("weave-calendar-editors")),
                "calendar.manage_events",
                "calendar",
                "create-event");
    }

    @Test
    void keepsAgentRuntimeControlDisabledWithoutConfiguredEntitlement() {
        WorkspaceCapabilityService service = new WorkspaceCapabilityService(
                resourceServerProperties("https://auth.weave.test/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new WorkspaceCapabilityProperties(
                        null,
                        null,
                        null,
                        null,
                        null,
                        new WorkspaceCapabilityProperties.Capability(false, null, null)));

        var snapshot = service.snapshot(jwt(List.of("admin"), List.of("weave-weaver-runtime")));

        assertThat(snapshot.agentRuntimeControl().enabled()).isFalse();
        assertThat(snapshot.agentRuntimeControl().readiness()).isEqualTo(WorkspaceCapabilityReadiness.UNAVAILABLE);
        assertThat(snapshot.agentRuntimeControl().policyState()).isEqualTo(WorkspaceCapabilityPolicyState.DISABLED);
        assertThat(snapshot.agentRuntimeControl().grantedCapabilities()).isEmpty();
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
                .issuer("https://auth.example.invalid/realms/acme")
                .claim("resource_access", Map.of("weave-app", Map.of("roles", roles)))
                .claim("groups", groups)
                .build();
    }

    private ProviderCapabilityHealthService providerHealth(
            String capability,
            String state,
            String code,
            boolean stale) {
        ProviderCapabilityHealthService service = mock(ProviderCapabilityHealthService.class);
        when(service.cached(capability)).thenReturn(Optional.of(observation(capability, state, code, stale)));
        return service;
    }

    private ProviderCapabilityHealthResponse.CapabilityHealth observation(
            String capability,
            String state,
            String code,
            boolean stale) {
        return new ProviderCapabilityHealthResponse.CapabilityHealth(
                capability,
                state,
                code,
                "provider-health:" + capability + ":test",
                Instant.parse("2026-07-12T08:00:00Z"),
                Instant.parse("2026-07-12T08:01:00Z"),
                null,
                0L,
                stale,
                stale ? 1 : 0,
                5,
                0);
    }
}

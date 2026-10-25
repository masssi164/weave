package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.testing.WorkspaceCapabilityTestInstallation;
import com.massimotter.weave.backend.capability.WorkspaceCapabilityService;
import com.massimotter.weave.backend.identity.OrganizationIdentityContextResolver;
import com.massimotter.weave.backend.health.ProviderCapabilityHealthService;
import com.massimotter.weave.backend.support.HumanJwtTestSupport;
import com.massimotter.weave.backend.security.workload.config.WorkloadEntitlementProperties;
import com.massimotter.weave.backend.security.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.testing.WorkspaceCapabilityTestInstallation.Settings;
import com.massimotter.weave.backend.api.error.ApiErrorException;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilitiesResponse;
import com.massimotter.weave.backend.health.api.ProviderCapabilityHealthResponse;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityPolicyState;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityStatusResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.security.oauth2.jwt.Jwt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkspaceCapabilityServiceTest {

    @Test
    void verifiedLiveMemberContextUsesTheSamePolicyAsNormalHumanTokens() {
        var service = WorkspaceCapabilityTestInstallation.service(resourceServerProperties("https://auth.weave.test/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new Settings(null, null, null, null, null, null));
        for (String role : List.of("owner", "admin", "member", "guest", "unmapped-role")) {
            var token = jwt(List.of(role), List.of("/" + role + "s"));
            var context = OrganizationIdentityContextResolver.defaults().resolve(token);
            assertThat(service.snapshotForVerifiedMember(context)).isEqualTo(service.snapshot(token));
            if (role.equals("owner") || role.equals("admin")) {
                service.requireCapabilityForVerifiedMember(context, "admin.policy.edit", "spaces", "bootstrap");
            } else {
                assertThatThrownBy(() -> service.requireCapabilityForVerifiedMember(
                        context, "admin.policy.edit", "spaces", "bootstrap"))
                        .isInstanceOfSatisfying(ApiErrorException.class,
                                error -> assertThat(error.code()).isEqualTo("capability-policy-blocked"));
            }
        }
        assertThatThrownBy(() -> service.snapshotForVerifiedMember(null)).isInstanceOf(NullPointerException.class);
    }

    // V01_KEYCLOAK_RBAC_CAPABILITY_POLICY

    @Test
    void delegatedMcpTokenRetainsMemberCapabilitiesThroughTheWeaveAppClientRole() {
        WorkspaceCapabilityService service = WorkspaceCapabilityTestInstallation.service(
                resourceServerProperties("https://auth.weave.test/realms/weave"),
                new WeaveSecurityProperties("weave-backend", "weave-app"),
                new Settings(null, null, null, null, null, null));
        Jwt delegated = Jwt.withTokenValue("delegated")
                .header("alg", "none")
                .subject("user-1")
                .issuer("https://auth.weave.test/realms/weave")
                .claim("azp", "weave-mcp-server")
                .claim(
                        "organization",
                        HumanJwtTestSupport
                                .organizationWithRolesAndGroups(
                                        List.of("member"),
                                        List.of("/members", "/capabilities/weaver")))
                .build();

        assertThat(service.grantedCapabilities(delegated))
                .contains("chat.send", "files.read", "calendar.read")
                .doesNotContain("agent-runtime.entitled");
    }

    @Test
    void legacyRealmGroupsCannotGrantCanonicalHumanRoles() {
        WorkspaceCapabilityService service = WorkspaceCapabilityTestInstallation.service(
                resourceServerProperties("https://auth.weave.test/realms/weave"),
                new WeaveSecurityProperties("weave-backend", "weave-app"),
                new Settings(null, null, null, null, null, null));
        Jwt delegated = Jwt.withTokenValue("delegated")
                .header("alg", "none")
                .subject("user-1")
                .issuer("https://auth.weave.test/realms/weave")
                .claim("azp", "weave-mcp-server")
                .claim("groups", List.of("/weave/owners", "/weave/admins", "/weave/members"))
                .build();

        assertThat(service.grantedCapabilities(delegated)).isEmpty();
    }

    @Test
    void projectsOnlyTheNativeWeaverOrganizationCapabilityForMemberUx() {
        WorkspaceCapabilityService service = WorkspaceCapabilityTestInstallation.service(
                resourceServerProperties("https://auth.weave.test/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new Settings(null, null, null, null, null, null),
                new WorkloadEntitlementProperties(true, null, List.of("calendar.read")));

        assertThat(service.grantedCapabilities(
                jwt(List.of("member"), List.of("/capabilities/weaver"))))
                .contains("agent-runtime.entitled");
        assertThat(service.grantedCapabilities(
                jwt(List.of("member"), List.of("/weave/weaver-runtime"))))
                .doesNotContain("agent-runtime.entitled");
    }

    @Test
    void marksChatAndFilesDegradedUntilTheirRoutesAreConfigured() {
        WorkspaceCapabilityService service = WorkspaceCapabilityTestInstallation.service(
                resourceServerProperties("https://auth.weave.test/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new Settings(null, null, null, null, null, null));

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
        WorkspaceCapabilityService service = WorkspaceCapabilityTestInstallation.service(
                resourceServerProperties(null),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new Settings(
                        new Settings.Capability(true, null, null),
                        new Settings.Capability(true, "https://matrix.weave.test", null),
                        new Settings.Capability(true, "https://files.weave.test", null),
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
        WorkspaceCapabilityService service = WorkspaceCapabilityTestInstallation.service(
                resourceServerProperties("https://auth.weave.test/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new Settings(
                        new Settings.Capability(true, null, null),
                        new Settings.Capability(true, "https://matrix.weave.test", WorkspaceCapabilityReadiness.DEGRADED),
                        new Settings.Capability(true, "https://files.weave.test", WorkspaceCapabilityReadiness.BLOCKED),
                        new Settings.Capability(true, null, WorkspaceCapabilityReadiness.READY),
                        new Settings.Capability(false, null, WorkspaceCapabilityReadiness.READY),
                        null));

        var snapshot = service.snapshot(jwt(List.of("member"), List.of("workspace-default")));

        assertThat(snapshot.chat().readiness()).isEqualTo(WorkspaceCapabilityReadiness.DEGRADED);
        assertThat(snapshot.files().readiness()).isEqualTo(WorkspaceCapabilityReadiness.BLOCKED);
        assertThat(snapshot.calendar().readiness()).isEqualTo(WorkspaceCapabilityReadiness.READY);
        assertThat(snapshot.boards().readiness()).isEqualTo(WorkspaceCapabilityReadiness.UNAVAILABLE);
    }

    @Test
    void degradesOnlyFilesWhenTheCachedProviderObservationIsDegraded() {
        WorkspaceCapabilityService service = WorkspaceCapabilityTestInstallation.service(
                resourceServerProperties("https://auth.weave.test/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new Settings(
                        new Settings.Capability(true, null, null),
                        new Settings.Capability(true, "https://matrix.weave.test", null),
                        new Settings.Capability(true, "https://files.weave.test", null),
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
    void aConfiguredChatRouteCannotOverrideDegradedProviderEvidence() {
        WorkspaceCapabilityService service = WorkspaceCapabilityTestInstallation.service(
                resourceServerProperties("https://auth.weave.test/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new Settings(
                        new Settings.Capability(true, null, null),
                        new Settings.Capability(
                                true,
                                "https://matrix.weave.test",
                                WorkspaceCapabilityReadiness.READY),
                        new Settings.Capability(true, "https://files.weave.test", null),
                        null,
                        null,
                        null),
                providerHealth("chat", "degraded", "chat-provider-authenticated-capability-degraded", false));

        var snapshot = service.snapshot(jwt(List.of("member"), List.of("workspace-default")));

        assertThat(snapshot.chat().readiness()).isEqualTo(WorkspaceCapabilityReadiness.DEGRADED);
        assertThat(snapshot.chat().memberImpact())
                .contains("Chat needs admin attention")
                .doesNotContain("Synapse")
                .doesNotContain("Matrix");
        assertThat(snapshot.chat().supportRef())
                .isEqualTo("support:workspace-capability:chat:degraded:allowed:"
                        + "chat-provider-authenticated-capability-degraded");
        assertThat(snapshot.files().readiness()).isEqualTo(WorkspaceCapabilityReadiness.READY);
    }

    @Test
    void treatsAStaleProviderObservationAsDomainLocalDegradation() {
        WorkspaceCapabilityService service = WorkspaceCapabilityTestInstallation.service(
                resourceServerProperties("https://auth.weave.test/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new Settings(
                        new Settings.Capability(true, null, null),
                        new Settings.Capability(true, "https://matrix.weave.test", null),
                        new Settings.Capability(true, "https://files.weave.test", null),
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
        WorkspaceCapabilityService service = WorkspaceCapabilityTestInstallation.service(
                resourceServerProperties("https://auth.weave.test/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new Settings(
                        new Settings.Capability(true, null, null),
                        new Settings.Capability(true, "https://matrix.weave.test", null),
                        new Settings.Capability(true, "https://files.weave.test", null),
                        new Settings.Capability(true, "https://files.weave.test", null),
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
        WorkspaceCapabilityService service = WorkspaceCapabilityTestInstallation.service(
                resourceServerProperties("https://auth.weave.test/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new Settings(
                        new Settings.Capability(true, null, null),
                        new Settings.Capability(true, "https://matrix.weave.test", null),
                        new Settings.Capability(true, "https://files.weave.test", WorkspaceCapabilityReadiness.READY),
                        new Settings.Capability(true, "https://files.weave.test", WorkspaceCapabilityReadiness.READY),
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
        WorkspaceCapabilityService service = WorkspaceCapabilityTestInstallation.service(
                resourceServerProperties("https://auth.weave.test/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new Settings(
                        new Settings.Capability(true, null, null),
                        new Settings.Capability(true, "https://matrix.weave.test", null),
                        new Settings.Capability(true, "https://files.weave.test", null),
                        new Settings.Capability(true, "https://files.weave.test", WorkspaceCapabilityReadiness.DEGRADED),
                        null,
                        null),
                providerHealth);

        var snapshot = service.snapshot(jwt(List.of("member"), List.of("workspace-default")));

        assertThat(snapshot.calendar().readiness()).isEqualTo(WorkspaceCapabilityReadiness.DEGRADED);
    }

    @Test
    void marksShellAccessUnavailableWhenTheCapabilityIsDisabled() {
        WorkspaceCapabilityService service = WorkspaceCapabilityTestInstallation.service(
                resourceServerProperties("https://auth.weave.test/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new Settings(
                        new Settings.Capability(false, null, null),
                        new Settings.Capability(true, "https://matrix.weave.test", null),
                        new Settings.Capability(true, "https://files.weave.test", null),
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
        WorkspaceCapabilityService service = WorkspaceCapabilityTestInstallation.service(
                resourceServerProperties("https://auth.weave.test/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new Settings(
                        new Settings.Capability(true, null, null),
                        new Settings.Capability(true, "https://matrix.weave.test", null),
                        new Settings.Capability(true, "https://files.weave.test", null),
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
        WorkspaceCapabilityService service = WorkspaceCapabilityTestInstallation.service(
                resourceServerProperties("https://auth.weave.test/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new Settings(null, null, null, null, null, null));

        var policy = service.policySnapshot(jwt(List.of("admin"), List.of("/capabilities/weaver")));

        assertThat(policy.platformIdentityAuthority()).isEqualTo("Keycloak");
        assertThat(policy.federationContract()).contains("LDAP", "OIDC", "SAML");
        assertThat(policy.roles()).containsExactly("admin");
        assertThat(policy.groups()).containsExactly("/capabilities/weaver");
        assertThat(policy.profileKeys()).contains("workspace-admin", "group:/capabilities/weaver");
        assertThat(policy.grantedCapabilities()).contains("chat.read", "files.upload", "boards.update_task");
        assertThat(policy.grantedCapabilities()).doesNotContain("agent-runtime.entitled");
        assertThat(policy.denyByDefault()).isTrue();
        assertThat(policy.supportSafe()).isTrue();
        assertThat(policy.agentRuntimeControlPosture()).contains("disabled-by-default");
    }

    @Test
    void ordinaryMembersCanManageCalendarEventsWithoutAdministrativeCapabilities() {
        WorkspaceCapabilityService service = domainPolicyService(true, WorkspaceCapabilityReadiness.READY);

        service.requireCapability(jwt(List.of("member"), List.of()),
                "calendar.manage_events", "calendar", "create-event");
        assertThatThrownBy(() -> service.requireCapability(
                jwt(List.of("member"), List.of()),
                "admin.provider.configure", "admin", "configure-provider"))
                .isInstanceOfSatisfying(ApiErrorException.class,
                        exception -> assertThat(exception.code()).isEqualTo("capability-policy-blocked"));
        assertThatThrownBy(() -> service.requireCapability(
                jwt(List.of("guest"), List.of()),
                "calendar.manage_events", "calendar", "create-event"))
                .isInstanceOfSatisfying(ApiErrorException.class,
                        exception -> assertThat(exception.code()).isEqualTo("capability-policy-blocked"));
    }

    @Test
    void disabledDomainPolicyRejectsHumanDeviceAndVerifiedMemberGrants() {
        WorkspaceCapabilityService service = domainPolicyService(false, WorkspaceCapabilityReadiness.READY);
        Jwt owner = jwt(List.of("owner"), List.of("/capabilities/weaver"));
        var identity = OrganizationIdentityContextResolver.defaults().resolve(owner);

        for (var domain : domainStatuses(service.snapshot(owner))) {
            assertThat(domain.policyState()).isEqualTo(WorkspaceCapabilityPolicyState.DISABLED);
            assertThat(domain.grantedCapabilities()).isNotEmpty();
            for (String capability : domain.grantedCapabilities()) {
                assertDisabledPolicy(() -> service.requireCapability(owner, capability, "domain", "read"), capability);
                assertDisabledPolicy(() -> service.requireCapabilityForVerifiedMember(
                        identity, capability, "domain", "read"), capability);
                Jwt device = Jwt.withTokenValue("device").header("alg", "none").subject("device-1")
                        .claim("weave_auth_method", "device_credential")
                        .claim("weave_capabilities", List.of(capability)).build();
                assertDisabledPolicy(() -> service.requireCapability(device, capability, "domain", "read"), capability);
            }
        }
        service.requireCapability(owner, "admin.policy.edit", "admin-control-plane", "update-policy");
        service.requireCapability(owner, "admin.provider.configure", "admin-control-plane", "configure-provider");
    }

    @Test
    void enabledDomainPolicyDoesNotTurnReadinessIntoAuthorization() {
        WorkspaceCapabilityService service = domainPolicyService(true, WorkspaceCapabilityReadiness.BLOCKED);
        Jwt owner = jwt(List.of("owner"), List.of("/capabilities/weaver"));

        for (var domain : domainStatuses(service.snapshot(owner))) {
            assertThat(domain.enabled()).isTrue();
            assertThat(domain.readiness()).isEqualTo(WorkspaceCapabilityReadiness.BLOCKED);
            assertThat(domain.grantedCapabilities()).isNotEmpty();
            for (String capability : domain.grantedCapabilities()) {
                service.requireCapability(owner, capability, "domain", "read");
            }
        }
    }

    @Test
    void disabledFilesDoesNotBlockShellOtherDomainsOrSetupAuthority() {
        var ready = new Settings.Capability(true, null, WorkspaceCapabilityReadiness.READY);
        var service = WorkspaceCapabilityTestInstallation.service(
                resourceServerProperties("https://auth.weave.test/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new Settings(null, ready,
                        new Settings.Capability(false, null, null), ready, null, null));
        Jwt owner = jwt(List.of("owner"), List.of());
        var snapshot = service.snapshot(owner);

        assertThat(snapshot.files().policyState()).isEqualTo(WorkspaceCapabilityPolicyState.DISABLED);
        assertThat(snapshot.shellAccess().readiness()).isEqualTo(WorkspaceCapabilityReadiness.READY);
        assertThat(snapshot.chat().policyState()).isEqualTo(WorkspaceCapabilityPolicyState.ALLOWED);
        assertThat(snapshot.calendar().policyState()).isEqualTo(WorkspaceCapabilityPolicyState.ALLOWED);
        service.requireCapability(owner, "chat.send", "chat", "send");
        service.requireCapability(owner, "calendar.manage_events", "calendar", "create");
        service.requireCapability(owner, "admin.policy.edit", "admin-control-plane", "update-policy");
        service.requireCapability(owner, "admin.provider.configure", "admin-control-plane", "configure-provider");
        assertDisabledPolicy(() -> service.requireCapability(owner, "files.upload", "files", "create"), "files.upload");
    }

    @Test
    void ownerAndAdminCanConfigureProvidersAndPolicyButOperatorCannotMutate() {
        WorkspaceCapabilityService service = WorkspaceCapabilityTestInstallation.service(
                resourceServerProperties("https://auth.weave.test/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new Settings(null, null, null, null, null, null));

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
        WorkspaceCapabilityService service = WorkspaceCapabilityTestInstallation.service(
                resourceServerProperties("https://auth.weave.test/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new Settings(null, null, null, null, null, null));

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
    void legacyRealmGroupCannotGrantAProductCapability() {
        WorkspaceCapabilityService service = WorkspaceCapabilityTestInstallation.service(
                resourceServerProperties("https://auth.weave.test/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new Settings(null, null, null, null, null, null));

        assertThatThrownBy(() -> service.requireCapability(
                jwt(List.of("member"), List.of("/weave-calendar-editors")),
                "calendar.manage_events",
                "calendar",
                "create-event"))
                .isInstanceOf(ApiErrorException.class);
    }

    @Test
    void keepsAgentRuntimeControlDisabledWithoutConfiguredEntitlement() {
        WorkspaceCapabilityService service = WorkspaceCapabilityTestInstallation.service(
                resourceServerProperties("https://auth.weave.test/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new Settings(
                        null,
                        null,
                        null,
                        null,
                        null,
                        new Settings.Capability(false, null, null)));

        var snapshot = service.snapshot(jwt(List.of("admin"), List.of("/capabilities/weaver")));

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

    private WorkspaceCapabilityService domainPolicyService(boolean enabled, WorkspaceCapabilityReadiness readiness) {
        var domain = new Settings.Capability(enabled, null, readiness);
        return WorkspaceCapabilityTestInstallation.service(
                resourceServerProperties("https://auth.weave.test/realms/weave"),
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new Settings(null, domain, domain, domain, domain, domain,
                        domain, domain, domain, domain, null, domain),
                new WorkloadEntitlementProperties(true, null, List.of("calendar.read")));
    }

    private List<WorkspaceCapabilityStatusResponse> domainStatuses(WorkspaceCapabilitiesResponse snapshot) {
        return List.of(snapshot.chat(), snapshot.files(), snapshot.calendar(), snapshot.boards(),
                snapshot.meetingsCalls(), snapshot.documentsCollaboration(), snapshot.decisionsEvidence(),
                snapshot.manualsHelp(), snapshot.releaseEvidence(), snapshot.agentRuntimeControl());
    }

    private void assertDisabledPolicy(Runnable operation, String capability) {
        assertThatThrownBy(operation::run).as("disabled policy for %s", capability)
                .isInstanceOfSatisfying(ApiErrorException.class, error -> {
                    assertThat(error.status().value()).isEqualTo(403);
                    assertThat(error.code()).isEqualTo("capability-policy-blocked");
                    assertThat(error.details()).containsEntry("policyState", "disabled")
                            .containsEntry("requiredCapability", capability)
                            .containsEntry("diagnosticsRedacted", true);
                });
    }

    private Jwt jwt(List<String> roles, List<String> groups) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-1")
                .issuer("https://auth.example.invalid/realms/acme")
                .claim(
                        "organization",
                        HumanJwtTestSupport
                                .organizationWithRolesAndGroups(roles, groups))
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

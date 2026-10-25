package com.massimotter.weave.backend.service.spaces;

import com.massimotter.weave.backend.testing.WorkspaceCapabilityTestInstallation;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityPolicyState;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilitiesResponse;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityStatusResponse;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.calendar.spaces.CalendarDefaultSpacePreparationContribution;
import com.massimotter.weave.backend.files.spaces.FilesDefaultSpacePreparationContribution;
import com.massimotter.weave.backend.calendar.port.CalendarProviderPort;
import com.massimotter.weave.backend.providerbinding.contract.ProviderConformanceProfile;
import com.massimotter.weave.backend.providerbinding.domain.ProviderBinding;
import com.massimotter.weave.backend.providerbinding.port.ProviderBindingRepository;
import com.massimotter.weave.backend.service.calendar.CalendarProviderBindingReadiness;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import com.massimotter.weave.backend.security.config.DeploymentOrganizationProperties;
import com.massimotter.weave.backend.api.error.ApiErrorException;
import com.massimotter.weave.backend.identity.IdentityReferences;
import com.massimotter.weave.backend.identity.invitation.KeycloakIdentityAdminClient;
import com.massimotter.weave.backend.identity.invitation.KeycloakIdentityAdminClient.MemberAuthorization;
import com.massimotter.weave.backend.identity.invitation.KeycloakIdentityAdminClient.ProviderMember;
import com.massimotter.weave.backend.model.*;
import com.massimotter.weave.backend.identity.OrganizationIdentityContext;
import com.massimotter.weave.backend.identity.OrganizationIdentityContextResolver;
import com.massimotter.weave.backend.capability.WorkspaceCapabilityService;
import com.massimotter.weave.backend.spaces.port.DefaultSpacePreparationPort;
import com.massimotter.weave.backend.spaces.preparation.DefaultSpacePreparationContribution;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.http.HttpStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DefaultSpaceBootstrapServiceTest {
    private final KeycloakIdentityAdminClient keycloak = mock(KeycloakIdentityAdminClient.class);
    private final OrganizationIdentityContextResolver identities = mock(OrganizationIdentityContextResolver.class);
    private final WorkspaceCapabilityService capabilities = mock(WorkspaceCapabilityService.class);
    private final DefaultSpacePreparationPort spaces = mock(DefaultSpacePreparationPort.class);
    private final AuditEventPublisher audit = mock(AuditEventPublisher.class);
    private DefaultSpaceBootstrapService service;
    private final ProviderBindingRepository calendarBindings = mock(ProviderBindingRepository.class);
    private final CalendarProviderPort calendarProvider = mock(CalendarProviderPort.class);
    private static final String ISSUER = "https://auth.example.test/realms/weave";

    @BeforeEach void setUp() {
        var properties = new OAuth2ResourceServerProperties();
        properties.getJwt().setIssuerUri(ISSUER);
        when(calendarProvider.conformanceProfile()).thenReturn(new ProviderConformanceProfile(
                "calendar", "weave-native", Set.of(), Map.of(), true, true, true));
        when(calendarProvider.configured()).thenReturn(true);
        when(calendarBindings.current("org:configured", "calendar")).thenReturn(Optional.of(new ProviderBinding(
                "org:configured", "calendar", 1, "weave-native", CalendarProviderBindingReadiness.CONFIGURATION_REF,
                ProviderBinding.State.ACTIVE, Instant.EPOCH)));
        service = new DefaultSpaceBootstrapService(keycloak, identities, capabilities, spaces, audit, properties,
                new DeploymentOrganizationProperties("provider-org", "weave"),
                List.of(new FilesDefaultSpacePreparationContribution(), new CalendarDefaultSpacePreparationContribution(
                        new CalendarProviderBindingReadiness(calendarBindings, List.of(calendarProvider)))));
        when(keycloak.configuredOrganizationId()).thenReturn("provider-org");
        when(identities.configuredOrganizationId()).thenReturn("org:configured");
        when(keycloak.members("provider-org")).thenReturn(List.of(member("actual-owner", List.of("owner"), true)));
        when(keycloak.memberAuthorization("provider-org", "actual-owner")).thenReturn(
                new MemberAuthorization("actual-owner", true, true, List.of("owner"), List.of("/owners")));
        when(capabilities.snapshotForVerifiedMember(any())).thenReturn(snapshot(ready(), ready()));
        when(spaces.prepare(anyString(), anyString())).thenReturn(DefaultSpacePreparationPort.Result.CREATED);
    }

    @Test void missingCalendarBindingPreventsReadyHandoffDespiteHealthyEnabledCapabilitySnapshot() {
        when(calendarBindings.current("org:configured", "calendar")).thenReturn(Optional.empty());
        assertThatThrownBy(service::prepare).isInstanceOfSatisfying(ApiErrorException.class,
                error -> assertThat(error.code()).isEqualTo("calendar-provider-unavailable"));
        verifyNoInteractions(spaces, audit);
        verify(calendarProvider, never()).query(any(), any(), any(), any());
    }

    @Test void bindingChangeDuringPreparationCannotPublishSuccessfulEvidence() {
        var binding = new ProviderBinding("org:configured", "calendar", 1, "weave-native",
                CalendarProviderBindingReadiness.CONFIGURATION_REF, ProviderBinding.State.ACTIVE, Instant.EPOCH);
        when(calendarBindings.current("org:configured", "calendar")).thenReturn(Optional.of(binding), Optional.empty());
        assertThatThrownBy(service::prepare).isInstanceOfSatisfying(ApiErrorException.class,
                error -> assertThat(error.code()).isEqualTo("calendar-provider-unavailable"));
        verify(audit, never()).publish(any());
    }

    @Test void actualOwnerContextIsVerifiedAndAuditedWithoutIdentityWrites() {
        var result = service.prepare();
        assertThat(result.outcome()).isEqualTo("CREATED");
        assertThat(result.ownerAccessVerified()).isTrue();
        assertThat(result.supportSafe()).isTrue();
        assertThat(result.containsSecretValues()).isFalse();
        assertThat(result.toString()).doesNotContain("actual-owner", "provider-org", "acct_");
        verify(spaces).prepare("org:configured", IdentityReferences.accountId(ISSUER, "actual-owner"));
        ArgumentCaptor<OrganizationIdentityContext> context = ArgumentCaptor.forClass(OrganizationIdentityContext.class);
        verify(capabilities).requireCapabilityForVerifiedMember(context.capture(), eq("admin.policy.edit"), eq("spaces"), eq("bootstrap"));
        assertThat(context.getValue().groups()).containsExactly("/owners");
        assertThat(context.getValue().issuer()).isEqualTo(ISSUER);
        verify(keycloak).configuredOrganizationId();
        verify(keycloak).members("provider-org");
        verify(keycloak).memberAuthorization("provider-org", "actual-owner");
        verifyNoMoreInteractions(keycloak);
        verify(audit).publish(argThat(event -> event.actorRef().equals("operator:default-space-bootstrap")
                && event.payload().get("result").equals("CREATED")));
    }

    @Test void noOwnerDisabledOwnerMultipleOwnersAndMixedRoleFailBeforeSpaceWrites() {
        for (var inventory : List.of(List.<ProviderMember>of(),
                List.of(member("owner", List.of("owner"), false)),
                List.of(member("one", List.of("owner"), true), member("two", List.of("owner"), true)),
                List.of(member("owner", List.of("owner", "admin"), true)),
                List.of(member("member", List.of("member"), true)))) {
            when(keycloak.members("provider-org")).thenReturn(inventory);
            assertThatThrownBy(service::prepare).isInstanceOfSatisfying(ApiErrorException.class,
                    error -> assertThat(error.code()).isEqualTo("default-space-owner-unavailable"));
        }
        verifyNoInteractions(spaces, audit);
    }

    @Test void liveRecheckCannotChangeDisableOrBroadenSelectedOwner() {
        for (var owner : List.of(new MemberAuthorization("actual-owner", true, false, List.of("owner"), List.of()),
                new MemberAuthorization("actual-owner", false, true, List.of("owner"), List.of()),
                new MemberAuthorization("other-owner", true, true, List.of("owner"), List.of()),
                new MemberAuthorization("actual-owner", true, true, List.of("owner", "member"), List.of()))) {
            when(keycloak.memberAuthorization("provider-org", "actual-owner")).thenReturn(owner);
            assertThatThrownBy(service::prepare).isInstanceOf(ApiErrorException.class);
        }
        verifyNoInteractions(spaces, audit);
    }

    @Test void unavailableInventoryDoesNotExposeProviderFailureOrMutate() {
        when(keycloak.members("provider-org")).thenThrow(new IllegalStateException("private provider payload"));
        assertThatThrownBy(service::prepare).isInstanceOfSatisfying(ApiErrorException.class,
                error -> assertThat(error.code()).isEqualTo("default-space-owner-verification-unavailable"))
                .hasMessageNotContaining("private provider payload");
        verifyNoInteractions(spaces, audit);
    }

    @Test void wrongConfiguredOrganizationFailsClosed() {
        when(keycloak.configuredOrganizationId()).thenReturn("other-org");
        assertThatThrownBy(service::prepare).isInstanceOf(ApiErrorException.class);
        verify(keycloak, never()).members(anyString());
        verifyNoInteractions(spaces, audit);
    }

    @Test void currentAdminPolicyDenialPreventsAllSpaceMutation() {
        doThrow(new ApiErrorException(HttpStatus.FORBIDDEN, "capability-policy-blocked", "denied", Map.of()))
                .when(capabilities).requireCapabilityForVerifiedMember(any(), eq("admin.policy.edit"), anyString(), anyString());
        assertThatThrownBy(service::prepare).isInstanceOfSatisfying(ApiErrorException.class,
                error -> assertThat(error.code()).isEqualTo("capability-policy-blocked"));
        verifyNoInteractions(spaces, audit);
    }

    @Test void disabledBlockedUnavailableAndDegradedSurfacesPreventPreparation() {
        for (var state : List.of(new WorkspaceCapabilityStatusResponse(false, WorkspaceCapabilityReadiness.READY,
                        WorkspaceCapabilityPolicyState.DISABLED, "policy", "disabled", List.of()),
                new WorkspaceCapabilityStatusResponse(true, WorkspaceCapabilityReadiness.READY,
                        WorkspaceCapabilityPolicyState.POLICY_BLOCKED, "policy", "denied", List.of()),
                new WorkspaceCapabilityStatusResponse(true, WorkspaceCapabilityReadiness.UNAVAILABLE,
                        WorkspaceCapabilityPolicyState.UNAVAILABLE, "policy", "unavailable", List.of()),
                new WorkspaceCapabilityStatusResponse(true, WorkspaceCapabilityReadiness.DEGRADED,
                        WorkspaceCapabilityPolicyState.ALLOWED, "policy", "degraded", List.of()))) {
            when(capabilities.snapshotForVerifiedMember(any())).thenReturn(snapshot(state, ready()));
            assertThatThrownBy(service::prepare).isInstanceOf(ApiErrorException.class);
            when(capabilities.snapshotForVerifiedMember(any())).thenReturn(snapshot(ready(), state));
            assertThatThrownBy(service::prepare).isInstanceOf(ApiErrorException.class);
        }
        verifyNoInteractions(spaces, audit);
    }

    @Test void existingAccessIsVerifiedAndConflictIsNeverRestored() {
        when(spaces.prepare(anyString(), anyString())).thenReturn(DefaultSpacePreparationPort.Result.VERIFIED);
        assertThat(service.prepare().outcome()).isEqualTo("VERIFIED");
        when(spaces.prepare(anyString(), anyString())).thenThrow(new DefaultSpacePreparationPort.Conflict());
        assertThatThrownBy(service::prepare).isInstanceOfSatisfying(ApiErrorException.class,
                error -> assertThat(error.code()).isEqualTo("default-space-owner-access-conflict"));
    }

    @Test void noInstalledDomainDoesNotRequireDomainCapabilitiesOrReadiness() {
        service = withDomains(List.of());
        assertThat(service.prepare().outcome()).isEqualTo("CREATED");
        verify(capabilities, never()).snapshotForVerifiedMember(any());
        verify(capabilities, never()).requireCapabilityForVerifiedMember(any(), eq("files.read"), anyString(), anyString());
        verify(capabilities, never()).requireCapabilityForVerifiedMember(any(), eq("calendar.read"), anyString(), anyString());
        verify(calendarBindings, never()).current(anyString(), anyString());
        verify(spaces).prepare("org:configured", IdentityReferences.accountId(ISSUER, "actual-owner"));
    }

    @Test void filesOnlyPreparationIgnoresOmittedCalendarStateAndBinding() {
        service = withDomains(List.of(new FilesDefaultSpacePreparationContribution()));
        when(capabilities.snapshotForVerifiedMember(any())).thenReturn(snapshot(ready(), null));
        assertThat(service.prepare().outcome()).isEqualTo("CREATED");
        verify(capabilities).requireCapabilityForVerifiedMember(any(), eq("files.upload"), eq("spaces"), eq("prepare-human-test"));
        verify(capabilities, never()).requireCapabilityForVerifiedMember(any(), eq("calendar.manage_events"), anyString(), anyString());
        verify(calendarBindings, never()).current(anyString(), anyString());
    }

    @Test void calendarOnlyPreparationIgnoresOmittedFilesStateAndCapabilities() {
        service = withDomains(List.of(new CalendarDefaultSpacePreparationContribution(
                new CalendarProviderBindingReadiness(calendarBindings, List.of(calendarProvider)))));
        when(capabilities.snapshotForVerifiedMember(any())).thenReturn(snapshot(null, ready()));
        assertThat(service.prepare().outcome()).isEqualTo("CREATED");
        verify(capabilities, never()).requireCapabilityForVerifiedMember(any(), eq("files.read"), anyString(), anyString());
        verify(calendarBindings, times(2)).current("org:configured", "calendar");
    }

    @Test void allReadinessChecksPrecedeSpaceMutationAndRevisionRecheckPrecedesAudit() {
        service.prepare();
        var sequence = inOrder(capabilities, calendarBindings, spaces, audit);
        sequence.verify(capabilities).snapshotForVerifiedMember(any());
        sequence.verify(calendarBindings).current("org:configured", "calendar");
        sequence.verify(spaces).prepare("org:configured", IdentityReferences.accountId(ISSUER, "actual-owner"));
        sequence.verify(calendarBindings).current("org:configured", "calendar");
        sequence.verify(audit).publish(any());
    }

    private DefaultSpaceBootstrapService withDomains(List<DefaultSpacePreparationContribution> contributions) {
        var properties = new OAuth2ResourceServerProperties();
        properties.getJwt().setIssuerUri(ISSUER);
        return new DefaultSpaceBootstrapService(keycloak, identities, capabilities, spaces, audit, properties,
                new DeploymentOrganizationProperties("provider-org", "weave"), contributions);
    }

    private static ProviderMember member(String subject, List<String> roles, boolean enabled) {
        return new ProviderMember(subject, "private@example.test", "Private name", roles, List.of(), enabled);
    }
    private static WorkspaceCapabilityStatusResponse ready() {
        return new WorkspaceCapabilityStatusResponse(true, WorkspaceCapabilityReadiness.READY,
                WorkspaceCapabilityPolicyState.ALLOWED, "policy", "ready", List.of());
    }
    private static WorkspaceCapabilitiesResponse snapshot(WorkspaceCapabilityStatusResponse files,
            WorkspaceCapabilityStatusResponse calendar) {
        return WorkspaceCapabilityTestInstallation.snapshot(ready(), ready(), files, calendar, ready(), ready());
    }
}

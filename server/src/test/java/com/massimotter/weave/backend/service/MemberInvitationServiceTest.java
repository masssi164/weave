package com.massimotter.weave.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.config.IdentityInvitationProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.identity.invitation.KeycloakIdentityAdminClient;
import com.massimotter.weave.backend.identity.invitation.KeycloakIdentityAdminClient.ProviderInvitation;
import com.massimotter.weave.backend.identity.invitation.ProvisioningIntent;
import com.massimotter.weave.backend.identity.invitation.ProvisioningIntentRepository;
import com.massimotter.weave.backend.identity.invitation.ProvisioningIntentStatus;
import com.massimotter.weave.backend.model.identity.MemberInvitationRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

class MemberInvitationServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-22T10:00:00Z");

    private ProvisioningIntentRepository intents;
    private KeycloakIdentityAdminClient keycloak;
    private AuditEventPublisher audit;
    private MemberInvitationService service;

    @BeforeEach
    void setUp() {
        intents = mock(ProvisioningIntentRepository.class);
        keycloak = mock(KeycloakIdentityAdminClient.class);
        when(keycloak.configuredOrganizationRef()).thenReturn("weave-dogfood");
        audit = mock(AuditEventPublisher.class);
        service = new MemberInvitationService(
                intents,
                keycloak,
                new IdentityInvitationProperties(),
                audit,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void persistsOnlyTheCanonicalRoleAndLeavesGroupMappingToTheIamAdapter() {
        when(intents.findPendingByEmail("weave-dogfood", "weave-dogfood", "member@example.invalid"))
                .thenReturn(List.of());
        when(intents.save(any(ProvisioningIntent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(keycloak.issue("weave-dogfood", "member@example.invalid", "Member Example"))
                .thenReturn(new ProviderInvitation(
                        "invitation-1",
                        "member@example.invalid",
                        "Member Example",
                        "pending",
                        NOW.plusSeconds(3600),
                        NOW));

        var response = service.create(
                "weave-dogfood",
                new MemberInvitationRequest(
                        "Member@Example.invalid",
                        "Member Example",
                        "member"),
                "invite-once",
                adminJwt());

        assertThat(response.requestedRole()).isEqualTo("member");
        ArgumentCaptor<ProvisioningIntent> saved = ArgumentCaptor.forClass(ProvisioningIntent.class);
        verify(intents, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(intent ->
                assertThat(intent.requestedRole()).isEqualTo("member"));
    }

    @Test
    void rejectsCrossOrganizationInvitationBeforeCreatingProviderOrWorkState() {
        assertThatThrownBy(() -> service.create(
                "org-1",
                new MemberInvitationRequest(
                        "member@example.invalid",
                        null,
                        "member"),
                "invite-escalation",
                adminJwt()))
                .isInstanceOfSatisfying(ApiErrorException.class, error -> {
                    assertThat(error.status().value()).isEqualTo(404);
                    assertThat(error.code()).isEqualTo("member-invitation-not-found");
                });

        verifyNoInteractions(intents, keycloak, audit);
    }

    @Test
    void reconcilesOneVerifiedAuthenticatedMemberAndRequiresTokenRefresh() {
        ProvisioningIntent pending = pendingIntent("member@example.invalid");
        when(intents.findPendingByEmail(
                        "weave-dogfood",
                        "weave-dogfood",
                        "member@example.invalid"))
                .thenReturn(List.of(pending));
        when(keycloak.isOrganizationMember("weave-dogfood", "member-1"))
                .thenReturn(true);
        when(intents.save(any(ProvisioningIntent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        boolean accessUpdated = service.reconcileAuthenticated(memberJwt(true));

        assertThat(accessUpdated).isTrue();
        verify(keycloak).applyOrganizationRole("weave-dogfood", "member-1", "member");
        ArgumentCaptor<ProvisioningIntent> saved =
                ArgumentCaptor.forClass(ProvisioningIntent.class);
        verify(intents).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(ProvisioningIntentStatus.APPLIED);
        assertThat(saved.getValue().appliedSubject()).isEqualTo("member-1");
    }

    @Test
    void leavesAnAuthenticatedMemberUnchangedWhenNoIntentExists() {
        when(intents.findPendingByEmail(
                        "weave-dogfood",
                        "weave-dogfood",
                        "member@example.invalid"))
                .thenReturn(List.of());

        assertThat(service.reconcileAuthenticated(memberJwt(true))).isFalse();

        verify(keycloak, never()).isOrganizationMember(any(), any());
        verify(keycloak, never()).applyOrganizationRole(any(), any(), any());
        verify(intents, never()).save(any());
    }

    @Test
    void rejectsUnverifiedEmailBeforeReadingProvisioningState() {
        assertThatThrownBy(() -> service.reconcileAuthenticated(memberJwt(false)))
                .isInstanceOfSatisfying(ApiErrorException.class, error -> {
                    assertThat(error.status()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(error.code()).isEqualTo("identity-session-email-unverified");
                });

        verifyNoInteractions(intents);
        verify(keycloak, never()).isOrganizationMember(any(), any());
    }

    @Test
    void rejectsAmbiguousPendingIntentsWithoutProviderMutation() {
        ProvisioningIntent pending = pendingIntent("member@example.invalid");
        when(intents.findPendingByEmail(
                        "weave-dogfood",
                        "weave-dogfood",
                        "member@example.invalid"))
                .thenReturn(List.of(pending, pending));

        assertThatThrownBy(() -> service.reconcileAuthenticated(memberJwt(true)))
                .isInstanceOfSatisfying(ApiErrorException.class, error -> {
                    assertThat(error.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(error.code())
                            .isEqualTo("identity-session-reconciliation-ambiguous");
                });

        verify(keycloak, never()).applyOrganizationRole(any(), any(), any());
        verify(intents, never()).save(any());
    }

    @Test
    void keepsPendingIntentRetryableWhenKeycloakReconciliationFails() {
        ProvisioningIntent pending = pendingIntent("member@example.invalid");
        when(intents.findPendingByEmail(
                        "weave-dogfood",
                        "weave-dogfood",
                        "member@example.invalid"))
                .thenReturn(List.of(pending));
        when(keycloak.isOrganizationMember("weave-dogfood", "member-1"))
                .thenReturn(true);
        org.mockito.Mockito.doThrow(new IllegalStateException("provider unavailable"))
                .when(keycloak)
                .applyOrganizationRole("weave-dogfood", "member-1", "member");

        assertThatThrownBy(() -> service.reconcileAuthenticated(memberJwt(true)))
                .isInstanceOfSatisfying(ApiErrorException.class, error -> {
                    assertThat(error.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(error.code()).isEqualTo("identity-session-provider-unavailable");
                });

        verify(intents, never()).save(any());
    }

    private ProvisioningIntent pendingIntent(String email) {
        return new ProvisioningIntent(
                UUID.fromString("6637d85d-09bf-47c1-b4a8-8b46cc0fcc19"),
                "weave-dogfood",
                "weave-dogfood",
                email,
                "0".repeat(64),
                "member",
                "invitation-1",
                "https://auth.example.invalid/realms/weave",
                "admin-1",
                "invite-once",
                ProvisioningIntentStatus.PENDING,
                null,
                null,
                NOW.plusSeconds(3600),
                NOW.minusSeconds(60),
                NOW.minusSeconds(60));
    }

    private Jwt memberJwt(boolean emailVerified) {
        return Jwt.withTokenValue("member-token")
                .header("alg", "none")
                .subject("member-1")
                .issuer("https://auth.example.invalid/realms/weave")
                .claim("weave_tenant", "weave-dogfood")
                .claim("email", "Member@Example.invalid")
                .claim("email_verified", emailVerified)
                .claim(
                        "resource_access",
                        Map.of("weave-app", Map.of("roles", List.of())))
                .build();
    }

    private Jwt adminJwt() {
        return Jwt.withTokenValue("admin-token")
                .header("alg", "none")
                .subject("admin-1")
                .issuer("https://auth.example.invalid/realms/weave")
                .claim("weave_tenant", "weave-dogfood")
                .claim("resource_access", Map.of("weave-app", Map.of("roles", List.of("admin"))))
                .build();
    }
}

package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.support.HumanJwtTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.InMemoryAuditEventPublisher;
import com.massimotter.weave.backend.config.IdentityInvitationProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.identity.invitation.InMemoryProvisioningIntentRepository;
import com.massimotter.weave.backend.identity.invitation.KeycloakIdentityAdminClient;
import com.massimotter.weave.backend.identity.IdentityOpaqueReferenceCodec;
import com.massimotter.weave.backend.identity.invitation.KeycloakIdentityAdminClient.KeycloakAdminException;
import com.massimotter.weave.backend.identity.invitation.KeycloakIdentityAdminClient.ProviderInvitation;
import com.massimotter.weave.backend.identity.invitation.ProvisioningIntent;
import com.massimotter.weave.backend.identity.invitation.ProvisioningIntentStatus;
import com.massimotter.weave.backend.model.identity.BootstrapOwnerInvitationRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class MemberInvitationServiceTest {
  private static final Instant NOW = Instant.parse("2026-07-26T10:00:00Z");
  private static final String TENANT_ID = "tenant-default";
  private static final String ORGANIZATION_ID = "organization-1";
  private static final String EMAIL = "owner@example.org";
  private static final String IDEMPOTENCY_KEY = "bootstrap-owner-run-0001";

  @Mock KeycloakIdentityAdminClient keycloak;
  @Mock IdentityOpaqueReferenceCodec references;

  private InMemoryProvisioningIntentRepository intents;
  private InMemoryAuditEventPublisher audit;
  private MemberInvitationService service;

  @BeforeEach
  void setUp() {
    IdentityInvitationProperties properties = new IdentityInvitationProperties();
    properties.bootstrapOwner().setTenantId(TENANT_ID);
    intents = new InMemoryProvisioningIntentRepository();
    audit = new InMemoryAuditEventPublisher();
    lenient()
        .when(references.invitation(anyString(), anyString()))
        .thenAnswer(invocation -> "inv_" + invocation.getArgument(1, String.class));
    service =
        new MemberInvitationService(
            intents,
            keycloak,
            properties,
            references,
            audit,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void createsExactlyOneOwnerInvitationWhenTheHumanRealmIsEmpty() {
    ProviderInvitation providerInvitation = providerInvitation();
    when(keycloak.hasHumanUsers()).thenReturn(false);
    when(keycloak.configuredOrganizationId()).thenReturn(ORGANIZATION_ID);
    when(keycloak.invitationsForEmail(ORGANIZATION_ID, EMAIL)).thenReturn(List.of());
    when(keycloak.issue(ORGANIZATION_ID, EMAIL, "Weave Owner"))
        .thenReturn(providerInvitation);

    var response =
        service.bootstrapOwner(
            new BootstrapOwnerInvitationRequest(" Owner@Example.org ", "Weave Owner"),
            IDEMPOTENCY_KEY);

    assertThat(response.invitationHandle()).isEqualTo("inv_invitation-1");
    assertThat(response.organizationId()).isEqualTo(TENANT_ID);
    assertThat(response.email()).isEqualTo(EMAIL);
    assertThat(response.requestedRole()).isEqualTo("owner");
    assertThat(response.provisioningStatus()).isEqualTo("pending");

    List<ProvisioningIntent> saved =
        intents.findPendingByEmail(TENANT_ID, ORGANIZATION_ID, EMAIL);
    assertThat(saved).singleElement();
    assertThat(saved.getFirst().invitedByIssuer()).isEqualTo("urn:weave:identity-bootstrap");
    assertThat(saved.getFirst().invitedBySubject()).isEqualTo("bootstrap-owner-invitation");
    assertThat(saved.getFirst().auditCorrelation()).isEqualTo(IDEMPOTENCY_KEY);
    assertThat(audit.events()).hasSize(1);
  }

  @Test
  void returnsTheSameUnambiguousPendingOwnerInvitationOnRetry() {
    ProviderInvitation providerInvitation = providerInvitation();
    when(keycloak.hasHumanUsers()).thenReturn(false);
    when(keycloak.configuredOrganizationId()).thenReturn(ORGANIZATION_ID);
    when(keycloak.invitationsForEmail(ORGANIZATION_ID, EMAIL))
        .thenReturn(List.of(), List.of(providerInvitation));
    when(keycloak.issue(ORGANIZATION_ID, EMAIL, "Weave Owner"))
        .thenReturn(providerInvitation);

    var first =
        service.bootstrapOwner(
            new BootstrapOwnerInvitationRequest(EMAIL, "Weave Owner"), IDEMPOTENCY_KEY);
    var replay =
        service.bootstrapOwner(
            new BootstrapOwnerInvitationRequest(EMAIL, "Weave Owner"), IDEMPOTENCY_KEY);

    assertThat(replay).isEqualTo(first);
    verify(keycloak).issue(ORGANIZATION_ID, EMAIL, "Weave Owner");
    assertThat(intents.findPendingByEmail(TENANT_ID, ORGANIZATION_ID, EMAIL)).hasSize(1);
    assertThat(audit.events()).hasSize(1);
  }

  @Test
  void rejectsBootstrapWhenAnyHumanIdentityAlreadyExists() {
    when(keycloak.hasHumanUsers()).thenReturn(true);

    assertThatThrownBy(
            () ->
                service.bootstrapOwner(
                    new BootstrapOwnerInvitationRequest(EMAIL, "Weave Owner"),
                    IDEMPOTENCY_KEY))
        .isInstanceOfSatisfying(
            ApiErrorException.class,
            error -> {
              assertThat(error.status()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(error.code()).isEqualTo("owner-bootstrap-not-empty");
              assertThat(error.getMessage()).doesNotContain(EMAIL);
            });

    verify(keycloak, never()).issue(anyString(), anyString(), anyString());
    assertThat(intents.findPendingByEmail(TENANT_ID, ORGANIZATION_ID, EMAIL)).isEmpty();
    assertThat(audit.events()).isEmpty();
  }

  @Test
  void rejectsAnUncorrelatedProviderInvitationInsteadOfCreatingADuplicate() {
    when(keycloak.hasHumanUsers()).thenReturn(false);
    when(keycloak.configuredOrganizationId()).thenReturn(ORGANIZATION_ID);
    when(keycloak.invitationsForEmail(ORGANIZATION_ID, EMAIL))
        .thenReturn(List.of(providerInvitation()));

    assertThatThrownBy(
            () ->
                service.bootstrapOwner(
                    new BootstrapOwnerInvitationRequest(EMAIL, "Weave Owner"),
                    IDEMPOTENCY_KEY))
        .isInstanceOfSatisfying(
            ApiErrorException.class,
            error -> assertThat(error.code()).isEqualTo("owner-bootstrap-not-empty"));

    verify(keycloak, never()).issue(anyString(), anyString(), anyString());
  }

  @Test
  void correlatesOneProviderInvitationAfterLocalPostProcessingWasInterrupted() {
    ProviderInvitation providerInvitation = providerInvitation();
    ProvisioningIntent pending =
        pendingIntent(null, ProvisioningIntentStatus.PENDING);
    intents.save(pending);
    when(keycloak.hasHumanUsers()).thenReturn(false);
    when(keycloak.configuredOrganizationId()).thenReturn(ORGANIZATION_ID);
    when(keycloak.invitationsForEmail(ORGANIZATION_ID, EMAIL))
        .thenReturn(List.of(providerInvitation));

    var recovered =
        service.bootstrapOwner(
            new BootstrapOwnerInvitationRequest(EMAIL, "Weave Owner"),
            IDEMPOTENCY_KEY);

    assertThat(recovered.invitationHandle()).isEqualTo("inv_invitation-1");
    assertThat(
            intents
                .findPendingByEmail(TENANT_ID, ORGANIZATION_ID, EMAIL)
                .getFirst()
                .providerInvitationId())
        .isEqualTo("invitation-1");
    verify(keycloak, never()).issue(anyString(), anyString(), anyString());
  }

  @Test
  void doesNotMaskServerOwnedHandleFailureAsAKeycloakProviderOutage() {
    ProviderInvitation providerInvitation = providerInvitation();
    when(keycloak.hasHumanUsers()).thenReturn(false);
    when(keycloak.configuredOrganizationId()).thenReturn(ORGANIZATION_ID);
    when(keycloak.invitationsForEmail(ORGANIZATION_ID, EMAIL)).thenReturn(List.of());
    when(keycloak.issue(ORGANIZATION_ID, EMAIL, "Weave Owner"))
        .thenReturn(providerInvitation);
    when(references.invitation(ORGANIZATION_ID, "invitation-1"))
        .thenThrow(new IllegalStateException("local reference failure"));

    assertThatThrownBy(
            () ->
                service.bootstrapOwner(
                    new BootstrapOwnerInvitationRequest(EMAIL, "Weave Owner"),
                    IDEMPOTENCY_KEY))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("local reference failure");

    assertThat(
            intents
                .findPendingByEmail(TENANT_ID, ORGANIZATION_ID, EMAIL)
                .getFirst()
                .providerInvitationId())
        .isEqualTo("invitation-1");
  }

  @Test
  void mapsOnlyTheKeycloakMutationFailureToTheStableProviderError() {
    when(keycloak.hasHumanUsers()).thenReturn(false);
    when(keycloak.configuredOrganizationId()).thenReturn(ORGANIZATION_ID);
    when(keycloak.invitationsForEmail(ORGANIZATION_ID, EMAIL)).thenReturn(List.of());
    when(keycloak.issue(ORGANIZATION_ID, EMAIL, "Weave Owner"))
        .thenThrow(new KeycloakAdminException(503, "provider unavailable", "invitation-create"));

    assertThatThrownBy(
            () ->
                service.bootstrapOwner(
                    new BootstrapOwnerInvitationRequest(EMAIL, "Weave Owner"),
                    IDEMPOTENCY_KEY))
        .isInstanceOfSatisfying(
            ApiErrorException.class,
            error -> {
              assertThat(error.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
              assertThat(error.code()).isEqualTo("member-invitation-provider-unavailable");
            });
  }

  @Test
  void reconcilesOneVerifiedPendingIntentAgainstLiveOrganizationMembership() {
    intents.save(pendingIntent("invitation-1", ProvisioningIntentStatus.PENDING));
    when(keycloak.configuredOrganizationId()).thenReturn(ORGANIZATION_ID);
    when(keycloak.isOrganizationMember(ORGANIZATION_ID, "owner-subject"))
        .thenReturn(true);

    assertThat(service.reconcileAuthenticated(authenticatedOwner())).isTrue();

    verify(keycloak).applyRole("owner-subject", "owner");
    assertThat(intents.findPendingByEmail(TENANT_ID, ORGANIZATION_ID, EMAIL))
        .isEmpty();
  }

  @Test
  void mapsIdentitySessionProviderFailureToTheStableBadGatewayContract() {
    intents.save(pendingIntent("invitation-1", ProvisioningIntentStatus.PENDING));
    when(keycloak.configuredOrganizationId()).thenReturn(ORGANIZATION_ID);
    when(keycloak.isOrganizationMember(ORGANIZATION_ID, "owner-subject"))
        .thenReturn(true);
    doThrow(new KeycloakAdminException(503, "provider unavailable", "organization-group-add"))
        .when(keycloak)
        .applyRole("owner-subject", "owner");

    assertThatThrownBy(() -> service.reconcileAuthenticated(authenticatedOwner()))
        .isInstanceOfSatisfying(
            ApiErrorException.class,
            error -> {
              assertThat(error.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
              assertThat(error.code())
                  .isEqualTo("identity-session-provider-unavailable");
              assertThat(error.getMessage()).doesNotContain("provider unavailable");
            });

    assertThat(intents.findPendingByEmail(TENANT_ID, ORGANIZATION_ID, EMAIL)).isEmpty();
    assertThat(audit.events()).isEmpty();
  }

  @Test
  void scopesCreationAndAcceptanceAuditIdempotencyKeysByLifecycleAction() {
    ProviderInvitation providerInvitation = providerInvitation();
    when(keycloak.hasHumanUsers()).thenReturn(false);
    when(keycloak.configuredOrganizationId()).thenReturn(ORGANIZATION_ID);
    when(keycloak.invitationsForEmail(ORGANIZATION_ID, EMAIL)).thenReturn(List.of());
    when(keycloak.issue(ORGANIZATION_ID, EMAIL, "Weave Owner"))
        .thenReturn(providerInvitation);
    when(keycloak.isOrganizationMember(ORGANIZATION_ID, "owner-subject"))
        .thenReturn(true);

    service.bootstrapOwner(
        new BootstrapOwnerInvitationRequest(EMAIL, "Weave Owner"), IDEMPOTENCY_KEY);
    assertThat(service.reconcileAuthenticated(authenticatedOwner())).isTrue();

    assertThat(audit.events())
        .extracting(event -> event.idempotencyKey())
        .containsExactly(
            IDEMPOTENCY_KEY + ":" + AuditAction.MEMBER_INVITATION_CREATED.wireName(),
            IDEMPOTENCY_KEY + ":" + AuditAction.MEMBER_INVITATION_ACCEPTED.wireName())
        .doesNotHaveDuplicates();
  }

  @Test
  void rejectsAmbiguousPendingIntentsInsteadOfSelectingOne() {
    intents.save(pendingIntent("invitation-1", ProvisioningIntentStatus.PENDING));
    intents.save(pendingIntent("invitation-2", ProvisioningIntentStatus.PENDING));
    when(keycloak.configuredOrganizationId()).thenReturn(ORGANIZATION_ID);

    assertThatThrownBy(() -> service.reconcileAuthenticated(authenticatedOwner()))
        .isInstanceOfSatisfying(
            ApiErrorException.class,
            error -> {
              assertThat(error.status()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(error.code())
                  .isEqualTo("identity-session-reconciliation-ambiguous");
            });

    verify(keycloak, never()).applyRole(anyString(), anyString());
  }

  private Jwt authenticatedOwner() {
    return Jwt.withTokenValue("owner-token")
        .header("alg", "none")
        .issuer("https://auth.example.test/realms/weave")
        .subject("owner-subject")
        .claim("email", EMAIL)
        .claim("email_verified", true)
        .claim("organization", HumanJwtTestSupport.organizationWithRoles(List.of()))
        .build();
  }

  private ProvisioningIntent pendingIntent(
      String providerInvitationId, ProvisioningIntentStatus status) {
    return new ProvisioningIntent(
        UUID.randomUUID(),
        TENANT_ID,
        ORGANIZATION_ID,
        EMAIL,
        "email-sha256",
        "owner",
        providerInvitationId,
        "urn:weave:identity-bootstrap",
        "bootstrap-owner-invitation",
        IDEMPOTENCY_KEY,
        status,
        null,
        null,
        NOW.plusSeconds(86_400),
        NOW,
        NOW);
  }

  private ProviderInvitation providerInvitation() {
    return new ProviderInvitation(
        "invitation-1",
        EMAIL,
        "Weave Owner",
        "pending",
        NOW.plusSeconds(86_400),
        NOW);
  }
}

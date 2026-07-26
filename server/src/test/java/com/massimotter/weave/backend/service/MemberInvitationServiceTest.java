package com.massimotter.weave.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.massimotter.weave.backend.audit.InMemoryAuditEventPublisher;
import com.massimotter.weave.backend.config.IdentityInvitationProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.identity.invitation.InMemoryProvisioningIntentRepository;
import com.massimotter.weave.backend.identity.invitation.KeycloakIdentityAdminClient;
import com.massimotter.weave.backend.identity.IdentityOpaqueReferenceCodec;
import com.massimotter.weave.backend.identity.invitation.KeycloakIdentityAdminClient.ProviderInvitation;
import com.massimotter.weave.backend.identity.invitation.ProvisioningIntent;
import com.massimotter.weave.backend.model.identity.BootstrapOwnerInvitationRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class MemberInvitationServiceTest {
  private static final Instant NOW = Instant.parse("2026-07-26T10:00:00Z");
  private static final String TENANT_ID = "tenant-dogfood";
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
    assertThat(response.capabilities()).isEmpty();
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

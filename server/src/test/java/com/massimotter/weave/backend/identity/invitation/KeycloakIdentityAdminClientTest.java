package com.massimotter.weave.backend.identity.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.config.IdentityInvitationProperties;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KeycloakIdentityAdminClientTest {
  private MockRestServiceServer provider;
  private KeycloakIdentityAdminClient client;

  @BeforeEach
  void setUp() {
    IdentityInvitationProperties properties = new IdentityInvitationProperties();
    properties.keycloak().setBaseUrl(URI.create("https://identity.internal"));
    properties.keycloak().setRealm("weave");
    properties.keycloak().setOrganizationId("organization-1");

    RestClient.Builder builder = RestClient.builder().baseUrl(properties.keycloak().baseUrl());
    provider = MockRestServiceServer.bindTo(builder).build();
    client = new KeycloakIdentityAdminClient(properties, new ObjectMapper(), builder.build());
  }

  @Test
  void resolvesOrganizationByStableAliasOnce() {
    IdentityInvitationProperties properties = new IdentityInvitationProperties();
    properties.keycloak().setBaseUrl(URI.create("https://identity.internal"));
    properties.keycloak().setRealm("weave");
    properties.keycloak().setOrganizationId("");
    RestClient.Builder builder = RestClient.builder().baseUrl(properties.keycloak().baseUrl());
    provider = MockRestServiceServer.bindTo(builder).build();
    client = new KeycloakIdentityAdminClient(properties, new ObjectMapper(), builder.build());

    provider
        .expect(
            once(),
            requestTo(
                "https://identity.internal/admin/realms/weave"
                    + "/organizations?first=0&max=100&briefRepresentation=true"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                """
                [{"id":"organization-1","alias":"weave"}]
                """,
                MediaType.APPLICATION_JSON));

    assertThat(client.configuredOrganizationId()).isEqualTo("organization-1");
    assertThat(client.configuredOrganizationId()).isEqualTo("organization-1");
    provider.verify();
  }

  @Test
  void assignsRolesOnlyThroughNativeOrganizationGroups() {
    provider
        .expect(
            requestTo(
                "https://identity.internal/admin/realms/weave"
                    + "/organizations/organization-1/members/subject-1/groups"
                    + "?briefRepresentation=true"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
    provider
        .expect(
            requestTo(
                "https://identity.internal/admin/realms/weave"
                    + "/organizations/organization-1/groups?search=members&exact=true"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                """
                [{"id":"group-uuid","name":"members","path":"/members"}]
                """,
                MediaType.APPLICATION_JSON));
    provider
        .expect(
            requestTo(
                "https://identity.internal/admin/realms/weave"
                    + "/organizations/organization-1/groups/group-uuid/members/subject-1"))
        .andExpect(method(HttpMethod.PUT))
        .andRespond(withStatus(HttpStatus.NO_CONTENT));

    client.applyRole("subject-1", "member");

    provider.verify();
  }

  @Test
  void treatsAnAlreadyPresentOrganizationGroupMembershipAsIdempotentSuccess() {
    provider
        .expect(
            requestTo(
                "https://identity.internal/admin/realms/weave"
                    + "/organizations/organization-1/members/subject-1/groups"
                    + "?briefRepresentation=true"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
    provider
        .expect(
            requestTo(
                "https://identity.internal/admin/realms/weave"
                    + "/organizations/organization-1/groups?search=members&exact=true"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                """
                [{"id":"group-uuid","name":"members","path":"/members"}]
                """,
                MediaType.APPLICATION_JSON));
    provider
        .expect(
            requestTo(
                "https://identity.internal/admin/realms/weave"
                    + "/organizations/organization-1/groups/group-uuid/members/subject-1"))
        .andExpect(method(HttpMethod.PUT))
        .andRespond(withStatus(HttpStatus.CONFLICT));

    client.applyRole("subject-1", "member");

    provider.verify();
  }

  @Test
  void rejectsAConflictWhenRemovingAnOrganizationGroupMembership() {
    provider
        .expect(
            requestTo(
                "https://identity.internal/admin/realms/weave"
                    + "/organizations/organization-1/members/subject-1/groups"
                    + "?briefRepresentation=true"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                """
                [{"id":"member-group","name":"members","path":"/members"},
                 {"id":"owner-group","name":"owners","path":"/owners"}]
                """,
                MediaType.APPLICATION_JSON));
    provider
        .expect(
            requestTo(
                "https://identity.internal/admin/realms/weave"
                    + "/organizations/organization-1/groups?search=owners&exact=true"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                """
                [{"id":"owner-group","name":"owners","path":"/owners"}]
                """,
                MediaType.APPLICATION_JSON));
    provider
        .expect(
            requestTo(
                "https://identity.internal/admin/realms/weave"
                    + "/organizations/organization-1/groups/owner-group/members/subject-1"))
        .andExpect(method(HttpMethod.DELETE))
        .andRespond(withStatus(HttpStatus.CONFLICT));

    assertThatThrownBy(() -> client.applyRole("subject-1", "member"))
        .isInstanceOf(KeycloakIdentityAdminClient.KeycloakAdminException.class)
        .hasMessageContaining("sanitized status 409");

    provider.verify();
  }

  @Test
  void rejectsAProviderFailureWhenAddingAnOrganizationGroupMembership() {
    provider
        .expect(
            requestTo(
                "https://identity.internal/admin/realms/weave"
                    + "/organizations/organization-1/members/subject-1/groups"
                    + "?briefRepresentation=true"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
    provider
        .expect(
            requestTo(
                "https://identity.internal/admin/realms/weave"
                    + "/organizations/organization-1/groups?search=members&exact=true"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                """
                [{"id":"group-uuid","name":"members","path":"/members"}]
                """,
                MediaType.APPLICATION_JSON));
    provider
        .expect(
            requestTo(
                "https://identity.internal/admin/realms/weave"
                    + "/organizations/organization-1/groups/group-uuid/members/subject-1"))
        .andExpect(method(HttpMethod.PUT))
        .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

    assertThatThrownBy(() -> client.applyRole("subject-1", "member"))
        .isInstanceOf(KeycloakIdentityAdminClient.KeycloakAdminException.class)
        .hasMessageContaining("sanitized status 503");

    provider.verify();
  }

  @Test
  void checksBootstrapEmptinessThroughTheAuthorizedOrganizationProjection() {
    provider
        .expect(
            requestTo(
                "https://identity.internal/admin/realms/weave"
                    + "/organizations/organization-1/members"
                    + "?first=0&max=100&briefRepresentation=true"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                """
                [
                  {
                    "id":"service-account-1",
                    "username":"service-account-weave-identity-admin",
                    "serviceAccountClientId":"weave-identity-admin"
                  },
                  {
                    "id":"service-account-2",
                    "username":"service-account-weave-mcp-server"
                  }
                ]
                """,
                MediaType.APPLICATION_JSON));

    assertThat(client.hasHumanUsers()).isFalse();
    provider.verify();
  }

  @Test
  void detectsAHumanInTheOrganizationProjection() {
    provider
        .expect(
            requestTo(
                "https://identity.internal/admin/realms/weave"
                    + "/organizations/organization-1/members"
                    + "?first=0&max=100&briefRepresentation=true"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                """
                [
                  {
                    "id":"service-account-1",
                    "username":"service-account-weave-identity-admin",
                    "serviceAccountClientId":"weave-identity-admin"
                  },
                  {
                    "id":"person-1",
                    "username":"owner@example.org"
                  }
                ]
                """,
                MediaType.APPLICATION_JSON));

    assertThat(client.hasHumanUsers()).isTrue();
    provider.verify();
  }

  @Test
  void issuesAndCorrelatesTheOfficialKeycloakOrganizationInvitationProjection() {
    String inventory =
        "https://identity.internal/admin/realms/weave"
            + "/organizations/organization-1/invitations?email=owner@example.org";
    provider
        .expect(once(), requestTo(inventory))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
    provider
        .expect(
            once(),
            requestTo(
                "https://identity.internal/admin/realms/weave"
                    + "/organizations/organization-1/members/invite-user"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().contentType(MediaType.APPLICATION_FORM_URLENCODED))
        .andExpect(
            content()
                .string(
                    "email=owner%40example.org&firstName=Weave&lastName=Owner"))
        .andRespond(withStatus(HttpStatus.NO_CONTENT));
    provider
        .expect(once(), requestTo(inventory))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                """
                [{
                  "id":"invitation-1",
                  "organizationId":"organization-1",
                  "email":"owner@example.org",
                  "firstName":"Weave",
                  "lastName":"Owner",
                  "sentDate":1785081600,
                  "expiresAt":1785168000,
                  "status":"PENDING",
                  "inviteLink":"must-not-cross-the-adapter"
                }]
                """,
                MediaType.APPLICATION_JSON));

    KeycloakIdentityAdminClient.ProviderInvitation invitation =
        client.issue("organization-1", "owner@example.org", "Weave Owner");

    assertThat(invitation.providerInvitationId()).isEqualTo("invitation-1");
    assertThat(invitation.email()).isEqualTo("owner@example.org");
    assertThat(invitation.displayName()).isEqualTo("Weave Owner");
    assertThat(invitation.lifecycleStatus()).isEqualTo("pending");
    assertThat(invitation.createdAt()).isNotNull();
    assertThat(invitation.expiresAt()).isAfter(invitation.createdAt());
    provider.verify();
  }

  @Test
  void mapsProviderFailureToSanitizedStatus() {
    provider
        .expect(
            requestTo(
                "https://identity.internal/admin/realms/weave"
                    + "/organizations/organization-1/invitations"))
        .andRespond(
            withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("provider-secret-must-not-leak"));

    assertThatThrownBy(() -> client.list("organization-1"))
        .isInstanceOf(KeycloakIdentityAdminClient.KeycloakAdminException.class)
        .hasMessageContaining("sanitized status 503")
        .hasMessageNotContaining("provider-secret");
    provider.verify();
  }
}

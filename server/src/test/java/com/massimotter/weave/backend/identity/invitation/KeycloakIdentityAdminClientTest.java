package com.massimotter.weave.backend.identity.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
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
                    + "/organizations?search=weave&exact=true"))
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
  void treatsOnlyServiceAccountsAsAnEmptyHumanRealm() {
    provider
        .expect(
            requestTo(
                "https://identity.internal/admin/realms/weave"
                    + "/users?first=0&max=100&briefRepresentation=true"))
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
  void detectsAHumanAmongServiceAccounts() {
    provider
        .expect(
            requestTo(
                "https://identity.internal/admin/realms/weave"
                    + "/users?first=0&max=100&briefRepresentation=true"))
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

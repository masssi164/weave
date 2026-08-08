package com.massimotter.weave.backend.identity.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class KeycloakRealmMigrationOperationPolicyTest {
  @Test
  void acceptsOnlyTheReviewedFgapAndEphemeralAuthorityOperations() {
    List<Operation> allowed =
        List.of(
            operation(HttpMethod.GET, "/admin/realms/weave"),
            operation(
                HttpMethod.GET,
                "/admin/realms/master/clients?clientId=weave-realm-migration-bootstrap"),
            operation(
                HttpMethod.GET,
                "/admin/realms/weave/clients/client-id/service-account-user"),
            operation(
                HttpMethod.GET, "/admin/realms/weave/users/service-account-id/role-mappings"),
            operation(
                HttpMethod.GET,
                "/admin/realms/weave/organizations?first=0&max=100&briefRepresentation=true"),
            operation(
                HttpMethod.GET,
                "/admin/realms/weave/clients/admin-id/authz/resource-server/policy/user?first=0&max=100"),
            operation(
                HttpMethod.POST,
                "/admin/realms/weave/clients/admin-id/authz/resource-server/policy/user"),
            operation(
                HttpMethod.GET,
                "/admin/realms/weave/clients/admin-id/authz/resource-server/policy/user/policy-id/dependentPolicies"),
            operation(
                HttpMethod.PUT,
                "/admin/realms/weave/clients/admin-id/authz/resource-server/permission/scope/permission-id"),
            operation(
                HttpMethod.GET,
                "/admin/realms/weave/clients/admin-id/authz/resource-server/permission/scope/permission-id/associatedPolicies"),
            operation(HttpMethod.DELETE, "/admin/realms/master/clients/migration-id"));

    allowed.forEach(
        operation ->
            assertThat(
                    KeycloakRealmMigrationOperationPolicy.requireAllowed(
                        operation.method(), operation.path()))
                .isNotBlank());
  }

  @Test
  void rejectsGeneralRealmUserCredentialAndBroadClientOperations() {
    List<Operation> forbidden =
        List.of(
            operation(HttpMethod.PUT, "/admin/realms/weave"),
            operation(HttpMethod.POST, "/admin/realms/weave/users"),
            operation(
                HttpMethod.PUT, "/admin/realms/weave/users/person/reset-password"),
            operation(HttpMethod.GET, "/admin/realms/weave/clients"),
            operation(
                HttpMethod.GET,
                "/admin/realms/weave/clients?clientId=unreviewed-client"),
            operation(HttpMethod.DELETE, "/admin/realms/weave/organizations/org-id"),
            operation(
                HttpMethod.DELETE,
                "/admin/realms/weave/clients/admin-id/authz/resource-server/policy/user/policy-id"));

    forbidden.forEach(
        operation ->
            assertThatThrownBy(
                    () ->
                        KeycloakRealmMigrationOperationPolicy.requireAllowed(
                            operation.method(), operation.path()))
                .isInstanceOf(KeycloakRealmMigrationException.class)
                .hasMessage("admin-rest-operation-forbidden"));
  }

  private static Operation operation(HttpMethod method, String path) {
    return new Operation(method, path);
  }

  private record Operation(HttpMethod method, String path) {}
}

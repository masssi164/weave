package com.massimotter.weave.backend.identity.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class KeycloakIdentityAdminOperationPolicyTest {
  @Test
  void acceptsOnlyReviewedIdentityLifecycleOperations() {
    List<AllowedOperation> operations =
        List.of(
            allowed(
                HttpMethod.GET,
                "/admin/realms/weave/organizations?first=0&max=100&briefRepresentation=true",
                "organization-inventory"),
            allowed(
                HttpMethod.GET,
                "/admin/realms/weave/users?first=0&max=1&briefRepresentation=true",
                "human-user-inventory"),
            allowed(
                HttpMethod.GET,
                "/admin/realms/weave/organizations/org-1/members"
                    + "?first=0&max=100&briefRepresentation=false",
                "member-inventory"),
            allowed(
                HttpMethod.POST,
                "/admin/realms/weave/organizations/org-1/members/invite-user",
                "invitation-create"),
            allowed(
                HttpMethod.GET,
                "/admin/realms/weave/organizations/org-1/invitations"
                    + "?email=member%40example.invalid",
                "invitation-inventory"),
            allowed(
                HttpMethod.POST,
                "/admin/realms/weave/organizations/org-1/invitations/invitation-1/resend",
                "invitation-lifecycle"),
            allowed(
                HttpMethod.DELETE,
                "/admin/realms/weave/organizations/org-1/invitations/invitation-1",
                "invitation-lifecycle"),
            allowed(
                HttpMethod.GET,
                "/admin/realms/weave/organizations/org-1/members/subject-1/groups"
                    + "?briefRepresentation=true",
                "organization-member-groups"),
            allowed(
                HttpMethod.DELETE,
                "/admin/realms/weave/organizations/org-1/members/subject-1",
                "organization-member"),
            allowed(
                HttpMethod.GET,
                "/admin/realms/weave/organizations/org-1/groups?search=members&exact=true",
                "organization-group"),
            allowed(
                HttpMethod.PUT,
                "/admin/realms/weave/organizations/org-1/groups/group-1/members/subject-1",
                "organization-group-membership"),
            allowed(
                HttpMethod.PUT,
                "/admin/realms/weave/users/subject-1",
                "member-projection"),
            allowed(
                HttpMethod.POST,
                "/admin/realms/weave/users/subject-1/logout",
                "member-session-revocation"));

    operations.forEach(
        operation ->
            assertThat(
                    KeycloakIdentityAdminOperationPolicy.requireAllowed(
                        operation.method(), operation.uri()))
                .isEqualTo(operation.operationCode()));
  }

  @Test
  void rejectsCredentialImpersonationAndGeneralSessionRoutes() {
    assertForbidden(
        List.of(
            allowed(HttpMethod.PUT, "/admin/realms/weave/users/subject-1/reset-password", "forbidden"),
            allowed(HttpMethod.PUT, "/admin/realms/weave/users/subject-1/execute-actions-email", "forbidden"),
            allowed(HttpMethod.POST, "/admin/realms/weave/users/subject-1/impersonation", "forbidden"),
            allowed(HttpMethod.GET, "/admin/realms/weave/users/subject-1/sessions", "forbidden"),
            allowed(HttpMethod.DELETE, "/admin/realms/weave/sessions/session-1", "forbidden"),
            allowed(HttpMethod.DELETE, "/admin/realms/weave/users/subject-1/logout", "forbidden")));
  }

  @Test
  void rejectsHumanUserInventoryMutationAndQueryDrift() {
    assertForbidden(
        List.of(
            allowed(HttpMethod.POST, "/admin/realms/weave/users?first=0&max=1&briefRepresentation=true", "forbidden"),
            allowed(HttpMethod.DELETE, "/admin/realms/weave/users?first=0&max=1&briefRepresentation=true", "forbidden"),
            allowed(HttpMethod.GET, "/admin/realms/weave/users?first=0&max=1&briefRepresentation=false", "forbidden"),
            allowed(HttpMethod.GET, "/admin/realms/weave/users?max=1&first=0&briefRepresentation=true", "forbidden"),
            allowed(HttpMethod.GET, "/admin/realms/weave/users?first=1&max=1&briefRepresentation=true", "forbidden"),
            allowed(HttpMethod.GET, "/admin/realms/weave/users?first=0&max=100&briefRepresentation=true", "forbidden"),
            allowed(HttpMethod.GET, "/admin/realms/weave/users?first=0&max=1&briefRepresentation=true&search=owner", "forbidden"),
            allowed(HttpMethod.PUT, "/admin/realms/weave/users/subject-1/execute-actions-email", "forbidden"),
            allowed(HttpMethod.DELETE, "/admin/realms/weave/users/subject-1/credentials/credential-1", "forbidden")));
  }

  @Test
  void rejectsStaticRealmStructureMutationFromPermanentServerAuthority() {
    assertForbidden(
        List.of(
            allowed(HttpMethod.PUT, "/admin/realms/weave", "realm-update"),
            allowed(HttpMethod.POST, "/admin/realms/weave/clients", "client-create"),
            allowed(HttpMethod.PUT, "/admin/realms/weave/clients/client-1", "client-update"),
            allowed(HttpMethod.POST, "/admin/realms/weave/client-scopes", "client-scope-create"),
            allowed(HttpMethod.POST, "/admin/realms/weave/roles", "realm-role-create"),
            allowed(HttpMethod.PUT, "/admin/realms/weave/roles/owner", "realm-role-update"),
            allowed(HttpMethod.POST, "/admin/realms/weave/groups", "realm-group-create"),
            allowed(HttpMethod.POST, "/admin/realms/weave/organizations", "organization-create"),
            allowed(HttpMethod.PUT, "/admin/realms/weave/organizations/org-1", "organization-update"),
            allowed(HttpMethod.POST, "/admin/realms/weave/authentication/flows", "auth-flow-create"),
            allowed(HttpMethod.POST, "/admin/realms/weave/components", "component-create")));
  }

  private static void assertForbidden(List<AllowedOperation> forbidden) {
    forbidden.forEach(
        operation ->
            assertThatThrownBy(
                    () ->
                        KeycloakIdentityAdminOperationPolicy.requireAllowed(
                            operation.method(), operation.uri()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("guarded boundary"));
  }

  private static AllowedOperation allowed(HttpMethod method, String uri, String operationCode) {
    return new AllowedOperation(method, uri, operationCode);
  }

  private record AllowedOperation(HttpMethod method, String uri, String operationCode) {}
}

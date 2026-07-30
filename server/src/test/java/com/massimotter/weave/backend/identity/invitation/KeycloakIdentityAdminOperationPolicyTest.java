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
    List<AllowedOperation> forbidden =
        List.of(
            allowed(
                HttpMethod.PUT,
                "/admin/realms/weave/users/subject-1/reset-password",
                "forbidden"),
            allowed(
                HttpMethod.PUT,
                "/admin/realms/weave/users/subject-1/execute-actions-email",
                "forbidden"),
            allowed(
                HttpMethod.POST,
                "/admin/realms/weave/users/subject-1/impersonation",
                "forbidden"),
            allowed(
                HttpMethod.GET,
                "/admin/realms/weave/users/subject-1/sessions",
                "forbidden"),
            allowed(
                HttpMethod.DELETE,
                "/admin/realms/weave/sessions/session-1",
                "forbidden"),
            allowed(
                HttpMethod.DELETE,
                "/admin/realms/weave/users/subject-1/logout",
                "forbidden"));

    forbidden.forEach(
        operation ->
            assertThatThrownBy(
                    () ->
                        KeycloakIdentityAdminOperationPolicy.requireAllowed(
                            operation.method(), operation.uri()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("guarded boundary"));
  }

  private static AllowedOperation allowed(
      HttpMethod method, String uri, String operationCode) {
    return new AllowedOperation(method, uri, operationCode);
  }

  private record AllowedOperation(HttpMethod method, String uri, String operationCode) {}
}

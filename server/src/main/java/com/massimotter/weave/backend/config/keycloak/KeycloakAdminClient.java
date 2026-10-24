package com.massimotter.weave.backend.config.keycloak;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

/** Named provider operations; the protected transport admits their exact method, path and status. */
@HttpExchange("/admin/realms/{realm}")
public interface KeycloakAdminClient {
  String MANAGED_FIXTURE_ATTRIBUTE = "weave.keycloak.managed-fixture";

  @GetExchange("/users?first=0&max=1&briefRepresentation=true")
  String humanUsers(@PathVariable("realm") String realm);

  @GetExchange("/organizations?first={first}&max={max}&briefRepresentation=true")
  String organizations(@PathVariable("realm") String realm,
      @PathVariable("first") int first, @PathVariable("max") int max);

  @GetExchange("/organizations/{organization}/members?first={first}&max={max}&briefRepresentation=false")
  String members(@PathVariable("realm") String realm,
      @PathVariable("organization") String organization,
      @PathVariable("first") int first, @PathVariable("max") int max);

  @GetExchange("/organizations/{organization}/members/{subject}")
  String member(@PathVariable("realm") String realm,
      @PathVariable("organization") String organization, @PathVariable("subject") String subject);

  @DeleteExchange("/organizations/{organization}/members/{subject}")
  String removeMember(@PathVariable("realm") String realm,
      @PathVariable("organization") String organization, @PathVariable("subject") String subject);

  @GetExchange("/organizations/{organization}/members/{subject}/groups?briefRepresentation=true")
  String memberGroups(@PathVariable("realm") String realm,
      @PathVariable("organization") String organization, @PathVariable("subject") String subject);

  @GetExchange("/organizations/{organization}/invitations")
  String invitations(@PathVariable("realm") String realm,
      @PathVariable("organization") String organization,
      @RequestParam(name = "email", required = false) String email);

  @PostExchange(url = "/organizations/{organization}/members/invite-user",
      contentType = "application/x-www-form-urlencoded")
  String inviteUser(@PathVariable("realm") String realm,
      @PathVariable("organization") String organization, @RequestBody String form);

  @PostExchange(url = "/organizations/{organization}/invitations/{invitation}/resend",
      contentType = "application/json")
  String resendInvitation(@PathVariable("realm") String realm,
      @PathVariable("organization") String organization,
      @PathVariable("invitation") String invitation, @RequestBody String body);

  @DeleteExchange("/organizations/{organization}/invitations/{invitation}")
  String revokeInvitation(@PathVariable("realm") String realm,
      @PathVariable("organization") String organization,
      @PathVariable("invitation") String invitation);

  @GetExchange("/organizations/{organization}/groups?search={search}&exact=true")
  String groups(@PathVariable("realm") String realm,
      @PathVariable("organization") String organization, @PathVariable("search") String search);

  @PutExchange("/organizations/{organization}/groups/{group}/members/{subject}")
  String addGroupMember(@PathVariable("realm") String realm,
      @PathVariable("organization") String organization, @PathVariable("group") String group,
      @PathVariable("subject") String subject);

  @DeleteExchange("/organizations/{organization}/groups/{group}/members/{subject}")
  String removeGroupMember(@PathVariable("realm") String realm,
      @PathVariable("organization") String organization, @PathVariable("group") String group,
      @PathVariable("subject") String subject);

  @GetExchange("/users/{subject}")
  String user(@PathVariable("realm") String realm, @PathVariable("subject") String subject);

  @PutExchange(url = "/users/{subject}", contentType = "application/json")
  String updateUser(@PathVariable("realm") String realm,
      @PathVariable("subject") String subject, @RequestBody String body);

  @PostExchange(url = "/users/{subject}/logout", contentType = "application/json")
  String logout(@PathVariable("realm") String realm,
      @PathVariable("subject") String subject, @RequestBody String body);

  // These methods require the separate, explicitly authorized managed-fixture admission path.
  @GetExchange("/users?email={email}&exact=true&first=0&max=2")
  String managedUsers(@PathVariable("realm") String realm, @PathVariable("email") String email,
      @RequestAttribute(MANAGED_FIXTURE_ATTRIBUTE) boolean managedFixture);

  @PostExchange(url = "/users", contentType = "application/json")
  String createManagedUser(@PathVariable("realm") String realm, @RequestBody String body,
      @RequestAttribute(MANAGED_FIXTURE_ATTRIBUTE) boolean managedFixture);

  @PostExchange(url = "/organizations/{organization}/members", contentType = "application/json")
  String addManagedMember(@PathVariable("realm") String realm,
      @PathVariable("organization") String organization, @RequestBody String body,
      @RequestAttribute(MANAGED_FIXTURE_ATTRIBUTE) boolean managedFixture);

  @GetExchange("/users/{subject}/credentials")
  String managedCredentials(@PathVariable("realm") String realm,
      @PathVariable("subject") String subject,
      @RequestAttribute(MANAGED_FIXTURE_ATTRIBUTE) boolean managedFixture);

  @PutExchange(url = "/users/{subject}/reset-password", contentType = "application/json")
  String setManagedInitialPassword(@PathVariable("realm") String realm,
      @PathVariable("subject") String subject, @RequestBody String body,
      @RequestAttribute(MANAGED_FIXTURE_ATTRIBUTE) boolean managedFixture);
}

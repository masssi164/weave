package com.massimotter.weave.backend.identity.keycloak;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;
import tools.jackson.databind.JsonNode;

/** Named provider operations; the protected transport admits their exact method, path and status. */
@HttpExchange("/admin/realms/{realm}")
public interface KeycloakAdminClient {
  String MANAGED_FIXTURE_ATTRIBUTE = "weave.keycloak.managed-fixture";

  @GetExchange("/users")
  JsonNode humanUsers(
      @PathVariable("realm") String realm,
      @RequestParam("first") int first,
      @RequestParam("max") int max,
      @RequestParam("briefRepresentation") boolean briefRepresentation);

  @GetExchange("/organizations")
  JsonNode organizations(
      @PathVariable("realm") String realm,
      @RequestParam("first") int first,
      @RequestParam("max") int max,
      @RequestParam("briefRepresentation") boolean briefRepresentation);

  @GetExchange("/organizations/{organization}/members")
  JsonNode members(
      @PathVariable("realm") String realm,
      @PathVariable("organization") String organization,
      @RequestParam("first") int first,
      @RequestParam("max") int max,
      @RequestParam("briefRepresentation") boolean briefRepresentation);

  @GetExchange("/organizations/{organization}/members/{subject}")
  JsonNode member(
      @PathVariable("realm") String realm,
      @PathVariable("organization") String organization,
      @PathVariable("subject") String subject);

  @DeleteExchange("/organizations/{organization}/members/{subject}")
  void removeMember(
      @PathVariable("realm") String realm,
      @PathVariable("organization") String organization,
      @PathVariable("subject") String subject);

  @GetExchange("/organizations/{organization}/members/{subject}/groups")
  JsonNode memberGroups(
      @PathVariable("realm") String realm,
      @PathVariable("organization") String organization,
      @PathVariable("subject") String subject,
      @RequestParam("briefRepresentation") boolean briefRepresentation);

  @GetExchange("/organizations/{organization}/invitations")
  JsonNode invitations(
      @PathVariable("realm") String realm,
      @PathVariable("organization") String organization,
      @RequestParam(name = "email", required = false) String email);

  @PostExchange(
      url = "/organizations/{organization}/members/invite-user",
      contentType = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  void inviteUser(
      @PathVariable("realm") String realm,
      @PathVariable("organization") String organization,
      @RequestParam("email") String email,
      @RequestParam("firstName") String firstName,
      @RequestParam("lastName") String lastName);

  @PostExchange("/organizations/{organization}/invitations/{invitation}/resend")
  void resendInvitation(
      @PathVariable("realm") String realm,
      @PathVariable("organization") String organization,
      @PathVariable("invitation") String invitation);

  @DeleteExchange("/organizations/{organization}/invitations/{invitation}")
  void revokeInvitation(
      @PathVariable("realm") String realm,
      @PathVariable("organization") String organization,
      @PathVariable("invitation") String invitation);

  @GetExchange("/organizations/{organization}/groups")
  JsonNode groups(
      @PathVariable("realm") String realm,
      @PathVariable("organization") String organization,
      @RequestParam("search") String search,
      @RequestParam("exact") boolean exact);

  @PutExchange("/organizations/{organization}/groups/{group}/members/{subject}")
  void addGroupMember(
      @PathVariable("realm") String realm,
      @PathVariable("organization") String organization,
      @PathVariable("group") String group,
      @PathVariable("subject") String subject);

  @DeleteExchange("/organizations/{organization}/groups/{group}/members/{subject}")
  void removeGroupMember(
      @PathVariable("realm") String realm,
      @PathVariable("organization") String organization,
      @PathVariable("group") String group,
      @PathVariable("subject") String subject);

  @GetExchange("/users/{subject}")
  JsonNode user(
      @PathVariable("realm") String realm, @PathVariable("subject") String subject);

  @PutExchange(url = "/users/{subject}", contentType = MediaType.APPLICATION_JSON_VALUE)
  void updateUser(
      @PathVariable("realm") String realm,
      @PathVariable("subject") String subject,
      @RequestBody JsonNode body);

  @PostExchange("/users/{subject}/logout")
  void logout(
      @PathVariable("realm") String realm, @PathVariable("subject") String subject);

  // These methods require the separate, explicitly authorized managed-fixture admission path.
  @GetExchange("/users")
  JsonNode managedUsers(
      @PathVariable("realm") String realm,
      @RequestParam("email") String email,
      @RequestParam("exact") boolean exact,
      @RequestParam("first") int first,
      @RequestParam("max") int max,
      @RequestAttribute(MANAGED_FIXTURE_ATTRIBUTE) boolean managedFixture);

  @PostExchange(url = "/users", contentType = MediaType.APPLICATION_JSON_VALUE)
  void createManagedUser(
      @PathVariable("realm") String realm,
      @RequestBody JsonNode body,
      @RequestAttribute(MANAGED_FIXTURE_ATTRIBUTE) boolean managedFixture);

  @PostExchange(
      url = "/organizations/{organization}/members",
      contentType = MediaType.APPLICATION_JSON_VALUE)
  void addManagedMember(
      @PathVariable("realm") String realm,
      @PathVariable("organization") String organization,
      @RequestBody JsonNode body,
      @RequestAttribute(MANAGED_FIXTURE_ATTRIBUTE) boolean managedFixture);

  @GetExchange("/users/{subject}/credentials")
  JsonNode managedCredentials(
      @PathVariable("realm") String realm,
      @PathVariable("subject") String subject,
      @RequestAttribute(MANAGED_FIXTURE_ATTRIBUTE) boolean managedFixture);

  @PutExchange(
      url = "/users/{subject}/reset-password",
      contentType = MediaType.APPLICATION_JSON_VALUE)
  void setManagedInitialPassword(
      @PathVariable("realm") String realm,
      @PathVariable("subject") String subject,
      @RequestBody JsonNode body,
      @RequestAttribute(MANAGED_FIXTURE_ATTRIBUTE) boolean managedFixture);
}

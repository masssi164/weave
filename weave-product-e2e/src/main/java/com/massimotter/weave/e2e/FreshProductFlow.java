package com.massimotter.weave.e2e;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One-process Fresh product proof.
 *
 * <p>Human passwords, action links, OAuth tokens, and private JWKs are held only in this process.
 * The only durable output is an allowlisted support-safe evidence document.
 */
public final class FreshProductFlow {
  private static final Set<PosixFilePermission> OWNER_FILE_PERMISSIONS =
      PosixFilePermissions.fromString("rw-------");

  private final ProductFlowEnvironment environment;
  private final JsonHttpClient http;
  private final SecureRandom random = new SecureRandom();

  private FreshProductFlow(ProductFlowEnvironment environment) {
    this.environment = environment;
    this.http = new JsonHttpClient(environment.caCertificate());
  }

  public static void main(String[] arguments) {
    if (arguments.length != 0) {
      System.err.println("WEAVE_TEST_APP_ERROR command line arguments are not accepted");
      System.exit(2);
    }
    try {
      ProductFlowEnvironment environment = ProductFlowEnvironment.fromSystemProperties();
      System.setProperty("jdk.net.hosts.file", environment.hostsFile().toString());
      new FreshProductFlow(environment).run();
      System.out.println(
          "WEAVE_TEST_APP_RESULT status=passed activation=browser pkce=S256 "
              + "workload=private_key_jwt tool=files.search projection=webdav supportSafe=true");
    } catch (RuntimeException failure) {
      System.err.println(
          "WEAVE_TEST_APP_ERROR "
              + failure.getClass().getSimpleName()
              + " "
              + safeMessage(failure.getMessage()));
      System.exit(1);
    }
  }

  private void run() {
    Instant startedAt = Instant.now();
    String ownerPassword = randomPassword();
    String memberPassword = randomPassword();
    String outsiderPassword = randomPassword();
    String ownerEmail = environment.ownerEmail();
    String memberEmail = environment.memberEmail();
    String outsiderEmail = environment.outsiderEmail();
    String proofFile =
        "weave-e2e-" + Hashing.sha256(environment.runId()).substring(0, 16) + ".txt";
    boolean fileCreated = false;
    OidcBrowserJourney.TokenSet memberSession = null;
    OidcBrowserJourney.TokenSet outsiderSession = null;
    OidcBrowserJourney.TokenSet adminSession = null;
    String personRef = null;
    JsonNode startedRuntime = null;
    WorkloadMcpJourney.McpProof mcpProof = null;
    PersistenceRestartJourney.RestartProof restartProof = null;
    boolean revocationDenied = false;
    boolean regrantRestored = false;
    boolean sameHumanSubjectAfterRegrant = false;
    boolean samePersonRefAfterRegrant = false;
    List<CollaborationJourney.PassProof> collaborationPasses = new java.util.ArrayList<>();

    try (OidcBrowserJourney browser = new OidcBrowserJourney(environment, http)) {
      JsonNode ownerInvitation = bootstrapOwner(ownerEmail);
      String organizationId = requiredText(ownerInvitation, "organizationId");
      MailpitActivationInbox ownerInbox =
          new MailpitActivationInbox(
              http,
              environment.mailpitApi(),
              environment.issuer(),
              environment.convergenceTimeout());
      URI ownerAction =
          ownerInbox.awaitActivationLink(ownerEmail, startedAt.minusSeconds(5));
      Instant ownerRegistrationStartedAt = Instant.now();
      browser.activate(
          ownerAction,
          ownerEmail,
          ownerPassword,
          "Weave E2E Owner",
          () ->
              ownerInbox.awaitEmailVerificationLink(
                  ownerEmail, ownerRegistrationStartedAt.minusSeconds(5)));

      OidcBrowserJourney.TokenSet ownerSession =
          browser.authorize(
              "weave-app",
              URI.create("com.massimotter.weave:/oauthredirect"),
              List.of("openid", "profile", "email"),
              ownerEmail,
              ownerPassword,
              "owner-initial");
      validateHumanBootstrapToken(browser.jwtPayload(ownerSession.accessToken()), "weave-app");
      ownerSession =
          reconcileIdentitySession(
              browser, ownerSession, "owner", ownerEmail, ownerPassword);
      ownerSession = awaitAuthority(browser, ownerSession, "/owners", "owner");
      validateHumanWorkspaceToken(browser.jwtPayload(ownerSession.accessToken()), "weave-app");
      configureRequiredProviders(ownerSession.accessToken());
      awaitChatReadiness(ownerSession.accessToken());

      Instant memberInvitedAt = Instant.now();
      inviteActor(
          organizationId,
          memberEmail,
          "Weave E2E Member",
          "member",
          ownerSession.accessToken());
      MailpitActivationInbox memberInbox =
          new MailpitActivationInbox(
              http,
              environment.mailpitApi(),
              environment.issuer(),
              environment.convergenceTimeout());
      URI memberAction =
          memberInbox.awaitActivationLink(memberEmail, memberInvitedAt.minusSeconds(5));
      Instant memberRegistrationStartedAt = Instant.now();
      browser.activate(
          memberAction,
          memberEmail,
          memberPassword,
          "Weave E2E Member",
          () ->
              memberInbox.awaitEmailVerificationLink(
                  memberEmail, memberRegistrationStartedAt.minusSeconds(5)));
      memberSession =
          browser.authorize(
              "weave-app",
              URI.create("com.massimotter.weave:/oauthredirect"),
              List.of("openid", "profile", "email"),
              memberEmail,
              memberPassword,
              "member-initial");
      validateHumanBootstrapToken(browser.jwtPayload(memberSession.accessToken()), "weave-app");
      memberSession =
          reconcileIdentitySession(
              browser, memberSession, "member", memberEmail, memberPassword);
      setWeaverEntitlement(
          organizationId, memberEmail, ownerSession.accessToken(), true, "initial");
      memberSession =
          awaitAuthority(
              browser, memberSession, "/capabilities/weaver", "agent-runtime.entitled");
      JsonNode memberClaims = browser.jwtPayload(memberSession.accessToken());
      validateHumanWorkspaceToken(memberClaims, "weave-app");
      String memberUsername = memberEmail.substring(0, memberEmail.indexOf('@'));
      if (!memberUsername.equals(memberClaims.path("preferred_username").asString())) {
        throw new ProductFlowException(
            "member token does not match the isolated Context principal");
      }
      proveMemberApi(memberSession.accessToken());

      Instant outsiderInvitedAt = Instant.now();
      inviteActor(
          organizationId,
          outsiderEmail,
          "Weave E2E Outsider",
          "guest",
          ownerSession.accessToken());
      MailpitActivationInbox outsiderInbox =
          new MailpitActivationInbox(
              http,
              environment.mailpitApi(),
              environment.issuer(),
              environment.convergenceTimeout());
      URI outsiderAction =
          outsiderInbox.awaitActivationLink(outsiderEmail, outsiderInvitedAt.minusSeconds(5));
      Instant outsiderRegistrationStartedAt = Instant.now();
      browser.activate(
          outsiderAction,
          outsiderEmail,
          outsiderPassword,
          "Weave E2E Outsider",
          () ->
              outsiderInbox.awaitEmailVerificationLink(
                  outsiderEmail, outsiderRegistrationStartedAt.minusSeconds(5)));
      outsiderSession =
          browser.authorize(
              "weave-app",
              URI.create("com.massimotter.weave:/oauthredirect"),
              List.of("openid", "profile", "email"),
              outsiderEmail,
              outsiderPassword,
              "outsider-initial");
      validateHumanBootstrapToken(browser.jwtPayload(outsiderSession.accessToken()), "weave-app");
      outsiderSession =
          reconcileIdentitySession(
              browser, outsiderSession, "guest", outsiderEmail, outsiderPassword);
      outsiderSession = awaitAuthority(browser, outsiderSession, "/guests", "guest");
      validateHumanWorkspaceToken(browser.jwtPayload(outsiderSession.accessToken()), "weave-app");

      CollaborationJourney collaboration = new CollaborationJourney(environment, http);
      collaborationPasses.add(
          collaboration.runPass(
              1,
              ownerSession,
              memberSession,
              outsiderSession,
              browser.jwtPayload(ownerSession.accessToken()),
              browser.jwtPayload(memberSession.accessToken()),
              browser.jwtPayload(outsiderSession.accessToken())));
      collaboration.restartCollaborationServices();
      ownerSession =
          browser.authorize(
              "weave-app",
              URI.create("com.massimotter.weave:/oauthredirect"),
              List.of("openid", "profile", "email"),
              ownerEmail,
              ownerPassword,
              "owner-post-collaboration-restart");
      memberSession =
          browser.authorize(
              "weave-app",
              URI.create("com.massimotter.weave:/oauthredirect"),
              List.of("openid", "profile", "email"),
              memberEmail,
              memberPassword,
              "member-post-collaboration-restart");
      outsiderSession =
          browser.authorize(
              "weave-app",
              URI.create("com.massimotter.weave:/oauthredirect"),
              List.of("openid", "profile", "email"),
              outsiderEmail,
              outsiderPassword,
              "outsider-post-collaboration-restart");
      collaborationPasses.add(
          collaboration.runPass(
              2,
              ownerSession,
              memberSession,
              outsiderSession,
              browser.jwtPayload(ownerSession.accessToken()),
              browser.jwtPayload(memberSession.accessToken()),
              browser.jwtPayload(outsiderSession.accessToken())));

      adminSession =
          browser.authorize(
              "weave-admin-console",
              environment.productOrigin().resolve("/admin-console/"),
              List.of("openid", "profile", "email", "agent-runtime.admin"),
              ownerEmail,
              ownerPassword,
              "owner-agent-runtime-admin");
      validateAdminToken(browser.jwtPayload(adminSession.accessToken()));

      personRef =
          accountId(environment.issuer().toString(), memberSession.subject());
      JsonNode provisioned = provisionRuntime(personRef, adminSession.accessToken());
      startedRuntime = startRuntime(personRef, adminSession.accessToken(), provisioned);

      createProofFile(proofFile, memberSession.accessToken());
      fileCreated = true;
      mcpProof =
          new WorkloadMcpJourney(environment, http)
              .invokeFilesSearch(requiredText(startedRuntime, "cellRef"), proofFile);

      restartProof = new PersistenceRestartJourney(environment, http).restart();
      JsonNode persistedRuntime =
          getRuntime(personRef, adminSession.accessToken());
      requireSameRuntime(startedRuntime, persistedRuntime);
      WorkloadMcpJourney.McpProof postRestartMcpProof =
          new WorkloadMcpJourney(environment, http)
              .invokeFilesSearch(requiredText(startedRuntime, "cellRef"), proofFile);
      if (!mcpProof.equals(postRestartMcpProof)) {
        throw new ProductFlowException(
            "the same Cell MCP projection did not persist across service restarts");
      }

      String originalSubject = memberSession.subject();
      setWeaverEntitlement(
          organizationId, memberEmail, ownerSession.accessToken(), false, "revoke");
      memberSession =
          awaitAuthorityAbsent(
              browser, memberSession, "/capabilities/weaver", "agent-runtime.entitled");
      JsonNode revokedRuntime = reconcileRuntime(personRef, adminSession.accessToken(), "revoke");
      if (!"revoked".equals(revokedRuntime.path("entitlementState").asString())) {
        throw new ProductFlowException("ARC reconciliation did not revoke the unentitled cell");
      }
      try {
        new WorkloadMcpJourney(environment, http)
            .invokeFilesSearch(requiredText(startedRuntime, "cellRef"), proofFile);
      } catch (ProductFlowException expectedDenial) {
        revocationDenied = true;
      }
      if (!revocationDenied) {
        throw new ProductFlowException("revoked cell remained able to invoke MCP");
      }

      setWeaverEntitlement(
          organizationId, memberEmail, ownerSession.accessToken(), true, "regrant");
      memberSession =
          awaitAuthority(
              browser, memberSession, "/capabilities/weaver", "agent-runtime.entitled");
      sameHumanSubjectAfterRegrant = originalSubject.equals(memberSession.subject());
      String regrantedPersonRef =
          accountId(environment.issuer().toString(), memberSession.subject());
      samePersonRefAfterRegrant = personRef.equals(regrantedPersonRef);
      if (!sameHumanSubjectAfterRegrant || !samePersonRefAfterRegrant) {
        throw new ProductFlowException("Weaver regrant replaced the immutable human identity");
      }
      JsonNode regrantedRuntime =
          provisionRuntime(regrantedPersonRef, adminSession.accessToken(), "regrant");
      JsonNode restartedRuntime =
          startRuntime(regrantedPersonRef, adminSession.accessToken(), regrantedRuntime, "regrant");
      requireSameRuntimeIdentity(startedRuntime, restartedRuntime);
      WorkloadMcpJourney.McpProof postRegrantMcpProof =
          new WorkloadMcpJourney(environment, http)
              .invokeFilesSearch(requiredText(restartedRuntime, "cellRef"), proofFile);
      regrantRestored = mcpProof.equals(postRegrantMcpProof);
      if (!regrantRestored) {
        throw new ProductFlowException(
            "the regranted Cell did not restore the same MCP projection");
      }

      writeEvidence(
          startedAt,
          ownerEmail,
          memberEmail,
          outsiderEmail,
          requiredText(startedRuntime, "cellRef"),
          mcpProof,
          restartProof,
          revocationDenied,
          regrantRestored,
          sameHumanSubjectAfterRegrant,
          samePersonRefAfterRegrant,
          collaborationPasses);
    } finally {
      if (fileCreated && memberSession != null) {
        deleteProofFile(proofFile, memberSession.accessToken());
      }
      // Avoid retaining references longer than the single bounded JVM run.
      ownerPassword = "";
      memberPassword = "";
      outsiderPassword = "";
      memberSession = null;
      outsiderSession = null;
      adminSession = null;
      personRef = null;
      startedRuntime = null;
      mcpProof = null;
      restartProof = null;
    }
  }

  private JsonNode bootstrapOwner(String email) {
    ObjectNode request = http.mapper().createObjectNode();
    request.put("email", email);
    request.put("displayName", "Weave E2E Owner");
    return http.json(
        "create first owner invitation",
        "POST",
        environment.api("/api/bootstrap/owner-invitation"),
        Map.of(
            "X-Weave-Bootstrap-Token", readBootstrapToken(),
            "Idempotency-Key", "test-app-owner-" + runHash()),
        request,
        Set.of(200, 201));
  }

  private void inviteActor(
      String organizationId,
      String email,
      String displayName,
      String role,
      String accessToken) {
    ObjectNode request = http.mapper().createObjectNode();
    request.put("email", email);
    request.put("displayName", displayName);
    request.put("role", role);
    JsonNode invitation =
        http.json(
            "invite " + role + " through Weave",
            "POST",
            environment.api(
                "/api/admin/organizations/"
                    + encodeSegment(organizationId)
                    + "/invitations"),
            bearer(
                accessToken,
                Map.of("Idempotency-Key", "test-app-" + role + "-" + runHash())),
            request,
            Set.of(201));
    if (!role.equals(invitation.path("requestedRole").asString())
        || invitation.has("capabilities")) {
      throw new ProductFlowException(role + " invitation projection is invalid");
    }
  }

  private void setWeaverEntitlement(
      String organizationId,
      String email,
      String accessToken,
      boolean entitled,
      String operation) {
    JsonNode page =
        http.json(
            "list organization members for Weaver assignment",
            "GET",
            environment.api(
                "/api/admin/organizations/"
                    + encodeSegment(organizationId)
                    + "/members?size=100"),
            bearer(accessToken, Map.of()),
            null,
            Set.of(200));
    JsonNode member = null;
    for (JsonNode candidate : page.path("items")) {
      if (email.equalsIgnoreCase(candidate.path("email").asString())) {
        if (member != null) {
          throw new ProductFlowException("member projection is ambiguous");
        }
        member = candidate;
      }
    }
    if (member == null) {
      throw new ProductFlowException("activated member is missing");
    }
    ObjectNode request = http.mapper().createObjectNode();
    request.put("entitled", entitled);
    JsonNode updated =
        http.json(
            (entitled ? "assign" : "remove") + " native Weaver organization capability",
            "PUT",
            environment.api(
                "/api/admin/organizations/"
                    + encodeSegment(organizationId)
                    + "/members/"
                    + encodeSegment(requiredText(member, "memberHandle"))
                    + "/capabilities/weaver"),
            bearer(
                accessToken,
                Map.of(
                    "If-Match", requiredText(member, "version"),
                    "Idempotency-Key",
                        "test-app-weaver-" + operation + "-" + runHash())),
            request,
            Set.of(200));
    Set<String> expected = entitled ? Set.of("agent-runtime.entitled") : Set.of();
    if (!strings(updated.path("capabilities")).equals(expected)) {
      throw new ProductFlowException("native Weaver capability mutation did not converge");
    }
  }

  private OidcBrowserJourney.TokenSet awaitAuthority(
      OidcBrowserJourney browser,
      OidcBrowserJourney.TokenSet initial,
      String group,
      String role) {
    Instant deadline = Instant.now().plus(environment.convergenceTimeout());
    OidcBrowserJourney.TokenSet current = initial;
    Set<String> observedGroups = Set.of();
    Set<String> observedRoles = Set.of();
    Set<String> observedScopes = Set.of();
    while (Instant.now().isBefore(deadline)) {
      JsonNode claims = browser.jwtPayload(current.accessToken());
      observedGroups = organizationGroups(claims);
      observedRoles = organizationRoles(claims, "weave-app");
      observedScopes = tokenScopes(claims);
      if (observedGroups.contains(group) || observedRoles.contains(role)) {
        return current;
      }
      sleep();
      current = browser.refresh(current);
    }
    throw new ProductFlowException(
        "Keycloak role and capability projection did not converge"
            + " expectedGroup="
            + group
            + " expectedRole="
            + role
            + " observedGroups="
            + new java.util.TreeSet<>(observedGroups)
            + " observedRoles="
            + new java.util.TreeSet<>(observedRoles)
            + " observedScopes="
            + new java.util.TreeSet<>(observedScopes));
  }

  private OidcBrowserJourney.TokenSet awaitAuthorityAbsent(
      OidcBrowserJourney browser,
      OidcBrowserJourney.TokenSet initial,
      String group,
      String role) {
    Instant deadline = Instant.now().plus(environment.convergenceTimeout());
    OidcBrowserJourney.TokenSet current = initial;
    while (Instant.now().isBefore(deadline)) {
      current = browser.refresh(current);
      JsonNode claims = browser.jwtPayload(current.accessToken());
      if (!organizationGroups(claims).contains(group)
          && !organizationRoles(claims, "weave-app").contains(role)) {
        return current;
      }
      sleep();
    }
    throw new ProductFlowException("revoked Weaver authority remained in the human session");
  }

  private void proveMemberApi(String token) {
    JsonNode readiness =
        http.json(
            "read authenticated profile readiness",
            "GET",
            environment.api("/api/profile/readiness"),
            bearer(token, Map.of()),
            null,
            Set.of(200));
    if (!readiness.path("supportSafe").asBoolean(false)) {
      throw new ProductFlowException("profile readiness was not support-safe");
    }
  }

  private void configureRequiredProviders(String ownerToken) {
    List<ProviderSelection> requiredProviders =
        List.of(
            new ProviderSelection("chat", "weave-native"),
            new ProviderSelection("files", "weave-native"),
            new ProviderSelection("calendar", "weave-native"));
    for (ProviderSelection selection : requiredProviders) {
      ObjectNode request = http.mapper().createObjectNode();
      request.put("category", selection.category());
      request.put("providerKey", selection.providerKey());
      request.put("choiceModel", "recommended_self_hosted_default");
      request.put("dryRun", false);
      request.putArray("lossyMappingNotes");
      request.put("reason", "configure the isolated Fresh Stack product path");
      JsonNode response =
          http.json(
              "apply " + selection.category() + " provider selection",
              "POST",
              environment.api("/api/admin/providers/selections"),
              bearer(ownerToken, Map.of()),
              request,
              Set.of(200));
      if (!selection.category().equals(response.path("category").asString())
          || !selection.providerKey().equals(response.path("providerKey").asString())
          || !"recommended_self_hosted_default".equals(
              response.path("choiceModel").asString())
          || !response.path("applied").asBoolean(false)
          || response.path("dryRun").asBoolean(true)
          || !response.path("supportSafe").asBoolean(false)) {
        throw new ProductFlowException(
            selection.category() + " provider selection did not converge");
      }
    }
  }

  private void awaitChatReadiness(String ownerToken) {
    Instant deadline = Instant.now().plus(environment.convergenceTimeout());
    String observedState = "unavailable";
    while (Instant.now().isBefore(deadline)) {
      JsonNode readiness =
          http.json(
              "read Chat provider readiness",
              "GET",
              environment.api("/api/chat/readiness"),
              bearer(ownerToken, Map.of()),
              null,
              Set.of(200));
      observedState = readiness.path("memberState").asString();
      if ("available".equals(observedState)
          && "chat".equals(readiness.path("domain").asString())
          && !readiness.path("failClosed").asBoolean(true)
          && readiness.path("supportSafe").asBoolean(false)) {
        return;
      }
      sleep();
    }
    throw new ProductFlowException(
        "Chat provider readiness did not converge observedState=" + observedState);
  }

  private OidcBrowserJourney.TokenSet reconcileIdentitySession(
      OidcBrowserJourney browser,
      OidcBrowserJourney.TokenSet session,
      String expectedRole,
      String email,
      String password) {
    JsonNode response =
        http.json(
            "reconcile authenticated identity session",
            "POST",
            environment.api("/api/v1/identity/session/reconcile"),
            bearer(session.accessToken(), Map.of()),
            null,
            Set.of(200));
    if (!"access_updated".equals(response.path("state").asString())
        || !response.path("reauthorizationRequired").asBoolean(false)
        || response.has("sessionRefreshRequired")) {
      throw new ProductFlowException(
          "identity session did not apply the pending " + expectedRole + " intent");
    }
    return browser.authorize(
        "weave-app",
        URI.create("com.massimotter.weave:/oauthredirect"),
        session.requestedScopes(),
        email,
        password,
        expectedRole + "-post-session-reconcile");
  }

  private void validateAdminToken(JsonNode claims) {
    Set<String> audiences = strings(claims.path("aud"));
    Set<String> scopes = tokenScopes(claims);
    if (!"weave-admin-console".equals(claims.path("azp").asString())
        || !audiences.equals(Set.of(environment.apiOrigin().resolve("/api").toString()))
        || !hasExactWorkspaceScope(scopes)
        || !scopes.contains("agent-runtime.admin")
        || !organizationGroups(claims).contains("/owners")
        || !organizationRoles(claims, "weave-app").contains("owner")) {
      throw new ProductFlowException("Agent Runtime admin token is not exact");
    }
  }

  private void validateHumanWorkspaceToken(JsonNode claims, String clientId) {
    validateHumanToken(claims, clientId, true);
  }

  private void validateHumanBootstrapToken(JsonNode claims, String clientId) {
    validateHumanToken(claims, clientId, false);
  }

  private void validateHumanToken(
      JsonNode claims, String clientId, boolean workspaceAccessExpected) {
    Set<String> scopes = tokenScopes(claims);
    Set<String> invalidClaims = new java.util.TreeSet<>();
    if (!clientId.equals(claims.path("azp").asString())) {
      invalidClaims.add("authorized-party");
    }
    String explicitClientId = claims.path("client_id").asString("");
    if (!explicitClientId.isBlank() && !clientId.equals(explicitClientId)) {
      invalidClaims.add("client-id");
    }
    if (!strings(claims.path("aud"))
        .contains(environment.apiOrigin().resolve("/api").toString())) {
      invalidClaims.add("audience");
    }
    if (claims.path("email").asString("").isBlank()) {
      invalidClaims.add("email");
    }
    if (!claims.path("email_verified").asBoolean(false)) {
      invalidClaims.add("email-verified");
    }
    if (!hasExactWorkspaceScope(scopes)) {
      invalidClaims.add("workspace-scope");
    }
    Set<String> organizationRoles = organizationRoles(claims, "weave-app");
    Set<String> productRoles = Set.of("owner", "admin", "member", "guest");
    long productRoleCount = organizationRoles.stream().filter(productRoles::contains).count();
    if (workspaceAccessExpected && productRoleCount != 1) {
      invalidClaims.add("selected-organization-role");
    }
    if (!workspaceAccessExpected && productRoleCount != 0) {
      invalidClaims.add("premature-selected-organization-role");
    }
    if (strings(claims.path("resource_access").path("weave-app").path("roles"))
        .stream()
        .anyMatch(productRoles::contains)) {
      invalidClaims.add("top-level-product-role");
    }
    if (!invalidClaims.isEmpty()) {
      throw new ProductFlowException(
          (workspaceAccessExpected ? "human workspace token" : "human bootstrap token")
              + " contract did not match fields="
              + String.join(",", invalidClaims)
              + " observedScopes="
              + new java.util.TreeSet<>(scopes));
    }
  }

  private static boolean hasExactWorkspaceScope(Set<String> scopes) {
    return scopes.contains("weave:workspace") && !scopes.contains("weave-workspace");
  }

  private static Set<String> tokenScopes(JsonNode claims) {
    Set<String> result = new java.util.LinkedHashSet<>();
    for (String scope : claims.path("scope").asString("").trim().split("\\s+")) {
      if (!scope.isBlank()) {
        result.add(scope);
      }
    }
    return Set.copyOf(result);
  }

  private JsonNode provisionRuntime(String personRef, String token) {
    return provisionRuntime(personRef, token, "initial");
  }

  private JsonNode provisionRuntime(String personRef, String token, String operation) {
    JsonNode response =
        http.json(
            "provision Agent Runtime cell",
            "POST",
            runtimeUri(personRef, "/provision"),
            bearer(
                token,
                Map.of(
                    "Idempotency-Key",
                    "test-app-provision-" + operation + "-" + runHash())),
            null,
            Set.of(202));
    if (!personRef.equals(response.path("personRef").asString())
        || !"entitled".equals(response.path("entitlementState").asString())) {
      throw new ProductFlowException("ARC provision projection is not entitled");
    }
    return response;
  }

  private JsonNode startRuntime(String personRef, String token, JsonNode provisioned) {
    return startRuntime(personRef, token, provisioned, "initial");
  }

  private JsonNode startRuntime(
      String personRef, String token, JsonNode provisioned, String operation) {
    JsonNode response =
        http.json(
            "start Agent Runtime cell",
            "POST",
            runtimeUri(personRef, "/start"),
            bearer(
                token,
                Map.of(
                    "Idempotency-Key",
                    "test-app-start-" + operation + "-" + runHash())),
            null,
            Set.of(202));
    if (!requiredText(provisioned, "cellRef").equals(response.path("cellRef").asString())
        || response.path("runtimeProfileRef").asString("").isBlank()) {
      throw new ProductFlowException("ARC start did not issue the current RuntimeProfile");
    }
    return response;
  }

  private JsonNode reconcileRuntime(String personRef, String token, String operation) {
    return
        http.json(
            "reconcile Agent Runtime entitlement",
            "POST",
            runtimeUri(personRef, "/reconcile"),
            bearer(
                token,
                Map.of(
                    "Idempotency-Key",
                    "test-app-reconcile-" + operation + "-" + runHash())),
            null,
            Set.of(202));
  }

  private JsonNode getRuntime(String personRef, String token) {
    return http.json(
        "read persisted Agent Runtime cell after service restart",
        "GET",
        runtimeUri(personRef, ""),
        bearer(token, Map.of()),
        null,
        Set.of(200));
  }

  private static void requireSameRuntime(JsonNode before, JsonNode after) {
    for (String field :
        List.of(
            "personRef",
            "cellRef",
            "runtimeProfileRef",
            "entitlementRevision",
            "entitlementState",
            "desiredState")) {
      if (!requiredText(before, field).equals(requiredText(after, field))) {
        throw new ProductFlowException(
            "the JPA Cell projection changed across PostgreSQL restart field=" + field);
      }
    }
  }

  private static void requireSameRuntimeIdentity(JsonNode before, JsonNode after) {
    for (String field : List.of("personRef", "cellRef")) {
      if (!requiredText(before, field).equals(requiredText(after, field))) {
        throw new ProductFlowException(
            "the Weaver regrant replaced the immutable Runtime Cell field=" + field);
      }
    }
  }

  private URI runtimeUri(String personRef, String operation) {
    return environment.api(
        "/api/admin/agent-runtimes/" + encodeSegment(personRef) + operation);
  }

  private void createProofFile(String fileName, String token) {
    JsonHttpClient.Response response =
        http.send(
            "create Files WebDAV proof object",
            "PUT",
            environment.api("/dav/files/" + encodeSegment(fileName)),
            bearer(token, Map.of("If-None-Match", "*")),
            "text/plain; charset=utf-8",
            ("Weave testApp " + runHash()).getBytes(StandardCharsets.UTF_8),
            Set.of(201));
    if (response.firstHeader("ETag").isBlank()) {
      throw new ProductFlowException("Files WebDAV proof object omitted its ETag");
    }
  }

  private void deleteProofFile(String fileName, String token) {
    try {
      http.send(
          "delete Files WebDAV proof object",
          "DELETE",
          environment.api("/dav/files/" + encodeSegment(fileName)),
          bearer(token, Map.of()),
          null,
          null,
          Set.of(204, 404));
    } catch (ProductFlowException cleanupFailure) {
      System.err.println(
          "WEAVE_TEST_APP_CLEANUP_ERROR "
              + safeMessage(cleanupFailure.getMessage()));
    }
  }

  private void writeEvidence(
      Instant startedAt,
      String ownerEmail,
      String memberEmail,
      String outsiderEmail,
      String cellRef,
      WorkloadMcpJourney.McpProof mcpProof,
      PersistenceRestartJourney.RestartProof restartProof,
      boolean revocationDenied,
      boolean regrantRestored,
      boolean sameHumanSubjectAfterRegrant,
      boolean samePersonRefAfterRegrant,
      List<CollaborationJourney.PassProof> collaborationPasses) {
    ObjectNode evidence = http.mapper().createObjectNode();
    evidence.put("schemaVersion", "weave.test-app-product-flow/v2");
    evidence.put("startedAt", startedAt.toString());
    evidence.put("completedAt", Instant.now().toString());
    evidence.put("candidateCommit", environment.candidateCommit());
    evidence.put("sourceCandidateCommit", environment.sourceCandidateCommit());
    evidence.put("specificationCommit", environment.specificationCommit());
    evidence.put("candidateManifestDigest", environment.candidateManifestDigest());
    evidence.put("composeProject", environment.composeProject());
    evidence.put("runIdSha256", Hashing.sha256(environment.runId()));
    evidence.put("ownerEmailSha256", Hashing.sha256(ownerEmail));
    evidence.put("memberEmailSha256", Hashing.sha256(memberEmail));
    evidence.put("outsiderEmailSha256", Hashing.sha256(outsiderEmail));
    evidence.put("cellRefSha256", Hashing.sha256(cellRef));
    evidence.put("activation", "keycloak-required-actions-real-chromium");
    evidence.put("humanOAuth", "authorization_code_pkce_s256");
    evidence.put("workloadOAuth", "client_credentials_private_key_jwt");
    evidence.put("mcpTool", mcpProof.toolName());
    evidence.put("serverProjection", mcpProof.serverProjection());
    evidence.put("canonicalResourceSeen", mcpProof.canonicalResourceSeen());
    evidence.put("postgresRestartObserved", restartProof.postgresRestartObserved());
    evidence.put(
        "runtimeStateRestartObserved", restartProof.runtimeStateRestartObserved());
    evidence.put(
        "runtimeStateFixtureRestored", restartProof.runtimeStateFixtureRestored());
    evidence.put("sameJpaCellAfterRestart", true);
    evidence.put("sameMcpCellAfterRestart", true);
    evidence.put(
        "persistenceRestartEvidenceSha256",
        "sha256:" + restartProof.evidenceSha256());
    evidence.put("revocationDenied", revocationDenied);
    evidence.put("regrantRestored", regrantRestored);
    evidence.put("sameHumanSubjectAfterRegrant", sameHumanSubjectAfterRegrant);
    evidence.put("samePersonRefAfterRegrant", samePersonRefAfterRegrant);
    if (collaborationPasses.size() != 2
        || collaborationPasses.get(0).pass() != 1
        || collaborationPasses.get(1).pass() != 2
        || !collaborationPasses.get(0).authorIdentityRefHash()
            .equals(collaborationPasses.get(1).authorIdentityRefHash())
        || !collaborationPasses.get(0).collaboratorIdentityRefHash()
            .equals(collaborationPasses.get(1).collaboratorIdentityRefHash())
        || !collaborationPasses.get(0).outsiderIdentityRefHash()
            .equals(collaborationPasses.get(1).outsiderIdentityRefHash())) {
      throw new ProductFlowException("two-pass collaboration identity binding is incomplete");
    }
    ObjectNode collaboration = evidence.putObject("collaboration");
    collaboration.put("repeatCount", 2);
    ObjectNode selectedProviders = collaboration.putObject("selectedProviders");
    selectedProviders.put("chat", "weave-native");
    selectedProviders.put("files", "weave-native");
    selectedProviders.put("calendar", "weave-native");
    ObjectNode northboundFacades = collaboration.putObject("northboundFacades");
    northboundFacades.put("matrix", true);
    northboundFacades.put("webdav", true);
    northboundFacades.put("caldav", true);
    collaboration.put("southboundProviderDependencyObserved", false);
    ObjectNode identityHashes = collaboration.putObject("identityRefHashes");
    identityHashes.put("author", collaborationPasses.get(0).authorIdentityRefHash());
    identityHashes.put(
        "collaborator", collaborationPasses.get(0).collaboratorIdentityRefHash());
    identityHashes.put("outsider", collaborationPasses.get(0).outsiderIdentityRefHash());
    var passes = collaboration.putArray("passes");
    for (CollaborationJourney.PassProof pass : collaborationPasses) {
      ObjectNode item = passes.addObject();
      item.put("pass", pass.pass());
      item.put("freshAuthorizationCodePkce", pass.freshAuthorizationCodePkce());
      item.put("chatPassed", pass.chatPassed());
      item.put("filesPassed", pass.filesPassed());
      item.put("calendarPassed", pass.calendarPassed());
      item.put("homePassed", pass.homePassed());
      item.put("profilePassed", pass.profilePassed());
      item.put("outsiderDenied", pass.outsiderDenied());
      item.put("canonicalJpaVerified", pass.canonicalJpaVerified());
      item.put("nativePersistenceVerified", pass.nativePersistenceVerified());
      item.put("idempotencyVerified", pass.idempotencyVerified());
      item.put(
          "southboundProviderDependencyObserved",
          pass.southboundProviderDependencyObserved());
      item.put("restartContinuityVerified", pass.restartContinuityVerified());
      item.put("cleanupComplete", pass.cleanupComplete());
      item.put(
          "nativeRevisionHash", "sha256:" + pass.nativeRevisionHash());
    }
    evidence.put("credentialsIncluded", false);
    evidence.put("actionLinksIncluded", false);
    evidence.put("supportSafe", true);
    String serialized;
    try {
      serialized =
          http.mapper()
              .writerWithDefaultPrettyPrinter()
              .writeValueAsString(evidence)
              + "\n";
    } catch (JacksonException failure) {
      throw new ProductFlowException("testApp evidence encoding failed", failure);
    }
    if (serialized.contains("/protocol/openid-connect/registrations")
        || serialized.contains(ownerEmail)
        || serialized.contains(memberEmail)
        || serialized.contains(outsiderEmail)
        || serialized.contains(mcpProof.clientId())) {
      throw new ProductFlowException("testApp evidence failed secret-safe validation");
    }
    Path target = environment.evidenceFile();
    Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
    try {
      Files.createDirectories(target.getParent());
      Files.writeString(temporary, serialized, StandardCharsets.UTF_8);
      setPrivatePermissions(temporary);
      Files.move(
          temporary,
          target,
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING);
      setPrivatePermissions(target);
    } catch (IOException | UnsupportedOperationException failure) {
      try {
        Files.deleteIfExists(temporary);
      } catch (IOException ignored) {
        // The incomplete file contains support-safe hashes only.
      }
      throw new ProductFlowException("testApp evidence could not be persisted", failure);
    }
  }

  private String readBootstrapToken() {
    Path path = environment.bootstrapOwnerToken();
    try {
      if (Files.isSymbolicLink(path)
          || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
        throw new ProductFlowException("owner bootstrap SecretRef is unavailable");
      }
      requirePrivatePermissions(path);
      String token = Files.readString(path, StandardCharsets.UTF_8).strip();
      if (token.getBytes(StandardCharsets.UTF_8).length < 32
          || token.getBytes(StandardCharsets.UTF_8).length > 512) {
        throw new ProductFlowException("owner bootstrap SecretRef has an invalid size");
      }
      return token;
    } catch (IOException failure) {
      throw new ProductFlowException("owner bootstrap SecretRef could not be read", failure);
    }
  }

  private String randomPassword() {
    byte[] value = new byte[32];
    random.nextBytes(value);
    try {
      return "Wv!7" + Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    } finally {
      java.util.Arrays.fill(value, (byte) 0);
    }
  }

  private String runHash() {
    return Hashing.sha256(environment.runId()).substring(0, 24);
  }

  private static String accountId(String issuer, String subject) {
    String identityKey = "issuer+subject:" + issuer + "#" + subject;
    return "acct_" + Hashing.sha256(identityKey).substring(0, 32);
  }

  private static Map<String, String> bearer(
      String token, Map<String, String> additional) {
    Map<String, String> headers = new java.util.LinkedHashMap<>();
    headers.put("Authorization", "Bearer " + token);
    headers.putAll(additional);
    return Map.copyOf(headers);
  }

  private static Set<String> strings(JsonNode node) {
    if (node.isString()) {
      return Set.of(node.asString());
    }
    if (!node.isArray()) {
      return Set.of();
    }
    Set<String> result = new java.util.LinkedHashSet<>();
    node.forEach(value -> result.add(value.asString()));
    return Set.copyOf(result);
  }

  private static Set<String> organizationGroups(JsonNode claims) {
    JsonNode selected = selectedOrganization(claims);
    return selected == null ? Set.of() : strings(selected.path("groups"));
  }

  private static Set<String> organizationRoles(JsonNode claims, String clientId) {
    JsonNode selected = selectedOrganization(claims);
    return selected == null
        ? Set.of()
        : strings(selected.path("resource_access").path(clientId).path("roles"));
  }

  private static JsonNode selectedOrganization(JsonNode claims) {
    JsonNode organizations = claims.path("organization");
    if (!organizations.isObject() || organizations.size() != 1) {
      return null;
    }
    for (JsonNode value : organizations.values()) {
      return value;
    }
    return null;
  }

  private static String requiredText(JsonNode node, String field) {
    String value = node.path(field).asString("").trim();
    if (value.isEmpty()) {
      throw new ProductFlowException("product response omitted " + field);
    }
    return value;
  }

  private static String encodeSegment(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  private static void requirePrivatePermissions(Path path) throws IOException {
    try {
      Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path);
      Set<PosixFilePermission> forbidden =
          EnumSet.of(
              PosixFilePermission.GROUP_READ,
              PosixFilePermission.GROUP_WRITE,
              PosixFilePermission.GROUP_EXECUTE,
              PosixFilePermission.OTHERS_READ,
              PosixFilePermission.OTHERS_WRITE,
              PosixFilePermission.OTHERS_EXECUTE);
      if (!java.util.Collections.disjoint(permissions, forbidden)) {
        throw new ProductFlowException("SecretRef permissions are too broad");
      }
    } catch (UnsupportedOperationException ignored) {
      // Regular-file and no-symlink checks remain binding on non-POSIX file systems.
    }
  }

  private static void setPrivatePermissions(Path path) throws IOException {
    try {
      Files.setPosixFilePermissions(path, OWNER_FILE_PERMISSIONS);
    } catch (UnsupportedOperationException ignored) {
      // The test runner still writes into its private isolated evidence directory.
    }
  }

  private static void sleep() {
    try {
      Thread.sleep(1_000);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new ProductFlowException("identity convergence wait was interrupted", interrupted);
    }
  }

  private static String safeMessage(String message) {
    if (message == null || message.isBlank()) {
      return "unspecified-failure";
    }
    return message
        .replaceAll("https?://\\S+", "[uri-redacted]")
        .replaceAll("(?i)bearer\\s+\\S+", "bearer [redacted]")
        .replaceAll("(?i)(token|password|assertion)=\\S+", "$1=[redacted]");
  }

  private record ProviderSelection(String category, String providerKey) {}
}

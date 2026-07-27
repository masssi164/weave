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
    String ownerEmail = environment.ownerEmail();
    String memberEmail = environment.memberEmail();
    String proofFile =
        "weave-e2e-" + Hashing.sha256(environment.runId()).substring(0, 16) + ".txt";
    boolean fileCreated = false;
    OidcBrowserJourney.TokenSet memberSession = null;
    OidcBrowserJourney.TokenSet adminSession = null;
    String personRef = null;
    JsonNode startedRuntime = null;
    WorkloadMcpJourney.McpProof mcpProof = null;
    boolean revocationDenied = false;

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
              ownerPassword);
      validateHumanBootstrapToken(browser.jwtPayload(ownerSession.accessToken()), "weave-app");
      ownerSession =
          reconcileIdentitySession(
              browser, ownerSession, "owner", ownerEmail, ownerPassword);
      ownerSession = awaitAuthority(browser, ownerSession, "/owners", "owner");
      validateHumanWorkspaceToken(browser.jwtPayload(ownerSession.accessToken()), "weave-app");

      Instant memberInvitedAt = Instant.now();
      inviteMember(organizationId, memberEmail, ownerSession.accessToken());
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
              memberPassword);
      validateHumanBootstrapToken(browser.jwtPayload(memberSession.accessToken()), "weave-app");
      memberSession =
          reconcileIdentitySession(
              browser, memberSession, "member", memberEmail, memberPassword);
      assignWeaverEntitlement(
          organizationId, memberEmail, ownerSession.accessToken());
      memberSession =
          awaitAuthority(
              browser, memberSession, "/capabilities/weaver", "agent-runtime.entitled");
      validateHumanWorkspaceToken(browser.jwtPayload(memberSession.accessToken()), "weave-app");
      proveMemberApi(memberSession.accessToken());

      adminSession =
          browser.authorize(
              "weave-admin-console",
              URI.create("http://localhost:5173/e2e/oauth/callback"),
              List.of("openid", "profile", "email", "agent-runtime.admin"),
              ownerEmail,
              ownerPassword);
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

      revokeRuntime(personRef, adminSession.accessToken(), startedRuntime);
      try {
        new WorkloadMcpJourney(environment, http)
            .invokeFilesSearch(requiredText(startedRuntime, "cellRef"), proofFile);
      } catch (ProductFlowException expectedDenial) {
        revocationDenied = true;
      }
      if (!revocationDenied) {
        throw new ProductFlowException("revoked cell remained able to invoke MCP");
      }

      writeEvidence(
          startedAt,
          ownerEmail,
          memberEmail,
          requiredText(startedRuntime, "cellRef"),
          mcpProof,
          revocationDenied);
    } finally {
      if (fileCreated && memberSession != null) {
        deleteProofFile(proofFile, memberSession.accessToken());
      }
      // Avoid retaining references longer than the single bounded JVM run.
      ownerPassword = "";
      memberPassword = "";
      memberSession = null;
      adminSession = null;
      personRef = null;
      startedRuntime = null;
      mcpProof = null;
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

  private void inviteMember(String organizationId, String email, String accessToken) {
    ObjectNode request = http.mapper().createObjectNode();
    request.put("email", email);
    request.put("displayName", "Weave E2E Member");
    request.put("role", "member");
    JsonNode invitation =
        http.json(
            "invite entitled member through Weave",
            "POST",
            environment.api(
                "/api/admin/organizations/"
                    + encodeSegment(organizationId)
                    + "/invitations"),
            bearer(
                accessToken,
                Map.of("Idempotency-Key", "test-app-member-" + runHash())),
            request,
            Set.of(201));
    if (!"member".equals(invitation.path("requestedRole").asString())
        || invitation.has("capabilities")) {
      throw new ProductFlowException("member invitation projection is invalid");
    }
  }

  private void assignWeaverEntitlement(
      String organizationId, String email, String accessToken) {
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
    request.put("entitled", true);
    JsonNode updated =
        http.json(
            "assign native Weaver organization capability",
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
                    "Idempotency-Key", "test-app-weaver-" + runHash())),
            request,
            Set.of(200));
    if (!strings(updated.path("capabilities")).equals(Set.of("agent-runtime.entitled"))) {
      throw new ProductFlowException("native Weaver capability assignment did not converge");
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
      observedRoles =
          strings(claims.path("resource_access").path("weave-app").path("roles"));
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
        password);
  }

  private void validateAdminToken(JsonNode claims) {
    Set<String> audiences = strings(claims.path("aud"));
    Set<String> scopes = tokenScopes(claims);
    if (!"weave-admin-console".equals(claims.path("azp").asString())
        || !audiences.equals(Set.of(environment.apiOrigin().resolve("/api").toString()))
        || !hasExactWorkspaceScope(scopes)
        || !scopes.contains("agent-runtime.admin")
        || !organizationGroups(claims).contains("/owners")) {
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
    if (workspaceAccessExpected && !hasExactWorkspaceScope(scopes)) {
      invalidClaims.add("workspace-scope");
    }
    if (!workspaceAccessExpected && scopes.contains("weave:workspace")) {
      invalidClaims.add("premature-workspace-scope");
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
    JsonNode response =
        http.json(
            "provision Agent Runtime cell",
            "POST",
            runtimeUri(personRef, "/provision"),
            bearer(token, Map.of("Idempotency-Key", "test-app-provision-" + runHash())),
            null,
            Set.of(202));
    if (!personRef.equals(response.path("personRef").asString())
        || !"entitled".equals(response.path("entitlementState").asString())) {
      throw new ProductFlowException("ARC provision projection is not entitled");
    }
    return response;
  }

  private JsonNode startRuntime(String personRef, String token, JsonNode provisioned) {
    JsonNode response =
        http.json(
            "start Agent Runtime cell",
            "POST",
            runtimeUri(personRef, "/start"),
            bearer(token, Map.of("Idempotency-Key", "test-app-start-" + runHash())),
            null,
            Set.of(202));
    if (!requiredText(provisioned, "cellRef").equals(response.path("cellRef").asString())
        || response.path("runtimeProfileRef").asString("").isBlank()) {
      throw new ProductFlowException("ARC start did not issue the current RuntimeProfile");
    }
    return response;
  }

  private void revokeRuntime(String personRef, String token, JsonNode current) {
    ObjectNode request = http.mapper().createObjectNode();
    request.put("reason", "isolated testApp cleanup proof");
    request.put("entitlementRevision", requiredText(current, "entitlementRevision"));
    JsonNode response =
        http.json(
            "revoke Agent Runtime cell",
            "POST",
            runtimeUri(personRef, "/revoke"),
            bearer(token, Map.of("Idempotency-Key", "test-app-revoke-" + runHash())),
            request,
            Set.of(202));
    if (!"revoked".equals(response.path("entitlementState").asString())) {
      throw new ProductFlowException("ARC revocation did not converge");
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
      String cellRef,
      WorkloadMcpJourney.McpProof mcpProof,
      boolean revocationDenied) {
    ObjectNode evidence = http.mapper().createObjectNode();
    evidence.put("schemaVersion", "weave.test-app-product-flow/v1");
    evidence.put("startedAt", startedAt.toString());
    evidence.put("completedAt", Instant.now().toString());
    evidence.put("runIdSha256", Hashing.sha256(environment.runId()));
    evidence.put("ownerEmailSha256", Hashing.sha256(ownerEmail));
    evidence.put("memberEmailSha256", Hashing.sha256(memberEmail));
    evidence.put("cellRefSha256", Hashing.sha256(cellRef));
    evidence.put("activation", "keycloak-required-actions-real-chromium");
    evidence.put("humanOAuth", "authorization_code_pkce_s256");
    evidence.put("workloadOAuth", "client_credentials_private_key_jwt");
    evidence.put("mcpTool", mcpProof.toolName());
    evidence.put("serverProjection", mcpProof.serverProjection());
    evidence.put("canonicalResourceSeen", mcpProof.canonicalResourceSeen());
    evidence.put("revocationDenied", revocationDenied);
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
    JsonNode organizations = claims.path("organization");
    if (!organizations.isObject() || organizations.size() != 1) {
      return Set.of();
    }
    JsonNode selected = null;
    for (JsonNode value : organizations.values()) {
      selected = value;
    }
    return selected == null ? Set.of() : strings(selected.path("groups"));
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
}

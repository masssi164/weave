package com.massimotter.weave.e2e;

import com.massimotter.weave.adminapi.api.AdminWorkspaceApi;
import com.massimotter.weave.adminapi.api.OrganizationMembersApi;
import com.massimotter.weave.adminapi.model.OrganizationMemberResponse;
import com.massimotter.weave.userapi.api.FilesUserApi;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Real session-only revocation, distinct from membership/capability removal. */
final class MemberSessionRevocationJourney {
  private final ProductFlowEnvironment environment;
  private final JsonHttpClient http;
  private final FilesUserApi files;
  private final OrganizationMembersApi members;
  private final AdminWorkspaceApi admin;

  MemberSessionRevocationJourney(ProductFlowEnvironment environment, JsonHttpClient http) {
    this.environment = environment;
    this.http = http;
    var userClient = new com.massimotter.weave.userapi.invoker.ApiClient()
        .setHttpClientBuilder(clientBuilder()).setReadTimeout(Duration.ofSeconds(30));
    var adminClient = new com.massimotter.weave.adminapi.invoker.ApiClient()
        .setHttpClientBuilder(clientBuilder()).setReadTimeout(Duration.ofSeconds(30));
    String origin = environment.apiOrigin().getScheme() + "://"
        + environment.apiOrigin().getRawAuthority();
    userClient.updateBaseUri(origin);
    adminClient.updateBaseUri(origin);
    files = new FilesUserApi(userClient);
    members = new OrganizationMembersApi(adminClient);
    admin = new AdminWorkspaceApi(adminClient);
  }

  private HttpClient.Builder clientBuilder() {
    return HttpClient.newBuilder().sslContext(JsonHttpClient.sslContext(environment.caCertificate()))
        .connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER);
  }

  void prove(OidcBrowserJourney browser, String organizationId, String email, String password) {
    var user = authorizeUser(browser, email, password, "session-revoke-user");
    var administrator = authorizeAdmin(browser, email, password, "session-revoke-admin");
    requireUnexpired(browser, user.accessToken());
    requireUnexpired(browser, administrator.accessToken());
    requireFilesAvailable(user.accessToken());
    requireAdminAvailable(administrator.accessToken());
    String matrixUser = matrixWhoami(user.accessToken());
    OrganizationMemberResponse before;
    String key = "test-app-session-revoke-" + Hashing.sha256(environment.runId()).substring(0, 20);
    try {
      var page = members.listOrganizationMembers(organizationId, null, 100,
          bearer(administrator.accessToken()));
      var matches = page.getItems().stream().filter(item -> email.equalsIgnoreCase(item.getEmail())).toList();
      if (matches.size() != 1) throw new ProductFlowException("session revoke member is ambiguous");
      before = matches.getFirst();
      var result = members.revokeOrganizationMemberSessions(organizationId, before.getMemberHandle(),
          before.getVersion(), key, bearer(administrator.accessToken()));
      if (!before.getMemberHandle().equals(result.getMemberHandle())
          || !"sessions-revoked".equals(result.getOutcome())) {
        throw new ProductFlowException("generated Admin session revoke returned an invalid result");
      }
    } catch (com.massimotter.weave.adminapi.invoker.ApiException failure) {
      throw new ProductFlowException("generated Admin session revoke failed with HTTP " + failure.getCode());
    }
    requireUnexpired(browser, user.accessToken());
    requireUnexpired(browser, administrator.accessToken());
    requireFilesDenied(user.accessToken());
    requireAdminDenied(administrator.accessToken());
    requireMatrixDenied(user.accessToken());
    browser.requireRefreshDenied(user);
    browser.requireRefreshDenied(administrator);

    // The IdP's iat has second precision; reauthorization must be issued later.
    try { Thread.sleep(1100); }
    catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      throw new ProductFlowException("session reauthorization was interrupted", failure);
    }
    var restored = authorizeUser(browser, email, password, "session-revoke-reauthorize-user");
    var restoredAdmin = authorizeAdmin(browser, email, password, "session-revoke-reauthorize-admin");
    if (!user.subject().equals(restored.subject())
        || !administrator.subject().equals(restoredAdmin.subject())
        || !matrixUser.equals(matrixWhoami(restored.accessToken()))) {
      throw new ProductFlowException("reauthorization changed the stable member identity");
    }
    requireFilesAvailable(restored.accessToken());
    requireAdminAvailable(restoredAdmin.accessToken());
    try {
      var after = members.getOrganizationMember(organizationId, before.getMemberHandle(),
          bearer(restoredAdmin.accessToken()));
      if (!Objects.equals(before.getEnabled(), after.getEnabled())
          || !Objects.equals(before.getRole(), after.getRole())
          || !Objects.equals(before.getCapabilities(), after.getCapabilities())
          || !Objects.equals(before.getVersion(), after.getVersion())) {
        throw new ProductFlowException("session-only revocation changed member authorization");
      }
      var replay = members.revokeOrganizationMemberSessions(organizationId, before.getMemberHandle(),
          before.getVersion(), key, bearer(restoredAdmin.accessToken()));
      if (!"sessions-revoked".equals(replay.getOutcome())) {
        throw new ProductFlowException("session revocation idempotent replay changed outcome");
      }
    } catch (com.massimotter.weave.adminapi.invoker.ApiException failure) {
      throw new ProductFlowException("session revocation replay failed with HTTP " + failure.getCode());
    }
    requireFilesAvailable(restored.accessToken());
    requireAdminAvailable(restoredAdmin.accessToken());
    if (!matrixUser.equals(matrixWhoami(restored.accessToken()))) {
      throw new ProductFlowException("idempotent replay revoked the newly authenticated session");
    }

    // A fresh member session has never initialized Chat or an explicit device.
    var unbound = authorizeUser(browser, email, password, "session-unbound-logout");
    requireFilesAvailable(unbound.accessToken());
    http.json("logout unbound member session", "POST", environment.api("/_matrix/client/v3/logout"),
        bearer(unbound.accessToken()), http.mapper().createObjectNode(), Set.of(200));
    requireUnexpired(browser, unbound.accessToken());
    requireFilesDenied(unbound.accessToken());
    requireMatrixDenied(unbound.accessToken());
    var refreshedRevoked = browser.refresh(unbound);
    requireFilesDenied(refreshedRevoked.accessToken());
    requireMatrixDenied(refreshedRevoked.accessToken());
    System.out.println("WEAVE_MEMBER_SESSION_REVOCATION_RESULT status=passed generatedAdmin=true "
        + "userDenied=true adminDenied=true matrixDenied=true refreshDenied=true "
        + "reauthorization=true idempotentReplay=true unboundLogoutDenied=true supportSafe=true");
  }

  private OidcBrowserJourney.TokenSet authorizeUser(OidcBrowserJourney browser,
      String email, String password, String stage) {
    return browser.authorize("weave-app", URI.create("com.massimotter.weave:/oauthredirect"),
        List.of("openid", "profile", "email"), email, password, stage);
  }

  private OidcBrowserJourney.TokenSet authorizeAdmin(OidcBrowserJourney browser,
      String email, String password, String stage) {
    return browser.authorize("weave-admin-console", environment.productOrigin().resolve("/admin-console/"),
        List.of("openid", "profile", "email", "agent-runtime.admin"), email, password, stage);
  }

  private void requireUnexpired(OidcBrowserJourney browser, String token) {
    if (browser.jwtPayload(token).path("exp").asLong() <= Instant.now().getEpochSecond()) {
      throw new ProductFlowException("revocation denial probe bearer expired before assertion");
    }
  }

  private void requireFilesAvailable(String token) {
    try {
      if (!"file:root".equals(files.listFilesItems(null, bearer(token)).getParentFileId())) {
        throw new ProductFlowException("member session returned an invalid Files root");
      }
    } catch (com.massimotter.weave.userapi.invoker.ApiException failure) {
      throw new ProductFlowException("fresh User session failed with HTTP " + failure.getCode());
    }
  }

  private void requireFilesDenied(String token) {
    try { files.listFilesItems(null, bearer(token)); }
    catch (com.massimotter.weave.userapi.invoker.ApiException denied) {
      if (denied.getCode() == 401) return;
      throw new ProductFlowException("revoked User session returned HTTP " + denied.getCode());
    }
    throw new ProductFlowException("revoked User session retained Files access");
  }

  private void requireAdminAvailable(String token) {
    try {
      if (!admin.capabilityPolicy(bearer(token)).getGrantedCapabilities()
          .contains("admin_control_plane.readiness_read")) {
        throw new ProductFlowException("fresh Admin session lacked expected authority");
      }
    } catch (com.massimotter.weave.adminapi.invoker.ApiException failure) {
      throw new ProductFlowException("fresh Admin session failed with HTTP " + failure.getCode());
    }
  }

  private void requireAdminDenied(String token) {
    try { admin.capabilityPolicy(bearer(token)); }
    catch (com.massimotter.weave.adminapi.invoker.ApiException denied) {
      if (denied.getCode() == 401) return;
      throw new ProductFlowException("revoked Admin session returned HTTP " + denied.getCode());
    }
    throw new ProductFlowException("revoked Admin session retained authority");
  }

  private String matrixWhoami(String token) {
    var identity = http.json("fresh member Matrix whoami", "GET",
        environment.api("/_matrix/client/v3/account/whoami"), bearer(token), null, Set.of(200));
    String user = identity.path("user_id").asString();
    if (!user.startsWith("@") || identity.path("device_id").asString().isBlank()) {
      throw new ProductFlowException("fresh member Matrix identity was invalid");
    }
    return user;
  }

  private void requireMatrixDenied(String token) {
    var denied = http.json("revoked member Matrix whoami", "GET",
        environment.api("/_matrix/client/v3/account/whoami"), bearer(token), null, Set.of(401));
    if (!"M_UNKNOWN_TOKEN".equals(denied.path("errcode").asString())) {
      throw new ProductFlowException("revoked Matrix session returned the wrong failure");
    }
  }

  private static Map<String, String> bearer(String token) {
    return Map.of("Authorization", "Bearer " + token);
  }
}

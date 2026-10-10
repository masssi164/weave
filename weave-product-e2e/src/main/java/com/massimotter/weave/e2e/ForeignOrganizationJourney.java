package com.massimotter.weave.e2e;

import com.massimotter.weave.e2e.GeneratedCalendarJourney.Proof;
import java.io.IOException;
import java.net.URI;
import java.util.Base64;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import tools.jackson.databind.JsonNode;

/** Real same-realm foreign organization token against primary product boundaries. */
final class ForeignOrganizationJourney {
  private final ProductFlowEnvironment environment;
  private final JsonHttpClient http;

  ForeignOrganizationJourney(ProductFlowEnvironment environment, JsonHttpClient http) {
    this.environment = environment;
    this.http = http;
  }

  void provision() {
    Path command = Path.of(System.getProperty(
        "weave.e2e.foreign-organization-fixture-command", "")).toAbsolutePath().normalize();
    if (!Files.isRegularFile(command) || Files.isSymbolicLink(command)) {
      throw new ProductFlowException("foreign organization fixture command is unavailable");
    }
    Path output = environment.evidenceFile().resolveSibling(".foreign-organization-fixture.log");
    Process process = null;
    try {
      Files.deleteIfExists(output);
      Files.createFile(output);
      Files.setPosixFilePermissions(output, PosixFilePermissions.fromString("rw-------"));
      process = new ProcessBuilder("bash", command.toString(), "e2e",
          "foreign-organization-fixture")
          .redirectErrorStream(true)
          .redirectOutput(output.toFile())
          .start();
      if (!process.waitFor(300, TimeUnit.SECONDS)) {
        throw BoundedProcessTree.terminatePreservingFailure(process, Duration.ofSeconds(10),
            new ProductFlowException("foreign organization fixture exceeded its timeout"));
      }
      String log = Files.readString(output);
      if (process.exitValue() != 0 || log.length() > 65536
          || !log.contains("WEAVE_FOREIGN_ORGANIZATION_FIXTURE_RESULT status=passed "
              + "supportSafe=true adminRetired=true")) {
        throw new ProductFlowException("foreign organization fixture failed; private log retained");
      }
      Files.delete(output);
    } catch (IOException failure) {
      throw new ProductFlowException("foreign organization fixture could not execute");
    } catch (InterruptedException failure) {
      throw BoundedProcessTree.interruptedFailure(process, Duration.ofSeconds(10),
          "foreign organization fixture was interrupted", failure);
    }
  }

  void prove(
      OidcBrowserJourney browser,
      String primaryOrganizationId,
      GeneratedFilesJourney files,
      GeneratedFilesJourney.Proof file,
      String primaryMemberToken,
      GeneratedCalendarJourney calendar,
      Proof event,
      OidcBrowserJourney.TokenSet primaryOwnerSession,
      String primaryAdminToken,
      GeneratedAdminApi admin) {
    Fixture fixture = readFixture(primaryOrganizationId);
    OidcBrowserJourney.TokenSet userSession = browser.authorize(
        "weave-app", URI.create("com.massimotter.weave:/oauthredirect"),
        List.of("openid", "profile", "email"), fixture.email(), fixture.password(),
        "foreign-organization-user");
    requireForeignClaims(browser.jwtPayload(userSession.accessToken()), fixture, "weave-app");
    OidcBrowserJourney.TokenSet refreshedOwner = browser.refresh(primaryOwnerSession);
    files.requireRootAvailable(refreshedOwner.accessToken(), "primary owner after foreign fixture");
    requireComparableUserToken(browser.jwtPayload(refreshedOwner.accessToken()),
        browser.jwtPayload(userSession.accessToken()));
    if (!jwtKeyId(refreshedOwner.accessToken()).equals(jwtKeyId(userSession.accessToken()))) {
      throw new ProductFlowException("foreign token signing key differs from refreshed owner");
    }
    files.verifyForeignOrganizationDenied(file, userSession.accessToken(), primaryMemberToken);
    calendar.verifyForeignOrganizationDenied(event, userSession.accessToken(),
        refreshedOwner.accessToken());

    JsonNode whoami = http.json(
        "foreign organization Matrix identity denial", "GET",
        environment.api("/_matrix/client/v3/account/whoami"),
        bearer(userSession.accessToken()), null, Set.of(403, 404));
    requireMatrixDenial(whoami);
    JsonNode create = http.json(
        "foreign organization Matrix mutation denial", "POST",
        environment.api("/_matrix/client/v3/createRoom"),
        bearer(userSession.accessToken()), http.mapper().createObjectNode()
            .put("name", "foreign organization must not create a primary room"),
        Set.of(403, 404));
    requireMatrixDenial(create);

    OidcBrowserJourney.TokenSet adminSession = browser.authorize(
        "weave-admin-console", environment.productOrigin().resolve("/admin-console/"),
        List.of("openid", "profile", "email"), fixture.email(), fixture.password(),
        "foreign-organization-admin");
    requireForeignClaims(browser.jwtPayload(adminSession.accessToken()), fixture,
        "weave-admin-console");
    admin.requireForeignOrganizationDenied(adminSession.accessToken());
    if (!primaryOrganizationId.equals(admin.controlPlane(primaryAdminToken).getOrganizationId())) {
      throw new ProductFlowException("foreign organization denial changed primary Admin state");
    }
    System.out.println(
        "WEAVE_FOREIGN_ORGANIZATION_DENIAL_RESULT status=passed "
            + "issuer=real user=generated admin=generated matrix=client-server "
            + "providerMutation=false supportSafe=true");
  }

  private static Map<String, String> bearer(String token) {
    return Map.of("Authorization", "Bearer " + token);
  }

  private static void requireComparableUserToken(JsonNode primary, JsonNode foreign) {
    if (!setOf(primary.path("aud")).equals(setOf(foreign.path("aud")))) {
      throw new ProductFlowException("foreign user token audience differs from the primary owner");
    }
    if (!Set.of(primary.path("scope").asString().split(" "))
        .equals(Set.of(foreign.path("scope").asString().split(" ")))) {
      throw new ProductFlowException("foreign user token scope differs from the primary owner");
    }
    if (!primary.path("iss").asString().equals(foreign.path("iss").asString())
        || primary.path("sub").asString().equals(foreign.path("sub").asString())) {
      throw new ProductFlowException("foreign user token identity is not independent");
    }
  }

  private static Set<String> setOf(JsonNode value) {
    Set<String> result = new TreeSet<>();
    if (value.isArray()) {
      for (JsonNode entry : value) result.add(entry.asString());
    } else if (value.isString()) {
      result.add(value.asString());
    }
    return result;
  }

  private String jwtKeyId(String token) {
    try {
      JsonNode header = http.mapper().readTree(
          Base64.getUrlDecoder().decode(token.split("\\.")[0]));
      String keyId = header.path("kid").asString();
      if (keyId.isBlank()) throw new ProductFlowException("OIDC signing key id missing");
      return keyId;
    } catch (RuntimeException failure) {
      throw new ProductFlowException("OIDC signing key header is invalid");
    }
  }

  private static void requireMatrixDenial(JsonNode body) {
    String error = body.path("errcode").asString();
    if (!error.matches("M_[A-Z0-9_]{1,79}") || body.has("room_id")
        || body.has("user_id")) {
      throw new ProductFlowException("foreign Matrix denial returned an invalid error");
    }
  }

  private static void requireForeignClaims(JsonNode claims, Fixture fixture, String clientId) {
    JsonNode organizations = claims.path("organization");
    if (!organizations.isObject() || organizations.size() != 1) {
      throw new ProductFlowException("foreign identity did not select exactly one organization");
    }
    JsonNode selected = organizations.path(fixture.alias());
    if (!selected.isObject() || !fixture.organizationId().equals(selected.path("id").asString())
        || !contains(selected.path("groups"), "/owners")
        || !contains(selected.path("resource_access").path("weave-app").path("roles"),
            "owner")
        || !clientId.equals(claims.path("azp").asString())) {
      throw new ProductFlowException("foreign identity lacks its independent owner authority");
    }
  }

  private static boolean contains(JsonNode array, String expected) {
    if (!array.isArray()) return false;
    for (JsonNode value : array) {
      if (expected.equals(value.asString())) return true;
    }
    return false;
  }

  private Fixture readFixture(String primaryOrganizationId) {
    Path path = environment.foreignOrganizationIdentity();
    try {
      if (Files.size(path) > 8192
          || !Files.getPosixFilePermissions(path)
              .equals(PosixFilePermissions.fromString("rw-------"))) {
        throw new ProductFlowException("foreign organization fixture is not private");
      }
      JsonNode fixture = http.mapper().readTree(Files.readAllBytes(path));
      String alias = fixture.path("organizationAlias").asString();
      String id = fixture.path("organizationId").asString();
      String email = fixture.path("email").asString();
      String password = fixture.path("password").asString();
      if (!"weave.e2e-foreign-organization-fixture/v1"
              .equals(fixture.path("schemaVersion").asString())
          || !"weave-foreign-e2e".equals(alias)
          || id.isBlank() || id.equals(primaryOrganizationId)
          || !email.matches("weave-foreign-[0-9a-f]{20}@example\\.invalid")
          || password.length() < 32
          || !environment.candidateCommit().equals(fixture.path("candidateCommit").asString())
          || !environment.composeProject().equals(fixture.path("composeProject").asString())) {
        throw new ProductFlowException("foreign organization fixture contract is invalid");
      }
      return new Fixture(alias, id, email, password);
    } catch (IOException failure) {
      throw new ProductFlowException("foreign organization fixture could not be read");
    }
  }

  private record Fixture(String alias, String organizationId, String email, String password) {}
}

package com.massimotter.weave.backend.identity.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KeycloakRealmMigrationCliTest {
  private static final String BASELINE_DIGEST = "sha256:" + "a".repeat(64);
  private static final String TARGET_REVISION = "sha256:" + "b".repeat(64);

  @TempDir Path temporary;

  @Test
  void failsClosedWithSupportSafeOutputForUnknownOrSecretBearingArguments() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ByteArrayOutputStream errors = new ByteArrayOutputStream();

    int status =
        KeycloakRealmMigrationCli.run(
            new String[] {"--client-secret=must-not-appear"},
            new PrintStream(output),
            new PrintStream(errors));

    assertThat(status).isEqualTo(2);
    assertThat(output.toString(StandardCharsets.UTF_8)).isEmpty();
    assertThat(errors.toString(StandardCharsets.UTF_8))
        .isEqualTo("keycloak-realm-migration: blocked [reason=operator-arguments-invalid]\n")
        .doesNotContain("must-not-appear")
        .doesNotContain("client-secret");
  }

  @Test
  void requiresBackupProofBeforeTokenAcquisitionOrAnyAdminRestCall() throws Exception {
    Artifact artifact = writeArtifacts();
    AtomicInteger requests = new AtomicInteger();
    HttpServer keycloak = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    keycloak.createContext(
        "/",
        exchange -> {
          requests.incrementAndGet();
          exchange.sendResponseHeaders(500, -1);
          exchange.close();
        });
    keycloak.start();
    try {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      ByteArrayOutputStream errors = new ByteArrayOutputStream();

      int status =
          KeycloakRealmMigrationCli.run(
              new String[] {
                "--artifact-root=" + temporary,
                "--manifest-digest=" + artifact.manifestDigest(),
                "--baseline-digest=" + BASELINE_DIGEST,
                "--target-revision=" + TARGET_REVISION,
                "--backup-proof-file=" + temporary.resolve("missing-backup-proof.json"),
                "--environment=dogfood",
                "--candidate-commit=" + "c".repeat(40),
                "--compose-project=weave-dogfood",
                "--keycloak-base-url=http://127.0.0.1:" + keycloak.getAddress().getPort(),
                "--bootstrap-secret-file=" + temporary.resolve("missing-secret")
              },
              new PrintStream(output),
              new PrintStream(errors));

      assertThat(status).isEqualTo(2);
      assertThat(output.toString(StandardCharsets.UTF_8)).isEmpty();
      assertThat(errors.toString(StandardCharsets.UTF_8))
          .isEqualTo("keycloak-realm-migration: blocked [reason=backup-proof-unavailable]\n");
      assertThat(requests).hasValue(0);
    } finally {
      keycloak.stop(0);
    }
  }

  private Artifact writeArtifacts() throws Exception {
    String bundle =
        """
        {
          "apiVersion": "weave.keycloak-realm-migration-bundle/v1",
          "applicability": "after-fresh-start-realm-import",
          "baselineArtifactDigest": "%s",
          "containsSecretValues": false,
          "fromBaselineRevision": null,
          "keycloakVersion": "26.7.1",
          "operations": [
            {
              "blockedBy": "keycloak-26.7-imports-client-authorization-before-organizations",
              "desiredStateDigest": "%s",
              "desiredStatePointer": "/fineGrainedAdminPermissions",
              "id": "fgap-v2-primary-organization-post-import",
              "phase": "post-realm-import",
              "status": "requires-qualified-admin-rest-executor",
              "type": "keycloak-fgap-v2"
            }
          ],
          "reason": "Keycloak 26.7 cannot import a specific-organization FGAP permission in the same RealmRepresentation because authorization settings are processed before organizations. The baseline remains default-deny; an exact post-import Admin REST executor is required.",
          "status": "blocked-post-import-operation",
          "toBaselineRevision": "%s"
        }
        """
            .formatted(
                BASELINE_DIGEST,
                KeycloakFgapMigrationContract.DESIRED_STATE_DIGEST,
                TARGET_REVISION);
    Path bundlePath = temporary.resolve(KeycloakFgapMigrationContract.BUNDLE_PATH);
    Files.createDirectories(bundlePath.getParent());
    Files.writeString(bundlePath, bundle, StandardCharsets.UTF_8);
    String bundleDigest = digest(bundle.getBytes(StandardCharsets.UTF_8));
    String manifest =
        """
        {
          "baselineArtifactDigest": "%s",
          "bundles": [
            {
              "digest": "%s",
              "path": "keycloak/migrations/fresh-start-v1.json"
            }
          ],
          "containsSecretValues": false,
          "schemaVersion": "weave.keycloak-realm-migration-manifest/v1"
        }
        """
            .formatted(BASELINE_DIGEST, bundleDigest);
    Files.writeString(
        temporary.resolve("keycloak/migrations/manifest.json"),
        manifest,
        StandardCharsets.UTF_8);
    return new Artifact(digest(manifest.getBytes(StandardCharsets.UTF_8)));
  }

  private static String digest(byte[] value) throws Exception {
    return "sha256:"
        + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
  }

  private record Artifact(String manifestDigest) {}
}

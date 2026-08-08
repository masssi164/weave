package com.massimotter.weave.backend.identity.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KeycloakRealmMigrationManifestReaderTest {
  private static final String BASELINE_DIGEST =
      "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
  private static final String TARGET_REVISION =
      "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

  @TempDir Path temporary;

  private final ObjectMapper mapper =
      tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build();

  @Test
  void acceptsOnlyTheExactDigestBoundDeferredFgapOperation() throws Exception {
    Artifact artifact = writeArtifacts(validBundle());

    KeycloakRealmMigrationManifestReader.MigrationBundle result =
        new KeycloakRealmMigrationManifestReader(mapper)
            .read(temporary, artifact.manifestDigest(), BASELINE_DIGEST, TARGET_REVISION);

    assertThat(result.bundleDigest()).isEqualTo(artifact.bundleDigest());
    assertThat(result.baselineArtifactDigest()).isEqualTo(BASELINE_DIGEST);
    assertThat(result.targetBaselineRevision()).isEqualTo(TARGET_REVISION);
    assertThat(result.operationId()).isEqualTo(KeycloakFgapMigrationContract.OPERATION_ID);
  }

  @Test
  void rejectsAReviewedManifestWhenTheBundleChangesAfterReview() throws Exception {
    Artifact artifact = writeArtifacts(validBundle());
    Files.writeString(
        temporary.resolve(KeycloakFgapMigrationContract.BUNDLE_PATH),
        validBundle() + " ");

    assertThatThrownBy(
            () ->
                new KeycloakRealmMigrationManifestReader(mapper)
                    .read(
                        temporary,
                        artifact.manifestDigest(),
                        BASELINE_DIGEST,
                        TARGET_REVISION))
        .isInstanceOf(KeycloakRealmMigrationException.class)
        .hasMessage("bundle-digest-mismatch");
  }

  @Test
  void rejectsExtraOperationsEvenWhenEveryFileDigestIsRecomputed() throws Exception {
    String changed =
        validBundle()
            .replace(
                "\n  ],\n  \"reason\"",
                ",\n    {\"id\":\"unreviewed-operation\"}\n  ],\n  \"reason\"");
    Artifact artifact = writeArtifacts(changed);

    assertThatThrownBy(
            () ->
                new KeycloakRealmMigrationManifestReader(mapper)
                    .read(
                        temporary,
                        artifact.manifestDigest(),
                        BASELINE_DIGEST,
                        TARGET_REVISION))
        .isInstanceOf(KeycloakRealmMigrationException.class)
        .hasMessage("bundle-operation-count-invalid");
  }

  @Test
  void rejectsSymlinkedMigrationArtifacts() throws Exception {
    Artifact artifact = writeArtifacts(validBundle());
    Path bundle = temporary.resolve(KeycloakFgapMigrationContract.BUNDLE_PATH);
    Path replacement = temporary.resolve("replacement.json");
    Files.move(bundle, replacement);
    Files.createSymbolicLink(bundle, replacement);

    assertThatThrownBy(
            () ->
                new KeycloakRealmMigrationManifestReader(mapper)
                    .read(
                        temporary,
                        artifact.manifestDigest(),
                        BASELINE_DIGEST,
                        TARGET_REVISION))
        .isInstanceOf(KeycloakRealmMigrationException.class)
        .hasMessage("bundle-unavailable");
  }

  private Artifact writeArtifacts(String bundle) throws Exception {
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
    Path manifestPath = temporary.resolve("keycloak/migrations/manifest.json");
    Files.writeString(manifestPath, manifest, StandardCharsets.UTF_8);
    return new Artifact(digest(manifest.getBytes(StandardCharsets.UTF_8)), bundleDigest);
  }

  private static String validBundle() {
    return """
        {
          "apiVersion": "weave.keycloak-realm-migration-bundle/v1",
          "applicability": "after-fresh-start-realm-import",
          "baselineArtifactDigest": "%s",
          "containsSecretValues": false,
          "fromBaselineRevision": null,
          "keycloakVersion": "26.7.0",
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
  }

  private static String digest(byte[] value) throws Exception {
    return "sha256:"
        + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
  }

  private record Artifact(String manifestDigest, String bundleDigest) {}
}

package com.massimotter.weave.backend.identity.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class KeycloakRealmMigrationManifestReaderTest {
  private static final String BASELINE_DIGEST =
      "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
  private static final String TARGET_REVISION =
      "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
  private static final String SEMANTIC_SOURCE_DIGEST =
      "sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc";
  private static final String MIGRATION_DEFINITION_DIGEST =
      "sha256:dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd";
  private static final String DESIRED_STATE_DIGEST =
      "sha256:4c08fafc5467fe2f8f521cfd31e09a40bd3fef034b93bbff43098d363f9ac57a";

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
    assertThat(result.semanticRealmSourceDigest()).isEqualTo(SEMANTIC_SOURCE_DIGEST);
    assertThat(result.migrationDefinitionDigest()).isEqualTo(MIGRATION_DEFINITION_DIGEST);
  }

  @Test
  void rejectsAReviewedManifestWhenTheBundleChangesAfterReview() throws Exception {
    Artifact artifact = writeArtifacts(validBundle());
    Files.writeString(
        temporary.resolve(KeycloakFgapMigrationContract.BUNDLE_PATH), validBundle() + " ");

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
  void rejectsMalformedSemanticIdentityEvenWhenManifestDigestIsRecomputed() throws Exception {
    Artifact artifact = writeArtifacts(validBundle(), "sha256:not-a-digest");

    assertThatThrownBy(
            () ->
                new KeycloakRealmMigrationManifestReader(mapper)
                    .read(
                        temporary,
                        artifact.manifestDigest(),
                        BASELINE_DIGEST,
                        TARGET_REVISION))
        .isInstanceOf(KeycloakRealmMigrationException.class)
        .hasMessage("semantic-source-digest-invalid");
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
  void acceptsTheCurrentManifestBoundDesiredStateDigest() throws Exception {
    Artifact artifact = writeArtifacts(validBundle("sha256:" + "e".repeat(64)));

    KeycloakRealmMigrationManifestReader.MigrationBundle result =
        new KeycloakRealmMigrationManifestReader(mapper)
            .read(temporary, artifact.manifestDigest(), BASELINE_DIGEST, TARGET_REVISION);

    assertThat(result.bundleDigest()).isEqualTo(artifact.bundleDigest());
  }

  @Test
  void rejectsMalformedDesiredStateDigestEvenWhenFileDigestsAreRecomputed() throws Exception {
    Artifact artifact = writeArtifacts(validBundle("not-a-digest"));

    assertThatThrownBy(
            () ->
                new KeycloakRealmMigrationManifestReader(mapper)
                    .read(
                        temporary,
                        artifact.manifestDigest(),
                        BASELINE_DIGEST,
                        TARGET_REVISION))
        .isInstanceOf(KeycloakRealmMigrationException.class)
        .hasMessage("bundle-operation-desired-state-digest-invalid");
  }

  @ParameterizedTest
  @CsvSource({"clientScopeName,other", "name,other", "protocol,saml", "protocolMapper,other"})
  void rejectsChangedNativeMapperTargetEvenWithRecomputedArtifactDigests(String field, String value)
      throws Exception {
    var bundle = (tools.jackson.databind.node.ObjectNode) mapper.readTree(validBundle());
    var state = (tools.jackson.databind.node.ObjectNode) bundle.path("operations").get(1).path("desiredState");
    state.put(field, value);
    Artifact artifact = writeArtifacts(mapper.writeValueAsString(bundle));
    assertThatThrownBy(() -> new KeycloakRealmMigrationManifestReader(mapper)
        .read(temporary, artifact.manifestDigest(), BASELINE_DIGEST, TARGET_REVISION))
        .isInstanceOf(KeycloakRealmMigrationException.class)
        .hasMessage("bundle-organization-mapper-contract-mismatch");
  }

  @Test
  void rejectsNativeMapperIdOmissionEvenWithRecomputedArtifactDigests() throws Exception {
    var bundle = (tools.jackson.databind.node.ObjectNode) mapper.readTree(validBundle());
    var config = (tools.jackson.databind.node.ObjectNode) bundle.path("operations").get(1).path("desiredState").path("config");
    config.put("addOrganizationId", "false");
    Artifact artifact = writeArtifacts(mapper.writeValueAsString(bundle));
    assertThatThrownBy(() -> new KeycloakRealmMigrationManifestReader(mapper)
        .read(temporary, artifact.manifestDigest(), BASELINE_DIGEST, TARGET_REVISION))
        .isInstanceOf(KeycloakRealmMigrationException.class)
        .hasMessage("bundle-organization-mapper-contract-mismatch");
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
    return writeArtifacts(bundle, SEMANTIC_SOURCE_DIGEST);
  }

  private Artifact writeArtifacts(String bundle, String semanticDigest) throws Exception {
    Path bundlePath = temporary.resolve(KeycloakFgapMigrationContract.BUNDLE_PATH);
    Files.createDirectories(bundlePath.getParent());
    Files.writeString(bundlePath, bundle, StandardCharsets.UTF_8);
    String bundleDigest = digest(bundle.getBytes(StandardCharsets.UTF_8));
    String manifest =
        """
        {
          "bundles": [
            {
              "digest": "%s",
              "path": "keycloak/migrations/fresh-start-v1.json"
            }
          ],
          "containsSecretValues": false,
          "migrationDefinitionDigest": "%s",
          "renderedRealmDigest": "%s",
          "schemaVersion": "weave.keycloak-realm-migration-manifest/v2",
          "semanticRealmSourceDigest": "%s"
        }
        """
            .formatted(
                bundleDigest,
                MIGRATION_DEFINITION_DIGEST,
                BASELINE_DIGEST,
                semanticDigest);
    Path manifestPath = temporary.resolve("keycloak/migrations/manifest.json");
    Files.writeString(manifestPath, manifest, StandardCharsets.UTF_8);
    return new Artifact(digest(manifest.getBytes(StandardCharsets.UTF_8)), bundleDigest);
  }

  private static String validBundle() {
    return validBundle(DESIRED_STATE_DIGEST);
  }

  private static String validBundle(String desiredStateDigest) {
    return """
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
            },
            {
              "id": "organization-membership-id-post-import",
              "phase": "post-realm-import",
              "type": "keycloak-built-in-organization-mapper",
              "desiredStatePointer": "/operations/1/desiredState",
              "desiredState": {
                "clientScopeName": "organization",
                "name": "organization",
                "protocol": "openid-connect",
                "protocolMapper": "oidc-organization-membership-mapper",
                "config": {
                  "claim.name": "organization",
                  "jsonType.label": "JSON",
                  "multivalued": "true",
                  "addOrganizationId": "true",
                  "addOrganizationAttributes": "false",
                  "addOrganizationDomain": "false",
                  "access.token.claim": "true",
                  "id.token.claim": "true",
                  "userinfo.token.claim": "false",
                  "introspection.token.claim": "true"
                }
              },
              "desiredStateDigest": "sha256:5e19f3dff3818e8093aff5c7b61dc6da6baf303bf25654d24b602c6526bee475",
              "blockedBy": "keycloak-26.7-creates-built-in-scopes-before-realm-import",
              "status": "requires-qualified-admin-rest-executor"
            }
          ],
          "reason": "Keycloak 26.7 cannot import a specific-organization FGAP permission in the same RealmRepresentation because authorization settings are processed before organizations. The baseline remains default-deny; an exact post-import Admin REST executor is required. The built-in organization scope is created before import; its membership mapper requires a separate post-import update to include the native organization ID.",
          "status": "blocked-post-import-operation",
          "toBaselineRevision": "%s"
        }
        """
        .formatted(
            BASELINE_DIGEST,
            desiredStateDigest,
            TARGET_REVISION);
  }

  private static String digest(byte[] value) throws Exception {
    return "sha256:"
        + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
  }

  private record Artifact(String manifestDigest, String bundleDigest) {}
}

package com.massimotter.weave.backend.identity.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class KeycloakRealmMigrationReceiptVerifierTest {
  private static final String BASELINE_DIGEST =
      "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
  private static final String TARGET_REVISION =
      "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
  private static final String CANDIDATE = "c".repeat(40);
  private static final String ENVIRONMENT = "dogfood";
  private static final String COMPOSE_PROJECT = "weave-dogfood";

  @TempDir Path temporary;

  private final ObjectMapper mapper =
      tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build();

  @Test
  void verifiesTheExactReceiptAgainstTheReviewedManifestAndBundle() throws Exception {
    Artifact artifact = writeArtifacts();
    writeReceipt(artifact, List.of("create-primary-organization-permission"));
    KeycloakRealmMigrationManifestReader.MigrationBundle bundle = readBundle(artifact);

    new KeycloakRealmMigrationReceiptVerifier(mapper)
        .verify(temporary, bundle, readBackupProof(artifact, bundle));
  }

  @Test
  void exposesAnOfflineSecretFreeComposeGate() throws Exception {
    Artifact artifact = writeArtifacts();
    writeReceipt(artifact, List.of());
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ByteArrayOutputStream errors = new ByteArrayOutputStream();

    int status =
        KeycloakRealmMigrationReceiptVerifierCli.run(
            new String[] {
              "--artifact-root=" + temporary,
              "--manifest-digest=" + artifact.manifestDigest(),
              "--baseline-digest=" + BASELINE_DIGEST,
              "--target-revision=" + TARGET_REVISION,
              "--backup-proof-file=" + artifact.backupProofFile(),
              "--environment=" + ENVIRONMENT,
              "--candidate-commit=" + CANDIDATE,
              "--compose-project=" + COMPOSE_PROJECT
            },
            new PrintStream(output),
            new PrintStream(errors));

    assertThat(status).isZero();
    assertThat(output.toString(StandardCharsets.UTF_8))
        .isEqualTo("keycloak-realm-migration-receipt: verified\n");
    assertThat(errors.toString(StandardCharsets.UTF_8)).isEmpty();
  }

  @Test
  void rejectsAReceiptBoundToAnotherManifest() throws Exception {
    Artifact artifact = writeArtifacts();
    KeycloakFgapMigrationExecutor.MigrationResult receipt =
        receipt(
            artifact,
            List.of(),
            "sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc");
    new KeycloakRealmMigrationReceiptWriter(mapper).write(temporary, receipt);

    assertThatThrownBy(
            () ->
                new KeycloakRealmMigrationReceiptVerifier(mapper)
                    .verify(
                        temporary,
                        readBundle(artifact),
                        readBackupProof(artifact, readBundle(artifact))))
        .isInstanceOf(KeycloakRealmMigrationException.class)
        .hasMessage("migration-receipt-contract-mismatch");
  }

  @Test
  void rejectsCreateAndUpdateEvidenceForTheSameSemanticOperation() throws Exception {
    Artifact artifact = writeArtifacts();
    writeReceipt(
        artifact,
        List.of(
            "create-identity-admin-subject-policy",
            "update-identity-admin-subject-policy"));

    assertThatThrownBy(
            () ->
                new KeycloakRealmMigrationReceiptVerifier(mapper)
                    .verify(
                        temporary,
                        readBundle(artifact),
                        readBackupProof(artifact, readBundle(artifact))))
        .isInstanceOf(KeycloakRealmMigrationException.class)
        .hasMessage("migration-receipt-operations-invalid");
  }

  @Test
  void rejectsAdditionalUnreviewedReceiptFields() throws Exception {
    Artifact artifact = writeArtifacts();
    Path path = writeReceipt(artifact, List.of());
    ObjectNode changed = (ObjectNode) mapper.readTree(Files.readAllBytes(path));
    changed.put("bootstrapSecret", "must-not-be-accepted");
    Files.write(path, mapper.writeValueAsBytes(changed));

    assertThatThrownBy(
            () ->
                new KeycloakRealmMigrationReceiptVerifier(mapper)
                    .verify(
                        temporary,
                        readBundle(artifact),
                        readBackupProof(artifact, readBundle(artifact))))
        .isInstanceOf(KeycloakRealmMigrationException.class)
        .hasMessage("migration-receipt-shape-invalid");
  }

  @Test
  void rejectsStringValuesThatTryToMasqueradeAsBooleanProofs() throws Exception {
    Artifact artifact = writeArtifacts();
    Path path = writeReceipt(artifact, List.of());
    ObjectNode changed = (ObjectNode) mapper.readTree(Files.readAllBytes(path));
    changed.put("bootstrapAuthorityDeleted", "true");
    Files.write(path, mapper.writeValueAsBytes(changed));

    assertThatThrownBy(
            () ->
                new KeycloakRealmMigrationReceiptVerifier(mapper)
                    .verify(
                        temporary,
                        readBundle(artifact),
                        readBackupProof(artifact, readBundle(artifact))))
        .isInstanceOf(KeycloakRealmMigrationException.class)
        .hasMessage("migration-receipt-shape-invalid");
  }

  @Test
  void failsClosedWithoutEchoingUnknownArgumentValues() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ByteArrayOutputStream errors = new ByteArrayOutputStream();

    int status =
        KeycloakRealmMigrationReceiptVerifierCli.run(
            new String[] {"--secret=must-not-appear"},
            new PrintStream(output),
            new PrintStream(errors));

    assertThat(status).isEqualTo(2);
    assertThat(output.toString(StandardCharsets.UTF_8)).isEmpty();
    assertThat(errors.toString(StandardCharsets.UTF_8))
        .isEqualTo(
            "keycloak-realm-migration-receipt: blocked "
                + "[reason=operator-arguments-invalid]\n")
        .doesNotContain("must-not-appear")
        .doesNotContain("--secret");
  }

  private Path writeReceipt(Artifact artifact, List<String> operations) {
    return new KeycloakRealmMigrationReceiptWriter(mapper)
        .write(temporary, receipt(artifact, operations, artifact.manifestDigest()));
  }

  private static KeycloakFgapMigrationExecutor.MigrationResult receipt(
      Artifact artifact, List<String> operations, String manifestDigest) {
    return new KeycloakFgapMigrationExecutor.MigrationResult(
        KeycloakFgapMigrationContract.RESULT_SCHEMA,
        "complete",
        KeycloakFgapMigrationContract.OPERATION_ID,
        KeycloakFgapMigrationContract.KEYCLOAK_VERSION,
        manifestDigest,
        artifact.bundleDigest(),
        BASELINE_DIGEST,
        TARGET_REVISION,
        artifact.backupProofDigest(),
        operations,
        operations.size(),
        true,
        true,
        KeycloakFgapMigrationContract.BOOTSTRAP_REALM,
        KeycloakFgapMigrationContract.MIGRATION_CLIENT_ID,
        true,
        true,
        true,
        false);
  }

  private KeycloakRealmMigrationManifestReader.MigrationBundle readBundle(Artifact artifact) {
    return new KeycloakRealmMigrationManifestReader(mapper)
        .read(temporary, artifact.manifestDigest(), BASELINE_DIGEST, TARGET_REVISION);
  }

  private KeycloakRealmMigrationBackupProofReader.BackupProof readBackupProof(
      Artifact artifact, KeycloakRealmMigrationManifestReader.MigrationBundle bundle) {
    return new KeycloakRealmMigrationBackupProofReader(mapper)
        .read(artifact.backupProofFile(), bundle, ENVIRONMENT, CANDIDATE, COMPOSE_PROJECT);
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
    Path backupProofFile = temporary.resolve("backup-proof.json");
    String backupProof =
        """
        {
          "schemaVersion": "weave.keycloak-realm-migration-backup-proof/v1",
          "supportSafe": true,
          "status": "verified",
          "createdAt": "2026-08-08T10:00:00Z",
          "environment": "%s",
          "realm": "weave",
          "sourceBaselineRevision": "%s",
          "backupManifestSha256": "sha256:%s",
          "backupIdSha256": "sha256:%s",
          "candidateCommit": "%s",
          "composeProject": "%s"
        }
        """
            .formatted(
                ENVIRONMENT,
                TARGET_REVISION,
                "d".repeat(64),
                "e".repeat(64),
                CANDIDATE,
                COMPOSE_PROJECT);
    Files.writeString(backupProofFile, backupProof, StandardCharsets.UTF_8);
    if (Files.getFileStore(backupProofFile).supportsFileAttributeView("posix")) {
      Files.setPosixFilePermissions(
          backupProofFile, PosixFilePermissions.fromString("rw-------"));
    }
    return new Artifact(
        digest(manifest.getBytes(StandardCharsets.UTF_8)),
        bundleDigest,
        backupProofFile,
        digest(backupProof.getBytes(StandardCharsets.UTF_8)));
  }

  private static String digest(byte[] value) throws Exception {
    return "sha256:"
        + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
  }

  private record Artifact(
      String manifestDigest,
      String bundleDigest,
      Path backupProofFile,
      String backupProofDigest) {}
}

package com.massimotter.weave.backend.identity.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class KeycloakRealmMigrationBackupProofReaderTest {
  private static final String BASELINE_REVISION =
      "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
  private static final String CANDIDATE = "b".repeat(40);

  @TempDir Path temporary;

  private final ObjectMapper mapper =
      tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build();

  @Test
  void acceptsOnlyThePrivateSupportSafeBackupProofAndHashesItsExactBytes() throws Exception {
    Path proof = writeProof(validBackupProof());
    String expectedDigest = digest(proof);

    KeycloakRealmMigrationBackupProofReader.BackupProof result =
        reader().read(proof, bundle(), "dogfood", CANDIDATE, "weave-dogfood");

    assertThat(result.digest()).isEqualTo(expectedDigest);
    assertThat(result.environment()).isEqualTo("dogfood");
    assertThat(result.candidateCommit()).isEqualTo(CANDIDATE);
    assertThat(result.composeProject()).isEqualTo("weave-dogfood");
  }

  @Test
  void acceptsAnApprovedFreshStartProofWithoutPretendingItIsABackup() throws Exception {
    Path proof = writeProof(validFreshStartProof());

    KeycloakRealmMigrationBackupProofReader.BackupProof result =
        reader().read(proof, bundle(), "dogfood", CANDIDATE, "weave-dogfood");

    assertThat(result.digest()).isEqualTo(digest(proof));
  }

  @Test
  void rejectsFreshStartProofForProductionOrWrongCandidate() throws Exception {
    Path proof = writeProof(validFreshStartProof());

    assertThatThrownBy(() -> reader().read(proof, bundle(), "prod", CANDIDATE, "weave-dogfood"))
        .isInstanceOf(KeycloakRealmMigrationException.class)
        .hasMessage("fresh-start-proof-contract-mismatch");
    assertThatThrownBy(
            () -> reader().read(proof, bundle(), "dogfood", "c".repeat(40), "weave-dogfood"))
        .isInstanceOf(KeycloakRealmMigrationException.class)
        .hasMessage("fresh-start-proof-contract-mismatch");
  }

  @Test
  void rejectsMissingAndSymlinkedProofs() throws Exception {
    Path missing = temporary.resolve("missing-proof.json");

    assertThatThrownBy(
            () -> reader().read(missing, bundle(), "dogfood", CANDIDATE, "weave-dogfood"))
        .isInstanceOf(KeycloakRealmMigrationException.class)
        .hasMessage("backup-proof-unavailable");

    Path proof = writeProof(validBackupProof());
    Path symlink = temporary.resolve("proof-link.json");
    Files.createSymbolicLink(symlink, proof);
    assertThatThrownBy(
            () -> reader().read(symlink, bundle(), "dogfood", CANDIDATE, "weave-dogfood"))
        .isInstanceOf(KeycloakRealmMigrationException.class)
        .hasMessage("backup-proof-unavailable");
  }

  @Test
  void rejectsAnyGroupOrOtherFileAccess() throws Exception {
    Path proof = writeProof(validBackupProof());
    assumeTrue(Files.getFileStore(proof).supportsFileAttributeView("posix"));
    Files.setPosixFilePermissions(proof, PosixFilePermissions.fromString("rw-r-----"));

    assertThatThrownBy(
            () -> reader().read(proof, bundle(), "dogfood", CANDIDATE, "weave-dogfood"))
        .isInstanceOf(KeycloakRealmMigrationException.class)
        .hasMessage("backup-proof-permissions-invalid");
  }

  @Test
  void rejectsSecretLikeOrOtherUnreviewedFields() throws Exception {
    ObjectNode proof = validBackupProof();
    proof.put("clientSecret", "must-never-enter-a-support-safe-proof");
    Path path = writeProof(proof);

    assertThatThrownBy(
            () -> reader().read(path, bundle(), "dogfood", CANDIDATE, "weave-dogfood"))
        .isInstanceOf(KeycloakRealmMigrationException.class)
        .hasMessage("backup-proof-shape-invalid")
        .hasMessageNotContaining("must-never-enter");
  }

  @Test
  void rejectsBaselineEnvironmentAndRealmMismatches() throws Exception {
    assertContractMismatch("sourceBaselineRevision", "sha256:" + "c".repeat(64));
    assertContractMismatch("environment", "prod");
    assertContractMismatch("realm", "master");
  }

  @Test
  void rejectsAProofFromAnotherCandidateOrComposeProject() throws Exception {
    Path proof = writeProof(validBackupProof());

    assertThatThrownBy(
            () -> reader().read(proof, bundle(), "dogfood", "c".repeat(40), "weave-dogfood"))
        .isInstanceOf(KeycloakRealmMigrationException.class)
        .hasMessage("backup-proof-contract-mismatch");
    assertThatThrownBy(
            () -> reader().read(proof, bundle(), "dogfood", CANDIDATE, "weave-prod"))
        .isInstanceOf(KeycloakRealmMigrationException.class)
        .hasMessage("backup-proof-contract-mismatch");
  }

  private void assertContractMismatch(String field, String value) throws Exception {
    ObjectNode proof = validBackupProof();
    proof.put(field, value);
    Path path = temporary.resolve(field + ".json");
    writeProof(path, proof);
    assertThatThrownBy(
            () -> reader().read(path, bundle(), "dogfood", CANDIDATE, "weave-dogfood"))
        .as(field)
        .isInstanceOf(KeycloakRealmMigrationException.class)
        .hasMessage("backup-proof-contract-mismatch");
  }

  private KeycloakRealmMigrationBackupProofReader reader() {
    return new KeycloakRealmMigrationBackupProofReader(mapper);
  }

  private static KeycloakRealmMigrationManifestReader.MigrationBundle bundle() {
    return new KeycloakRealmMigrationManifestReader.MigrationBundle(
        "sha256:" + "1".repeat(64),
        "sha256:" + "2".repeat(64),
        "sha256:" + "3".repeat(64),
        BASELINE_REVISION,
        KeycloakFgapMigrationContract.OPERATION_ID,
        "sha256:" + "4".repeat(64),
        "sha256:" + "5".repeat(64));
  }

  private ObjectNode validBackupProof() {
    ObjectNode proof = mapper.createObjectNode();
    proof.put("schemaVersion", "weave.keycloak-realm-migration-backup-proof/v1");
    proof.put("supportSafe", true);
    proof.put("status", "verified");
    proof.put("createdAt", "2026-08-08T10:00:00Z");
    proof.put("environment", "dogfood");
    proof.put("realm", "weave");
    proof.put("sourceBaselineRevision", BASELINE_REVISION);
    proof.put("backupManifestSha256", "sha256:" + "d".repeat(64));
    proof.put("backupIdSha256", "sha256:" + "e".repeat(64));
    proof.put("candidateCommit", CANDIDATE);
    proof.put("composeProject", "weave-dogfood");
    return proof;
  }

  private ObjectNode validFreshStartProof() {
    ObjectNode proof = mapper.createObjectNode();
    proof.put("schemaVersion", "weave.keycloak-realm-migration-fresh-start-proof/v1");
    proof.put("supportSafe", true);
    proof.put("containsSecretValues", false);
    proof.put("status", "verified");
    proof.put("environment", "dogfood");
    proof.put("realm", "weave");
    proof.put("sourceBaselineRevision", BASELINE_REVISION);
    proof.put("freshStartPlanSha256", "sha256:" + "d".repeat(64));
    proof.put("freshStartApplyEvidenceSha256", "sha256:" + "e".repeat(64));
    proof.put("operationNonce", "fresh-start-0123456789");
    proof.put("retiredGeneration", "legacy-generation");
    proof.put("targetGeneration", "fresh-generation");
    proof.put("candidateCommit", CANDIDATE);
    proof.put("candidateManifestDigest", "sha256:" + "f".repeat(64));
    proof.put("composeProject", "weave-dogfood");
    return proof;
  }

  private Path writeProof(ObjectNode proof) throws Exception {
    return writeProof(temporary.resolve("migration-proof.json"), proof);
  }

  private Path writeProof(Path path, ObjectNode proof) throws Exception {
    Files.writeString(
        path,
        mapper.writerWithDefaultPrettyPrinter().writeValueAsString(proof) + "\n",
        StandardCharsets.UTF_8);
    if (Files.getFileStore(path).supportsFileAttributeView("posix")) {
      Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
    }
    return path;
  }

  private static String digest(Path proof) throws Exception {
    return "sha256:"
        + HexFormat.of()
            .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(proof)));
  }
}

package com.massimotter.weave.backend.identity.migration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Consumes the support-safe precondition proof for the bounded static-IAM migration.
 *
 * <p>The historical class/record name remains internal-only. A persistent non-empty realm must
 * provide the verified private-backup proof. A Fresh Start dogfood cut may instead provide the
 * exact retirement proof generated from the approved Fresh Start plan and apply evidence. Neither
 * proof contains credentials or grants general reconciliation authority.
 */
final class KeycloakRealmMigrationBackupProofReader {
  private static final String BACKUP_SCHEMA = "weave.keycloak-realm-migration-backup-proof/v1";
  private static final String FRESH_START_SCHEMA =
      "weave.keycloak-realm-migration-fresh-start-proof/v1";
  private static final long MAXIMUM_PROOF_BYTES = 32 * 1024;
  private static final Pattern DIGEST = Pattern.compile("sha256:[0-9a-f]{64}");
  private static final Pattern COMMIT = Pattern.compile("[0-9a-f]{40}");
  private static final Pattern COMPOSE_PROJECT = Pattern.compile("[a-z0-9][a-z0-9_-]{1,62}");
  private static final Pattern OPERATION_NONCE = Pattern.compile("[a-z0-9][a-z0-9-]{15,63}");
  private static final Pattern GENERATION = Pattern.compile("[a-z0-9][a-z0-9._-]{2,63}");
  private static final Pattern RFC3339 =
      Pattern.compile(
          "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}"
              + "(?:\\.[0-9]{1,9})?(?:Z|[+-][0-9]{2}:[0-9]{2})");
  private static final Set<String> BACKUP_FIELDS =
      Set.of(
          "schemaVersion",
          "supportSafe",
          "status",
          "createdAt",
          "environment",
          "realm",
          "sourceBaselineRevision",
          "backupManifestSha256",
          "backupIdSha256",
          "candidateCommit",
          "composeProject");
  private static final Set<String> FRESH_START_FIELDS =
      Set.of(
          "schemaVersion",
          "supportSafe",
          "containsSecretValues",
          "status",
          "environment",
          "realm",
          "sourceBaselineRevision",
          "freshStartPlanSha256",
          "freshStartApplyEvidenceSha256",
          "operationNonce",
          "retiredGeneration",
          "targetGeneration",
          "candidateCommit",
          "candidateManifestDigest",
          "composeProject");

  private final ObjectMapper mapper;

  KeycloakRealmMigrationBackupProofReader(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  BackupProof read(
      Path proofFile,
      KeycloakRealmMigrationManifestReader.MigrationBundle bundle,
      String expectedEnvironment,
      String expectedCandidateCommit,
      String expectedComposeProject) {
    requireExpectedScope(expectedEnvironment, expectedCandidateCommit, expectedComposeProject);
    byte[] payload = readProof(proofFile);
    JsonNode proof = parse(payload);
    String schema = proof.path("schemaVersion").asString();
    if (BACKUP_SCHEMA.equals(schema)) {
      requireBackupProof(
          proof, bundle, expectedEnvironment, expectedCandidateCommit, expectedComposeProject);
    } else if (FRESH_START_SCHEMA.equals(schema)) {
      requireFreshStartProof(
          proof, bundle, expectedEnvironment, expectedCandidateCommit, expectedComposeProject);
    } else {
      throw blocked("migration-precondition-proof-schema-unsupported");
    }
    return new BackupProof(
        digest(payload), expectedEnvironment, expectedCandidateCommit, expectedComposeProject);
  }

  private static void requireBackupProof(
      JsonNode proof,
      KeycloakRealmMigrationManifestReader.MigrationBundle bundle,
      String environment,
      String candidateCommit,
      String composeProject) {
    requireExactShape(proof, BACKUP_FIELDS, Set.of("supportSafe"));
    String createdAt = proof.path("createdAt").asString();
    if (!proof.path("supportSafe").asBoolean(false)
        || !"verified".equals(proof.path("status").asString())
        || !validRfc3339(createdAt)
        || !environment.equals(proof.path("environment").asString())
        || !KeycloakFgapMigrationContract.REALM.equals(proof.path("realm").asString())
        || !bundle.currentBaselineRevision().equals(proof.path("sourceBaselineRevision").asString())
        || !DIGEST.matcher(proof.path("backupManifestSha256").asString()).matches()
        || !DIGEST.matcher(proof.path("backupIdSha256").asString()).matches()
        || !candidateCommit.equals(proof.path("candidateCommit").asString())
        || !composeProject.equals(proof.path("composeProject").asString())) {
      throw blocked("backup-proof-contract-mismatch");
    }
  }

  private static void requireFreshStartProof(
      JsonNode proof,
      KeycloakRealmMigrationManifestReader.MigrationBundle bundle,
      String environment,
      String candidateCommit,
      String composeProject) {
    requireExactShape(proof, FRESH_START_FIELDS, Set.of("supportSafe", "containsSecretValues"));
    if (!"dogfood".equals(environment)
        || !proof.path("supportSafe").asBoolean(false)
        || proof.path("containsSecretValues").asBoolean(true)
        || !"verified".equals(proof.path("status").asString())
        || !environment.equals(proof.path("environment").asString())
        || !KeycloakFgapMigrationContract.REALM.equals(proof.path("realm").asString())
        || !bundle.currentBaselineRevision().equals(proof.path("sourceBaselineRevision").asString())
        || !DIGEST.matcher(proof.path("freshStartPlanSha256").asString()).matches()
        || !DIGEST.matcher(proof.path("freshStartApplyEvidenceSha256").asString()).matches()
        || !OPERATION_NONCE.matcher(proof.path("operationNonce").asString()).matches()
        || !GENERATION.matcher(proof.path("retiredGeneration").asString()).matches()
        || !GENERATION.matcher(proof.path("targetGeneration").asString()).matches()
        || !candidateCommit.equals(proof.path("candidateCommit").asString())
        || !DIGEST.matcher(proof.path("candidateManifestDigest").asString()).matches()
        || !composeProject.equals(proof.path("composeProject").asString())) {
      throw blocked("fresh-start-proof-contract-mismatch");
    }
  }

  private static void requireExpectedScope(
      String environment, String candidateCommit, String composeProject) {
    if (!("dogfood".equals(environment) || "prod".equals(environment))
        || candidateCommit == null
        || !COMMIT.matcher(candidateCommit).matches()
        || composeProject == null
        || !COMPOSE_PROJECT.matcher(composeProject).matches()) {
      throw blocked("backup-proof-expected-scope-invalid");
    }
  }

  private static byte[] readProof(Path proofFile) {
    if (proofFile == null || !proofFile.isAbsolute()) {
      throw blocked("backup-proof-unavailable");
    }
    Path normalized = proofFile.normalize();
    try {
      if (Files.isSymbolicLink(normalized)
          || !Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)
          || Files.size(normalized) < 1
          || Files.size(normalized) > MAXIMUM_PROOF_BYTES) {
        throw blocked("backup-proof-unavailable");
      }
      requirePrivatePermissions(normalized);
      return Files.readAllBytes(normalized.toRealPath(LinkOption.NOFOLLOW_LINKS));
    } catch (KeycloakRealmMigrationException failure) {
      throw failure;
    } catch (IOException failure) {
      throw blocked("backup-proof-unavailable");
    }
  }

  private static void requirePrivatePermissions(Path proof) throws IOException {
    if (!Files.getFileStore(proof).supportsFileAttributeView("posix")) {
      throw blocked("backup-proof-permissions-unverifiable");
    }
    Set<PosixFilePermission> permissions =
        Files.getPosixFilePermissions(proof, LinkOption.NOFOLLOW_LINKS);
    if (!permissions.contains(PosixFilePermission.OWNER_READ)
        || permissions.contains(PosixFilePermission.OWNER_EXECUTE)
        || permissions.stream()
            .anyMatch(
                permission ->
                    permission.name().startsWith("GROUP_")
                        || permission.name().startsWith("OTHERS_"))) {
      throw blocked("backup-proof-permissions-invalid");
    }
  }

  private JsonNode parse(byte[] payload) {
    try {
      JsonNode value = mapper.readTree(payload);
      if (value == null || !value.isObject()) {
        throw blocked("backup-proof-invalid");
      }
      return value;
    } catch (KeycloakRealmMigrationException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw blocked("backup-proof-invalid");
    }
  }

  private static void requireExactShape(
      JsonNode proof, Set<String> fields, Set<String> booleanFields) {
    Set<String> observed =
        proof.properties().stream().map(Map.Entry::getKey).collect(Collectors.toUnmodifiableSet());
    if (!fields.equals(observed)
        || fields.stream()
            .filter(field -> !booleanFields.contains(field))
            .anyMatch(field -> !proof.path(field).isString())
        || booleanFields.stream().anyMatch(field -> !proof.path(field).isBoolean())) {
      throw blocked("backup-proof-shape-invalid");
    }
  }

  private static boolean validRfc3339(String value) {
    if (value == null || !RFC3339.matcher(value).matches()) {
      return false;
    }
    try {
      OffsetDateTime.parse(value);
      return true;
    } catch (DateTimeParseException failure) {
      return false;
    }
  }

  private static String digest(byte[] payload) {
    try {
      return "sha256:"
          + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
    } catch (NoSuchAlgorithmException failure) {
      throw blocked("digest-algorithm-unavailable");
    }
  }

  private static KeycloakRealmMigrationException blocked(String reason) {
    return new KeycloakRealmMigrationException(reason);
  }

  record BackupProof(
      String digest, String environment, String candidateCommit, String composeProject) {}
}

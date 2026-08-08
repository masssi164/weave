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

/** Consumes only a support-safe proof that infra already verified the private realm backup. */
final class KeycloakRealmMigrationBackupProofReader {
  private static final String SCHEMA = "weave.keycloak-realm-migration-backup-proof/v1";
  private static final long MAXIMUM_PROOF_BYTES = 32 * 1024;
  private static final Pattern DIGEST = Pattern.compile("sha256:[0-9a-f]{64}");
  private static final Pattern COMMIT = Pattern.compile("[0-9a-f]{40}");
  private static final Pattern COMPOSE_PROJECT =
      Pattern.compile("[a-z0-9][a-z0-9_-]{1,62}");
  private static final Pattern RFC3339 =
      Pattern.compile(
          "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}"
              + "(?:\\.[0-9]{1,9})?(?:Z|[+-][0-9]{2}:[0-9]{2})");
  private static final Set<String> FIELDS =
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
  private static final Set<String> STRING_FIELDS =
      Set.of(
          "schemaVersion",
          "status",
          "createdAt",
          "environment",
          "realm",
          "sourceBaselineRevision",
          "backupManifestSha256",
          "backupIdSha256",
          "candidateCommit",
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
    requireExactShape(proof);

    String createdAt = proof.path("createdAt").asString();
    if (!SCHEMA.equals(proof.path("schemaVersion").asString())
        || !proof.path("supportSafe").asBoolean(false)
        || !"verified".equals(proof.path("status").asString())
        || !validRfc3339(createdAt)
        || !expectedEnvironment.equals(proof.path("environment").asString())
        || !KeycloakFgapMigrationContract.REALM.equals(proof.path("realm").asString())
        || !bundle
            .currentBaselineRevision()
            .equals(proof.path("sourceBaselineRevision").asString())
        || !DIGEST.matcher(proof.path("backupManifestSha256").asString()).matches()
        || !DIGEST.matcher(proof.path("backupIdSha256").asString()).matches()
        || !expectedCandidateCommit.equals(proof.path("candidateCommit").asString())
        || !expectedComposeProject.equals(proof.path("composeProject").asString())) {
      throw blocked("backup-proof-contract-mismatch");
    }
    return new BackupProof(
        digest(payload), expectedEnvironment, expectedCandidateCommit, expectedComposeProject);
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

  private static void requireExactShape(JsonNode proof) {
    Set<String> observed =
        proof.properties().stream()
            .map(Map.Entry::getKey)
            .collect(Collectors.toUnmodifiableSet());
    if (!FIELDS.equals(observed)
        || STRING_FIELDS.stream().anyMatch(field -> !proof.path(field).isString())
        || !proof.path("supportSafe").isBoolean()) {
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

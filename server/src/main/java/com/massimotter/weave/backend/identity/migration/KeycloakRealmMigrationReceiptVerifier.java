package com.massimotter.weave.backend.identity.migration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Offline, secret-free verification of the exact completed Keycloak migration receipt. */
final class KeycloakRealmMigrationReceiptVerifier {
  private static final long MAXIMUM_RECEIPT_BYTES = 64 * 1024;
  private static final Set<String> RECEIPT_FIELDS =
      Set.of(
          "schemaVersion",
          "status",
          "operationId",
          "keycloakVersion",
          "manifestDigest",
          "bundleDigest",
          "baselineArtifactDigest",
          "targetBaselineRevision",
          "backupProofDigest",
          "firstRunOperations",
          "firstRunMutationCount",
          "semanticReadbackVerified",
          "secondRunPlanEmpty",
          "bootstrapAuthorityRealm",
          "bootstrapAuthorityClientId",
          "bootstrapAuthorityDeleted",
          "bootstrapAuthorityNegativeReadbackVerified",
          "supportSafe",
          "containsSecretValues");
  private static final Set<String> MUTATION_OPERATIONS =
      Set.of(
          "create-identity-admin-subject-policy",
          "update-identity-admin-subject-policy",
          "create-primary-organization-permission",
          "update-primary-organization-permission",
          "create-users-lifecycle-permission",
          "update-users-lifecycle-permission");

  private final ObjectMapper mapper;

  KeycloakRealmMigrationReceiptVerifier(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  void verify(
      Path artifactRoot,
      KeycloakRealmMigrationManifestReader.MigrationBundle migrationBundle,
      KeycloakRealmMigrationBackupProofReader.BackupProof backupProof) {
    JsonNode receipt = readReceipt(artifactRoot);
    requireExactFields(receipt);
    requireExactTypes(receipt);
    List<String> operations = requireOperations(receipt.path("firstRunOperations"));

    if (!KeycloakFgapMigrationContract.RESULT_SCHEMA.equals(
            receipt.path("schemaVersion").asString())
        || !"complete".equals(receipt.path("status").asString())
        || !migrationBundle.operationId().equals(receipt.path("operationId").asString())
        || !KeycloakFgapMigrationContract.KEYCLOAK_VERSION.equals(
            receipt.path("keycloakVersion").asString())
        || !migrationBundle.manifestDigest().equals(receipt.path("manifestDigest").asString())
        || !migrationBundle.bundleDigest().equals(receipt.path("bundleDigest").asString())
        || !migrationBundle
            .baselineArtifactDigest()
            .equals(receipt.path("baselineArtifactDigest").asString())
        || !migrationBundle
            .targetBaselineRevision()
            .equals(receipt.path("targetBaselineRevision").asString())
        || !backupProof.digest().equals(receipt.path("backupProofDigest").asString())
        || !receipt.path("firstRunMutationCount").isInt()
        || receipt.path("firstRunMutationCount").asInt(-1) != operations.size()
        || !receipt.path("semanticReadbackVerified").asBoolean(false)
        || !receipt.path("secondRunPlanEmpty").asBoolean(false)
        || !KeycloakFgapMigrationContract.BOOTSTRAP_REALM.equals(
            receipt.path("bootstrapAuthorityRealm").asString())
        || !KeycloakFgapMigrationContract.MIGRATION_CLIENT_ID.equals(
            receipt.path("bootstrapAuthorityClientId").asString())
        || !receipt.path("bootstrapAuthorityDeleted").asBoolean(false)
        || !receipt.path("bootstrapAuthorityNegativeReadbackVerified").asBoolean(false)
        || !receipt.path("supportSafe").asBoolean(false)
        || receipt.path("containsSecretValues").asBoolean(true)) {
      throw blocked("migration-receipt-contract-mismatch");
    }
  }

  private JsonNode readReceipt(Path artifactRoot) {
    if (artifactRoot == null || !artifactRoot.isAbsolute()) {
      throw blocked("migration-receipt-unavailable");
    }
    Path root = artifactRoot.normalize();
    Path receipt = root.resolve(KeycloakFgapMigrationContract.RECEIPT_PATH).normalize();
    try {
      if (!receipt.startsWith(root)
          || Files.isSymbolicLink(receipt)
          || !Files.isRegularFile(receipt, LinkOption.NOFOLLOW_LINKS)
          || Files.size(receipt) < 1
          || Files.size(receipt) > MAXIMUM_RECEIPT_BYTES) {
        throw blocked("migration-receipt-unavailable");
      }
      Path realRoot = root.toRealPath(LinkOption.NOFOLLOW_LINKS);
      Path realReceipt = receipt.toRealPath(LinkOption.NOFOLLOW_LINKS);
      if (!realReceipt.startsWith(realRoot)) {
        throw blocked("migration-receipt-unavailable");
      }
      requireSupportSafePermissions(realReceipt);
      JsonNode value = mapper.readTree(Files.readAllBytes(realReceipt));
      if (value == null || !value.isObject()) {
        throw blocked("migration-receipt-invalid");
      }
      return value;
    } catch (KeycloakRealmMigrationException failure) {
      throw failure;
    } catch (IOException | RuntimeException failure) {
      throw blocked("migration-receipt-invalid");
    }
  }

  private static void requireSupportSafePermissions(Path receipt) throws IOException {
    if (!Files.getFileStore(receipt).supportsFileAttributeView("posix")) {
      return;
    }
    Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(receipt);
    if (!permissions.contains(PosixFilePermission.OWNER_READ)
        || permissions.contains(PosixFilePermission.GROUP_WRITE)
        || permissions.contains(PosixFilePermission.OTHERS_WRITE)
        || permissions.stream().anyMatch(permission -> permission.name().endsWith("EXECUTE"))) {
      throw blocked("migration-receipt-permissions-invalid");
    }
  }

  private static void requireExactFields(JsonNode receipt) {
    Set<String> observed =
        receipt.properties().stream()
            .map(Map.Entry::getKey)
            .collect(Collectors.toUnmodifiableSet());
    if (!observed.equals(RECEIPT_FIELDS)) {
      throw blocked("migration-receipt-shape-invalid");
    }
  }

  private static void requireExactTypes(JsonNode receipt) {
    Set<String> stringFields =
        Set.of(
            "schemaVersion",
            "status",
            "operationId",
            "keycloakVersion",
            "manifestDigest",
            "bundleDigest",
            "baselineArtifactDigest",
            "targetBaselineRevision",
            "backupProofDigest",
            "bootstrapAuthorityRealm",
            "bootstrapAuthorityClientId");
    Set<String> booleanFields =
        Set.of(
            "semanticReadbackVerified",
            "secondRunPlanEmpty",
            "bootstrapAuthorityDeleted",
            "bootstrapAuthorityNegativeReadbackVerified",
            "supportSafe",
            "containsSecretValues");
    if (stringFields.stream().anyMatch(field -> !receipt.path(field).isString())
        || booleanFields.stream().anyMatch(field -> !receipt.path(field).isBoolean())
        || !receipt.path("firstRunOperations").isArray()
        || !receipt.path("firstRunMutationCount").isInt()) {
      throw blocked("migration-receipt-shape-invalid");
    }
  }

  private static List<String> requireOperations(JsonNode value) {
    if (!value.isArray() || value.size() > 3) {
      throw blocked("migration-receipt-operations-invalid");
    }
    List<String> operations = new ArrayList<>();
    for (JsonNode operation : value) {
      if (!operation.isString()) {
        throw blocked("migration-receipt-operations-invalid");
      }
      operations.add(operation.asString());
    }
    if (!operations.equals(operations.stream().sorted().toList())
        || operations.size() != new HashSet<>(operations).size()
        || !MUTATION_OPERATIONS.containsAll(operations)
        || operations.stream()
            .map(KeycloakRealmMigrationReceiptVerifier::semanticOperation)
            .distinct()
            .count()
            != operations.size()) {
      throw blocked("migration-receipt-operations-invalid");
    }
    return operations;
  }

  private static String semanticOperation(String operation) {
    if (operation.startsWith("create-")) {
      return operation.substring("create-".length());
    }
    if (operation.startsWith("update-")) {
      return operation.substring("update-".length());
    }
    throw blocked("migration-receipt-operations-invalid");
  }

  private static KeycloakRealmMigrationException blocked(String reason) {
    return new KeycloakRealmMigrationException(reason);
  }
}

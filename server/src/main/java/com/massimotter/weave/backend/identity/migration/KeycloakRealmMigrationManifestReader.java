package com.massimotter.weave.backend.identity.migration;

import static com.massimotter.weave.backend.identity.migration.KeycloakFgapMigrationContract.BUNDLE_PATH;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Reads the secret-free renderer manifest and its one digest-bound migration bundle. */
final class KeycloakRealmMigrationManifestReader {
  private static final long MAXIMUM_ARTIFACT_BYTES = 256 * 1024;
  private static final Pattern DIGEST = Pattern.compile("sha256:[0-9a-f]{64}");
  private static final Set<String> MANIFEST_FIELDS =
      Set.of(
          "schemaVersion",
          "semanticRealmSourceDigest",
          "migrationDefinitionDigest",
          "renderedRealmDigest",
          "bundles",
          "containsSecretValues");
  private static final Set<String> MANIFEST_BUNDLE_FIELDS = Set.of("digest", "path");
  private static final Set<String> BUNDLE_FIELDS =
      Set.of(
          "apiVersion",
          "applicability",
          "baselineArtifactDigest",
          "containsSecretValues",
          "fromBaselineRevision",
          "keycloakVersion",
          "operations",
          "reason",
          "status",
          "toBaselineRevision");
  private static final Set<String> OPERATION_FIELDS =
      Set.of(
          "blockedBy",
          "desiredStateDigest",
          "desiredStatePointer",
          "id",
          "phase",
          "status",
          "type");

  private final ObjectMapper mapper;

  KeycloakRealmMigrationManifestReader(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  MigrationBundle read(
      Path artifactRoot,
      String expectedManifestDigest,
      String expectedBaselineDigest,
      String expectedTargetRevision) {
    requireDigest(expectedManifestDigest, "invalid-manifest-digest");
    requireDigest(expectedBaselineDigest, "invalid-baseline-digest");
    requireDigest(expectedTargetRevision, "invalid-target-revision");

    Path root = requireDirectory(artifactRoot);
    Path manifestPath = root.resolve("keycloak/migrations/manifest.json").normalize();
    byte[] manifestBytes = readArtifact(root, manifestPath, "manifest-unavailable");
    requireEqualDigest(manifestBytes, expectedManifestDigest, "manifest-digest-mismatch");
    JsonNode manifest = parse(manifestBytes, "manifest-invalid");
    requireExactFields(manifest, MANIFEST_FIELDS, "manifest-shape-invalid");
    String semanticSourceDigest = manifest.path("semanticRealmSourceDigest").asString();
    String migrationDefinitionDigest = manifest.path("migrationDefinitionDigest").asString();
    requireDigest(semanticSourceDigest, "semantic-source-digest-invalid");
    requireDigest(migrationDefinitionDigest, "migration-definition-digest-invalid");
    if (!KeycloakFgapMigrationContract.MANIFEST_SCHEMA.equals(
            manifest.path("schemaVersion").asString())
        || manifest.path("containsSecretValues").asBoolean(true)
        || !expectedBaselineDigest.equals(manifest.path("renderedRealmDigest").asString())) {
      throw blocked("manifest-contract-mismatch");
    }

    JsonNode bundles = manifest.path("bundles");
    if (!bundles.isArray() || bundles.size() != 1) {
      throw blocked("manifest-bundle-count-invalid");
    }
    JsonNode bundleReference = bundles.get(0);
    requireExactFields(bundleReference, MANIFEST_BUNDLE_FIELDS, "manifest-bundle-shape-invalid");
    String bundlePathValue = bundleReference.path("path").asString();
    String bundleDigest = bundleReference.path("digest").asString();
    requireDigest(bundleDigest, "bundle-digest-invalid");
    if (!BUNDLE_PATH.equals(bundlePathValue)) {
      throw blocked("bundle-path-invalid");
    }

    Path bundlePath = root.resolve(bundlePathValue).normalize();
    byte[] bundleBytes = readArtifact(root, bundlePath, "bundle-unavailable");
    requireEqualDigest(bundleBytes, bundleDigest, "bundle-digest-mismatch");
    JsonNode bundle = parse(bundleBytes, "bundle-invalid");
    requireExactFields(bundle, BUNDLE_FIELDS, "bundle-shape-invalid");
    validateBundle(bundle, expectedBaselineDigest, expectedTargetRevision);
    return new MigrationBundle(
        expectedManifestDigest,
        bundleDigest,
        expectedBaselineDigest,
        expectedTargetRevision,
        KeycloakFgapMigrationContract.OPERATION_ID,
        semanticSourceDigest,
        migrationDefinitionDigest);
  }

  private static void validateBundle(
      JsonNode bundle, String expectedBaselineDigest, String expectedTargetRevision) {
    if (!KeycloakFgapMigrationContract.BUNDLE_SCHEMA.equals(bundle.path("apiVersion").asString())
        || !"after-fresh-start-realm-import".equals(bundle.path("applicability").asString())
        || !expectedBaselineDigest.equals(bundle.path("baselineArtifactDigest").asString())
        || bundle.path("containsSecretValues").asBoolean(true)
        || !bundle.path("fromBaselineRevision").isNull()
        || !KeycloakFgapMigrationContract.KEYCLOAK_VERSION.equals(
            bundle.path("keycloakVersion").asString())
        || !"blocked-post-import-operation".equals(bundle.path("status").asString())
        || !expectedTargetRevision.equals(bundle.path("toBaselineRevision").asString())) {
      throw blocked("bundle-contract-mismatch");
    }
    String reason = bundle.path("reason").asString();
    if (!reason.equals(
        "Keycloak 26.7 cannot import a specific-organization FGAP permission "
            + "in the same RealmRepresentation because authorization settings are "
            + "processed before organizations. The baseline remains default-deny; "
            + "an exact post-import Admin REST executor is required.")) {
      throw blocked("bundle-reason-mismatch");
    }
    JsonNode operations = bundle.path("operations");
    if (!operations.isArray() || operations.size() != 1) {
      throw blocked("bundle-operation-count-invalid");
    }
    JsonNode operation = operations.get(0);
    requireExactFields(operation, OPERATION_FIELDS, "bundle-operation-shape-invalid");
    if (!"keycloak-26.7-imports-client-authorization-before-organizations"
            .equals(operation.path("blockedBy").asString())
        || !KeycloakFgapMigrationContract.DESIRED_STATE_DIGEST.equals(
            operation.path("desiredStateDigest").asString())
        || !"/fineGrainedAdminPermissions".equals(operation.path("desiredStatePointer").asString())
        || !KeycloakFgapMigrationContract.OPERATION_ID.equals(operation.path("id").asString())
        || !"post-realm-import".equals(operation.path("phase").asString())
        || !"requires-qualified-admin-rest-executor".equals(operation.path("status").asString())
        || !"keycloak-fgap-v2".equals(operation.path("type").asString())) {
      throw blocked("bundle-operation-contract-mismatch");
    }
  }

  private static Path requireDirectory(Path artifactRoot) {
    if (artifactRoot == null || !artifactRoot.isAbsolute()) {
      throw blocked("artifact-root-invalid");
    }
    Path normalized = artifactRoot.normalize();
    if (Files.isSymbolicLink(normalized)
        || !Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) {
      throw blocked("artifact-root-unavailable");
    }
    try {
      return normalized.toRealPath(LinkOption.NOFOLLOW_LINKS);
    } catch (IOException failure) {
      throw blocked("artifact-root-unavailable");
    }
  }

  private static byte[] readArtifact(Path root, Path path, String unavailableCode) {
    try {
      if (!path.startsWith(root)
          || Files.isSymbolicLink(path)
          || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
        throw blocked(unavailableCode);
      }
      long size = Files.size(path);
      if (size < 1 || size > MAXIMUM_ARTIFACT_BYTES) {
        throw blocked(unavailableCode);
      }
      Path realPath = path.toRealPath(LinkOption.NOFOLLOW_LINKS);
      if (!realPath.startsWith(root)) {
        throw blocked(unavailableCode);
      }
      return Files.readAllBytes(realPath);
    } catch (KeycloakRealmMigrationException failure) {
      throw failure;
    } catch (IOException failure) {
      throw blocked(unavailableCode);
    }
  }

  private JsonNode parse(byte[] payload, String reasonCode) {
    try {
      JsonNode value = mapper.readTree(payload);
      if (value == null || !value.isObject()) {
        throw blocked(reasonCode);
      }
      return value;
    } catch (RuntimeException failure) {
      if (failure instanceof KeycloakRealmMigrationException migrationFailure) {
        throw migrationFailure;
      }
      throw blocked(reasonCode);
    }
  }

  private static void requireExactFields(JsonNode object, Set<String> expectedFields, String reasonCode) {
    if (!object.isObject()
        || !object.properties().stream()
            .map(java.util.Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toUnmodifiableSet())
            .equals(expectedFields)) {
      throw blocked(reasonCode);
    }
  }

  private static void requireEqualDigest(byte[] payload, String expectedDigest, String reasonCode) {
    try {
      String observed =
          "sha256:"
              + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
      if (!MessageDigest.isEqual(
          observed.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
          expectedDigest.getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
        throw blocked(reasonCode);
      }
    } catch (NoSuchAlgorithmException failure) {
      throw blocked("digest-algorithm-unavailable");
    }
  }

  private static void requireDigest(String value, String reasonCode) {
    if (value == null || !DIGEST.matcher(value).matches()) {
      throw blocked(reasonCode);
    }
  }

  private static KeycloakRealmMigrationException blocked(String reasonCode) {
    return new KeycloakRealmMigrationException(reasonCode);
  }

  record MigrationBundle(
      String manifestDigest,
      String bundleDigest,
      String baselineArtifactDigest,
      String targetBaselineRevision,
      String operationId,
      String semanticRealmSourceDigest,
      String migrationDefinitionDigest) {
    String currentBaselineRevision() {
      return targetBaselineRevision;
    }
  }
}

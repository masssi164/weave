package com.massimotter.weave.backend.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.massimotter.weave.backend.files.adapter.FilesVolumeAuthorityJpaRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.erdtman.jcs.JsonCanonicalizer;
import tools.jackson.databind.ObjectMapper;

class NativeFilesVolumeAuthorityTest {

  private static final String CANDIDATE = "a".repeat(40);
  private static final String FINGERPRINT = "b".repeat(64);

  @Test
  void mintsCanonicalImmutableMarkerAndReceiptBinding(@TempDir Path directory)
      throws Exception {
    Path receiptParent = Files.createDirectory(directory.resolve("schema-init"));
    Path blobRoot = Files.createDirectory(directory.resolve("blobs"));
    writeContext(receiptParent, "INITIAL_PROVISION", CANDIDATE);

    var context =
        NativeFilesVolumeAuthority.readTransitionContext(receiptParent, CANDIDATE)
            .orElseThrow();
    var authority = NativeFilesVolumeAuthority.mint(context, FINGERPRINT);
    NativeFilesVolumeAuthority.createOrValidateMarker(blobRoot, authority);

    assertThat(authority.authorityKey()).isEqualTo("native-files");
    assertThat(authority.volumeRef()).isNotEqualTo(authority.generationRef());
    assertThat(authority.transitionReceiptDigest()).matches("sha256:[0-9a-f]{64}");
    assertThat(authority.rootMarkerDigest()).matches("sha256:[0-9a-f]{64}");
    assertThat(blobRoot.resolve(NativeFilesVolumeAuthority.MARKER_FILE_NAME))
        .isRegularFile();
    NativeFilesVolumeAuthority.validateTransitionReceipt(authority, context);
    NativeFilesVolumeAuthority.validateMarker(blobRoot, authority);

    var projection = NativeFilesVolumeAuthority.receiptProjection(authority);
    var rebound =
        NativeFilesVolumeAuthority.authorityFromReceipt(
            new ObjectMapper().valueToTree(projection));
    assertThat(rebound).isEqualTo(authority);
  }

  @Test
  void missingOrAlteredMarkerAndReplacementRootFailClosed(@TempDir Path directory)
      throws Exception {
    Path receiptParent = Files.createDirectory(directory.resolve("schema-init"));
    Path blobRoot = Files.createDirectory(directory.resolve("blobs"));
    writeContext(receiptParent, "AUTHORIZED_RESET", CANDIDATE);
    var context =
        NativeFilesVolumeAuthority.readTransitionContext(receiptParent, CANDIDATE)
            .orElseThrow();
    var authority =
        NativeFilesVolumeAuthority.mint(context, testCatalogFingerprint());

    assertThatThrownBy(() -> NativeFilesVolumeAuthority.validateMarker(blobRoot, authority))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("marker");

    NativeFilesVolumeAuthority.createOrValidateMarker(blobRoot, authority);
    Files.writeString(
        blobRoot.resolve(NativeFilesVolumeAuthority.MARKER_FILE_NAME), "{}");
    assertThatThrownBy(() -> NativeFilesVolumeAuthority.validateMarker(blobRoot, authority))
        .isInstanceOf(IllegalStateException.class);

    Path replacement = Files.createDirectory(directory.resolve("replacement"));
    assertThatThrownBy(() -> NativeFilesVolumeAuthority.validateMarker(replacement, authority))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("marker");
  }

  @Test
  void ordinaryCreationCannotInferAuthorityFromAnEmptyRoot(@TempDir Path directory)
      throws Exception {
    Path receiptParent = Files.createDirectory(directory.resolve("schema-init"));
    assertThat(
            NativeFilesVolumeAuthority.readTransitionContext(receiptParent, CANDIDATE))
        .isEmpty();

    writeContext(receiptParent, "INITIAL_PROVISION", "c".repeat(40));
    assertThatThrownBy(
            () -> NativeFilesVolumeAuthority.readTransitionContext(receiptParent, CANDIDATE))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("stale");
  }

  @Test
  void rejectsOversizedAuthorityArtifactsBeforeParsing(@TempDir Path directory)
      throws Exception {
    Path receiptParent = Files.createDirectory(directory.resolve("schema-init"));
    Path context = receiptParent.resolve(
        NativeFilesVolumeAuthority.TRANSITION_CONTEXT_FILE_NAME);
    Files.write(context, new byte[(int) NativeFilesVolumeAuthority.MAX_TRANSITION_CONTEXT_BYTES + 1]);

    assertThatThrownBy(
            () -> NativeFilesVolumeAuthority.readTransitionContext(receiptParent, CANDIDATE))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("oversized");

    Files.delete(context);
    writeContext(receiptParent, "INITIAL_PROVISION", CANDIDATE);
    var transition = NativeFilesVolumeAuthority.readTransitionContext(receiptParent, CANDIDATE)
        .orElseThrow();
    var authority = NativeFilesVolumeAuthority.mint(transition, testCatalogFingerprint());
    Path blobRoot = Files.createDirectory(directory.resolve("blobs"));
    NativeFilesVolumeAuthority.createOrValidateMarker(blobRoot, authority);
    Files.write(
        blobRoot.resolve(NativeFilesVolumeAuthority.MARKER_FILE_NAME),
        new byte[(int) NativeFilesVolumeAuthority.MAX_ROOT_MARKER_BYTES + 1]);

    assertThatThrownBy(() -> NativeFilesVolumeAuthority.validateMarker(blobRoot, authority))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("oversized");

    Path receipt = receiptParent.resolve("receipt.json");
    Files.write(receipt, new byte[(int) SchemaReceiptVerifier.MAX_SCHEMA_RECEIPT_BYTES + 1]);
    assertThatThrownBy(() -> SchemaReceiptVerifier.verify(Map.of(
            "WEAVE_CANDIDATE_COMMIT", CANDIDATE,
            "WEAVE_SCHEMA_INIT_RECEIPT_FILE", receipt.toString(),
            "WEAVE_NATIVE_FILES_BLOB_ROOT", blobRoot.toString())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("oversized");
  }

  @Test
  void servingReadinessBindsPersistedRowMarkerAndExactSchemaReceipt(
      @TempDir Path directory) throws Exception {
    Path receiptParent = Files.createDirectory(directory.resolve("schema-init"));
    Path blobRoot = Files.createDirectory(directory.resolve("blobs"));
    writeContext(receiptParent, "INITIAL_PROVISION", CANDIDATE);
    var context =
        NativeFilesVolumeAuthority.readTransitionContext(receiptParent, CANDIDATE)
            .orElseThrow();
    var authority =
        NativeFilesVolumeAuthority.mint(context, testCatalogFingerprint());
    NativeFilesVolumeAuthority.createOrValidateMarker(blobRoot, authority);
    Path receipt = receiptParent.resolve("receipt.json");
    writeSchemaReceipt(receipt, authority);
    FilesVolumeAuthorityJpaRepository repository =
        mock(FilesVolumeAuthorityJpaRepository.class);
    when(repository.findAll()).thenReturn(java.util.List.of(authority.toEntity()));
    var readiness =
        new NativeFilesVolumeAuthorityReadiness(repository, blobRoot, receipt, CANDIDATE);

    assertThat(readiness.isReady()).isTrue();

    Files.writeString(
        blobRoot.resolve(NativeFilesVolumeAuthority.MARKER_FILE_NAME), "{}");
    assertThat(readiness.isReady()).isFalse();
    Files.delete(blobRoot.resolve(NativeFilesVolumeAuthority.MARKER_FILE_NAME));
    assertThat(readiness.isReady()).isFalse();
  }

  private static void writeContext(
      Path receiptParent, String transitionKind, String candidate) throws Exception {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("schemaVersion", NativeFilesVolumeAuthority.TRANSITION_CONTEXT_FORMAT);
    value.put("transitionKind", transitionKind);
    value.put("composeProject", "weave-test");
    value.put("runScope", "test-run");
    value.put("candidateCommit", candidate);
    Files.writeString(
        receiptParent.resolve(NativeFilesVolumeAuthority.TRANSITION_CONTEXT_FILE_NAME),
        new ObjectMapper().writeValueAsString(value));
  }

  private static void writeSchemaReceipt(
      Path receipt, NativeFilesVolumeAuthority.Authority authority) throws Exception {
    Map<String, Object> catalog = new LinkedHashMap<>();
    catalog.put("format", SchemaCatalogFingerprint.FORMAT);
    Map<String, Object> tables = new LinkedHashMap<>();
    tables.put("weave_files_volume_authorities", Map.of());
    tables.put("weave_schema_authority", Map.of());
    catalog.put("tables", tables);
    byte[] canonical =
        new JsonCanonicalizer(new ObjectMapper().writeValueAsString(catalog))
            .getEncodedUTF8();
    String fingerprint =
        java.util.HexFormat.of()
            .formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
    assertThat(fingerprint).isEqualTo(authority.schemaHistoryFingerprint());
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("schemaVersion", SchemaAuthorityInitializer.RECEIPT_FORMAT);
    value.put("supportSafe", true);
    value.put("authority", "flyway");
    value.put("epoch", SchemaAuthorityInitializer.EPOCH);
    value.put("relationalModelId", SchemaAuthorityInitializer.MODEL_ID);
    value.put("candidateCommit", CANDIDATE);
    value.put("migrationsExecuted", 0);
    value.put("targetSchemaVersion", "7");
    value.put(
        "nativeFilesVolumeAuthority",
        NativeFilesVolumeAuthority.receiptProjection(authority));
    value.put("catalogFingerprintFormat", SchemaCatalogFingerprint.FORMAT);
    value.put("catalogFingerprint", fingerprint);
    value.put("tableCount", tables.size());
    value.put("tables", tables.keySet().stream().sorted().toList());
    value.put("catalogProjection", catalog);
    value.put("completedAtUtc", "2026-08-20T00:00:00Z");
    value.put("secretValuesIncluded", false);
    Files.writeString(receipt, new ObjectMapper().writeValueAsString(value));
  }

  private static String testCatalogFingerprint() throws Exception {
    Map<String, Object> catalog = new LinkedHashMap<>();
    catalog.put("format", SchemaCatalogFingerprint.FORMAT);
    Map<String, Object> tables = new LinkedHashMap<>();
    tables.put("weave_files_volume_authorities", Map.of());
    tables.put("weave_schema_authority", Map.of());
    catalog.put("tables", tables);
    byte[] canonical =
        new JsonCanonicalizer(new ObjectMapper().writeValueAsString(catalog))
            .getEncodedUTF8();
    return java.util.HexFormat.of()
        .formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
  }
}

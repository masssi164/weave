package com.massimotter.weave.backend.schema;

import com.massimotter.weave.backend.files.adapter.FilesVolumeAuthorityJpaRepository;
import java.nio.file.Path;
import java.util.Map;

/** Fail-closed serving check for the relational/physical native Files generation binding. */
public final class NativeFilesVolumeAuthorityReadiness {

  private final FilesVolumeAuthorityJpaRepository repository;
  private final Path blobRoot;
  private final Path schemaReceipt;
  private final String candidateCommit;

  public NativeFilesVolumeAuthorityReadiness(
      FilesVolumeAuthorityJpaRepository repository,
      Path blobRoot,
      Path schemaReceipt,
      String candidateCommit) {
    this.repository = repository;
    this.blobRoot = blobRoot.toAbsolutePath().normalize();
    this.schemaReceipt = schemaReceipt.toAbsolutePath().normalize();
    this.candidateCommit = candidateCommit == null ? "" : candidateCommit;
  }

  public boolean isReady() {
    try {
      requireValidated();
      return true;
    } catch (Exception invalidAuthority) {
      return false;
    }
  }

  /**
   * Returns the one exact authority whose relational row, schema receipt, and root marker agree.
   *
   * <p>Callers use this stronger form when a physical operation must remain fenced to the
   * validated volume/generation, rather than merely contributing a boolean readiness signal.
   */
  public NativeFilesVolumeAuthority.Authority requireValidated() {
    try {
      var rows = repository.findAll();
      if (rows.size() != 1) {
        throw new IllegalStateException("native Files volume authority row is not singular");
      }
      NativeFilesVolumeAuthority.Authority persisted =
          NativeFilesVolumeAuthority.Authority.fromEntity(rows.getFirst());
      var receipt = SchemaReceiptVerifier.verify(
          Map.of(
              "WEAVE_CANDIDATE_COMMIT", candidateCommit,
              "WEAVE_SCHEMA_INIT_RECEIPT_FILE", schemaReceipt.toString(),
              "WEAVE_NATIVE_FILES_BLOB_ROOT", blobRoot.toString()));
      NativeFilesVolumeAuthority.Authority bound =
          NativeFilesVolumeAuthority.authorityFromReceipt(
              receipt.path("nativeFilesVolumeAuthority"));
      if (!persisted.equals(bound)) {
        throw new IllegalStateException(
            "native Files volume authority disagrees with the schema receipt");
      }
      NativeFilesVolumeAuthority.validateMarker(blobRoot, persisted);
      return persisted;
    } catch (RuntimeException failure) {
      throw failure;
    } catch (Exception failure) {
      throw new IllegalStateException("native Files volume authority is not validated", failure);
    }
  }
}

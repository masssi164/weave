package com.massimotter.weave.backend.identity.migration;

import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

/** Atomically publishes the support-safe receipt only after authority retirement succeeds. */
final class KeycloakRealmMigrationReceiptWriter {
  private static final Set<PosixFilePermission> SUPPORT_SAFE_RECEIPT_PERMISSIONS =
      Set.of(
          PosixFilePermission.OWNER_READ,
          PosixFilePermission.OWNER_WRITE,
          PosixFilePermission.GROUP_READ,
          PosixFilePermission.OTHERS_READ);

  private final ObjectMapper mapper;

  KeycloakRealmMigrationReceiptWriter(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  void requireTargetAvailable(Path artifactRoot) {
    target(artifactRoot);
  }

  Path write(
      Path artifactRoot, KeycloakFgapMigrationExecutor.MigrationResult receipt) {
    Path target = target(artifactRoot);
    Path parent = target.getParent();
    Path temporary = null;
    try {
      byte[] payload = (mapper.writeValueAsString(receipt) + "\n").getBytes(StandardCharsets.UTF_8);
      temporary = Files.createTempFile(parent, ".fgap-migration-receipt-", ".tmp");
      Files.write(temporary, payload);
      if (Files.getFileStore(temporary).supportsFileAttributeView("posix")) {
        Files.setPosixFilePermissions(temporary, SUPPORT_SAFE_RECEIPT_PERMISSIONS);
      }
      try {
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException failure) {
        throw blocked("migration-receipt-atomic-publish-unavailable");
      }
      temporary = null;
      return target;
    } catch (KeycloakRealmMigrationException failure) {
      throw failure;
    } catch (IOException failure) {
      throw blocked("migration-receipt-publish-failed");
    } finally {
      if (temporary != null) {
        try {
          Files.deleteIfExists(temporary);
        } catch (IOException ignored) {
          // The command remains blocked. The private bootstrap secret is never written here.
        }
      }
    }
  }

  private static Path target(Path artifactRoot) {
    if (artifactRoot == null || !artifactRoot.isAbsolute()) {
      throw blocked("migration-receipt-target-invalid");
    }
    Path normalizedRoot = artifactRoot.normalize();
    Path target = normalizedRoot.resolve(KeycloakFgapMigrationContract.RECEIPT_PATH).normalize();
    Path parent = target.getParent();
    if (!target.startsWith(normalizedRoot)
        || Files.exists(target, LinkOption.NOFOLLOW_LINKS)
        || parent == null
        || Files.isSymbolicLink(parent)
        || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
      throw blocked("migration-receipt-target-invalid");
    }
    return target;
  }

  private static KeycloakRealmMigrationException blocked(String reason) {
    return new KeycloakRealmMigrationException(reason);
  }
}

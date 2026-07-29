package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityException;
import com.massimotter.weave.backend.agentruntime.port.SecretRefAccess;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Read-only SecretRef adapter binding one logical reference to one exact mounted file.
 *
 * <p>This keeps the Identity-owned entitlement credential outside the writable per-Cell workload
 * tree and prevents the workload registration adapter from resolving it.
 */
final class ExactMountedSecretRefAccess implements SecretRefAccess {
  private static final long MAXIMUM_SECRET_BYTES = 64 * 1024;
  private static final Set<PosixFilePermission> FORBIDDEN_PERMISSIONS =
      EnumSet.of(
          PosixFilePermission.GROUP_READ,
          PosixFilePermission.GROUP_WRITE,
          PosixFilePermission.GROUP_EXECUTE,
          PosixFilePermission.OTHERS_READ,
          PosixFilePermission.OTHERS_WRITE,
          PosixFilePermission.OTHERS_EXECUTE);

  private final String credentialRef;
  private final Path mountedFile;

  ExactMountedSecretRefAccess(String credentialRef, Path mountedFile) {
    if (credentialRef == null || credentialRef.isBlank() || mountedFile == null) {
      throw new IllegalArgumentException("credentialRef and mountedFile are required");
    }
    this.credentialRef = credentialRef;
    this.mountedFile = mountedFile.toAbsolutePath().normalize();
  }

  @Override
  public <T> T withSecret(String requestedRef, SecretOperation<T> operation) {
    Objects.requireNonNull(operation, "operation");
    if (!credentialRef.equals(requestedRef)) {
      throw new RuntimeWorkloadIdentityException(
          "The requested SecretRef is outside the qualified entitlement boundary");
    }
    byte[] secret = read();
    try {
      return operation.apply(secret);
    } finally {
      Arrays.fill(secret, (byte) 0);
    }
  }

  private byte[] read() {
    try {
      if (!Files.isRegularFile(mountedFile, LinkOption.NOFOLLOW_LINKS)
          || Files.isSymbolicLink(mountedFile)) {
        throw new RuntimeWorkloadIdentityException(
            "The qualified entitlement SecretRef must be a regular non-symlink file");
      }
      long size = Files.size(mountedFile);
      if (size < 1 || size > MAXIMUM_SECRET_BYTES) {
        throw new RuntimeWorkloadIdentityException(
            "The qualified entitlement SecretRef payload size is invalid");
      }
      try {
        if (Files.getPosixFilePermissions(mountedFile, LinkOption.NOFOLLOW_LINKS).stream()
            .anyMatch(FORBIDDEN_PERMISSIONS::contains)) {
          throw new RuntimeWorkloadIdentityException(
              "The qualified entitlement SecretRef permissions are too broad");
        }
      } catch (UnsupportedOperationException ignored) {
        // The platform ACL remains authoritative on non-POSIX filesystems.
      }
      return Files.readAllBytes(mountedFile);
    } catch (RuntimeWorkloadIdentityException failure) {
      throw failure;
    } catch (IOException failure) {
      throw new RuntimeWorkloadIdentityException(
          "The qualified entitlement SecretRef is unavailable", failure);
    }
  }
}

package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityException;
import com.massimotter.weave.backend.agentruntime.port.SecretRefAccess;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

/** Read-only, exact-file SecretRef boundary for the Server-owned identity administration key. */
final class ExactFileSecretRefAccess implements SecretRefAccess {
  private static final long MAXIMUM_SECRET_BYTES = 64 * 1024;
  private static final Set<PosixFilePermission> OWNER_READ_WRITE =
      Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

  private final String credentialRef;
  private final Path path;

  ExactFileSecretRefAccess(String credentialRef, Path path) {
    if (credentialRef == null || !credentialRef.startsWith("credentialref://")) {
      throw new IllegalArgumentException("credentialRef must be a credentialref URI");
    }
    this.credentialRef = credentialRef;
    this.path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
  }

  @Override
  public <T> T withSecret(String requestedRef, SecretOperation<T> operation) {
    Objects.requireNonNull(operation, "operation");
    if (!credentialRef.equals(requestedRef)) {
      throw new RuntimeWorkloadIdentityException("The identity administration SecretRef is invalid");
    }
    byte[] value = read();
    try {
      return operation.apply(value);
    } finally {
      Arrays.fill(value, (byte) 0);
    }
  }

  private byte[] read() {
    try {
      if (Files.isSymbolicLink(path)
          || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
        throw new RuntimeWorkloadIdentityException(
            "The identity administration private JWK is unavailable");
      }
      if (Files.getFileStore(path).supportsFileAttributeView("posix")
          && !Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS)
              .equals(OWNER_READ_WRITE)) {
        throw new RuntimeWorkloadIdentityException(
            "The identity administration private JWK permissions are invalid");
      }
      long size = Files.size(path);
      if (size < 1 || size > MAXIMUM_SECRET_BYTES) {
        throw new RuntimeWorkloadIdentityException(
            "The identity administration private JWK size is invalid");
      }
      return Files.readAllBytes(path);
    } catch (IOException failure) {
      throw new RuntimeWorkloadIdentityException(
          "The identity administration private JWK is unavailable");
    }
  }
}

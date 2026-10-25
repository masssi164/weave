package com.massimotter.weave.backend.identity.migration;

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

/** Exact mode-0600 SecretRef for Keycloak's short-lived bootstrap-admin service secret. */
final class KeycloakRealmMigrationSecretRefAccess implements SecretRefAccess {
  private static final int MINIMUM_SECRET_BYTES = 32;
  private static final int MAXIMUM_SECRET_BYTES = 4 * 1024;
  private static final Set<PosixFilePermission> OWNER_READ_WRITE =
      Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

  private final String credentialRef;
  private final Path secretFile;

  KeycloakRealmMigrationSecretRefAccess(String credentialRef, Path secretFile) {
    if (credentialRef == null || !credentialRef.startsWith("credentialref://")) {
      throw new IllegalArgumentException("credentialRef must be a credentialref URI");
    }
    this.credentialRef = credentialRef;
    this.secretFile = Objects.requireNonNull(secretFile).toAbsolutePath().normalize();
  }

  @Override
  public <T> T withSecret(String requestedRef, SecretOperation<T> operation) {
    Objects.requireNonNull(operation, "operation");
    if (!credentialRef.equals(requestedRef)) {
      throw unavailable();
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
      if (Files.isSymbolicLink(secretFile)
          || !Files.isRegularFile(secretFile, LinkOption.NOFOLLOW_LINKS)
          || Files.size(secretFile) < MINIMUM_SECRET_BYTES
          || Files.size(secretFile) > MAXIMUM_SECRET_BYTES
          || (Files.getFileStore(secretFile).supportsFileAttributeView("posix")
              && !OWNER_READ_WRITE.equals(
                  Files.getPosixFilePermissions(secretFile, LinkOption.NOFOLLOW_LINKS)))) {
        throw unavailable();
      }
      return Files.readAllBytes(secretFile);
    } catch (RuntimeWorkloadIdentityException failure) {
      throw failure;
    } catch (IOException failure) {
      throw unavailable();
    }
  }

  private static RuntimeWorkloadIdentityException unavailable() {
    return new RuntimeWorkloadIdentityException(
        "The Keycloak realm-migration bootstrap SecretRef is unavailable");
  }
}

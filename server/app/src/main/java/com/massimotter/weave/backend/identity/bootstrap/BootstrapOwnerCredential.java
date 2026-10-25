package com.massimotter.weave.backend.identity.bootstrap;

import com.massimotter.weave.backend.config.IdentityInvitationProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Rotatable, file-backed credential for the one empty-realm owner invitation operation. */
@Component
@ConditionalOnProperty(
    name = "weave.identity.invitations.bootstrap-owner.enabled",
    havingValue = "true")
public final class BootstrapOwnerCredential {
  private static final int MINIMUM_BYTES = 32;
  private static final int MAXIMUM_BYTES = 512;

  private final Path tokenFile;
  private final AtomicBoolean consumed = new AtomicBoolean();

  public BootstrapOwnerCredential(IdentityInvitationProperties properties) {
    String configured = properties.bootstrapOwner().tokenFile();
    if (configured.isBlank()) {
      throw new IllegalStateException("Owner bootstrap token SecretRef is not configured");
    }
    this.tokenFile = Path.of(configured).toAbsolutePath().normalize();
  }

  public boolean matches(String candidate) {
    if (consumed.get() || candidate == null || candidate.isBlank()) {
      return false;
    }
    byte[] expected = readToken();
    byte[] supplied = candidate.getBytes(StandardCharsets.UTF_8);
    try {
      return supplied.length >= MINIMUM_BYTES
          && supplied.length <= MAXIMUM_BYTES
          && MessageDigest.isEqual(expected, supplied);
    } finally {
      java.util.Arrays.fill(expected, (byte) 0);
      java.util.Arrays.fill(supplied, (byte) 0);
    }
  }

  /** Atomically consumes the one-shot credential without mutating its deployment-owned SecretRef. */
  public void consumeAfterSuccess() {
    consumed.set(true);
  }

  private byte[] readToken() {
    try {
      if (Files.isSymbolicLink(tokenFile)
          || !Files.isRegularFile(tokenFile, LinkOption.NOFOLLOW_LINKS)) {
        throw unavailable();
      }
      long size = Files.size(tokenFile);
      if (size < MINIMUM_BYTES || size > MAXIMUM_BYTES + 1L) {
        throw unavailable();
      }
      requirePrivatePermissions();
      String value = Files.readString(tokenFile, StandardCharsets.UTF_8).strip();
      byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
      if (bytes.length < MINIMUM_BYTES || bytes.length > MAXIMUM_BYTES) {
        java.util.Arrays.fill(bytes, (byte) 0);
        throw unavailable();
      }
      return bytes;
    } catch (IOException | SecurityException failure) {
      throw unavailable();
    }
  }

  private void requirePrivatePermissions() throws IOException {
    try {
      Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(tokenFile);
      if (permissions.contains(PosixFilePermission.GROUP_READ)
          || permissions.contains(PosixFilePermission.GROUP_WRITE)
          || permissions.contains(PosixFilePermission.GROUP_EXECUTE)
          || permissions.contains(PosixFilePermission.OTHERS_READ)
          || permissions.contains(PosixFilePermission.OTHERS_WRITE)
          || permissions.contains(PosixFilePermission.OTHERS_EXECUTE)) {
        throw unavailable();
      }
    } catch (UnsupportedOperationException ignored) {
      // Regular-file/no-symlink checks remain binding on non-POSIX file systems.
    }
  }

  private static IllegalStateException unavailable() {
    return new IllegalStateException("Owner bootstrap token SecretRef is unavailable");
  }
}

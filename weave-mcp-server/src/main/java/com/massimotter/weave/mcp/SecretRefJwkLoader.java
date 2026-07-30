package com.massimotter.weave.mcp;

import com.nimbusds.jose.jwk.JWK;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

/** Loads a private client-authentication JWK or PEM key from a mounted, non-symlink SecretRef. */
final class SecretRefJwkLoader {
  private SecretRefJwkLoader() {}

  static JWK loadPrivateJwk(Path file) {
    try {
      Path normalized = file.toAbsolutePath().normalize();
      if (Files.isSymbolicLink(normalized)
          || !Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)
          || !Files.isReadable(normalized)
          || Files.size(normalized) > 65_536) {
        throw unavailable();
      }
      try {
        Set<PosixFilePermission> permissions =
            Files.getPosixFilePermissions(normalized, LinkOption.NOFOLLOW_LINKS);
        if (permissions.contains(PosixFilePermission.GROUP_WRITE)
            || permissions.contains(PosixFilePermission.OTHERS_READ)
            || permissions.contains(PosixFilePermission.OTHERS_WRITE)
            || permissions.contains(PosixFilePermission.OTHERS_EXECUTE)) {
          throw unavailable();
        }
      } catch (UnsupportedOperationException ignored) {
        // The regular-file, no-symlink and readability checks remain mandatory.
      }
      String encoded = Files.readString(normalized, StandardCharsets.UTF_8);
      JWK jwk =
          encoded.stripLeading().startsWith("{")
              ? JWK.parse(encoded)
              : JWK.parseFromPEMEncodedObjects(encoded);
      if (jwk == null || !jwk.isPrivate()) {
        throw unavailable();
      }
      return jwk;
    } catch (McpAdmissionException failure) {
      throw failure;
    } catch (Exception invalid) {
      throw unavailable();
    }
  }

  private static McpAdmissionException unavailable() {
    return new McpAdmissionException(McpAdmissionException.Kind.UNAVAILABLE);
  }
}

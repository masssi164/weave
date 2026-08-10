package com.massimotter.weave.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExactFileSecretRefAccessTest {
  private static final String REF = "credentialref://weave/keycloak/weave-identity-admin";

  @TempDir Path temporary;

  @Test
  void allowsOnlyTheExactModeOwnerOnlyRegularFile() throws Exception {
    Path key = temporary.resolve("identity-admin.jwk");
    Files.writeString(key, "private-jwk", StandardCharsets.UTF_8);
    if (Files.getFileStore(key).supportsFileAttributeView("posix")) {
      Files.setPosixFilePermissions(key, PosixFilePermissions.fromString("rw-------"));
    }
    ExactFileSecretRefAccess access = new ExactFileSecretRefAccess(REF, key);

    String observed =
        access.withSecret(REF, value -> new String(value, StandardCharsets.UTF_8));
    assertThat(observed).isEqualTo("private-jwk");
    assertThatThrownBy(
            () -> access.withSecret("credentialref://weave/keycloak/another", value -> null))
        .isInstanceOf(RuntimeWorkloadIdentityException.class)
        .hasMessage("The identity administration SecretRef is invalid");
  }

  @Test
  void rejectsSymlinksAndGroupReadableFiles() throws Exception {
    Path target = temporary.resolve("target.jwk");
    Files.writeString(target, "private-jwk", StandardCharsets.UTF_8);
    Path symlink = temporary.resolve("identity-admin.jwk");
    Files.createSymbolicLink(symlink, target.getFileName());

    assertThatThrownBy(
            () -> new ExactFileSecretRefAccess(REF, symlink).withSecret(REF, value -> null))
        .isInstanceOf(RuntimeWorkloadIdentityException.class)
        .hasMessage("The identity administration private JWK is unavailable");

    if (Files.getFileStore(target).supportsFileAttributeView("posix")) {
      Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rw-r-----"));
      assertThatThrownBy(
              () -> new ExactFileSecretRefAccess(REF, target).withSecret(REF, value -> null))
          .isInstanceOf(RuntimeWorkloadIdentityException.class)
          .hasMessage("The identity administration private JWK permissions are invalid");
    }
  }
}

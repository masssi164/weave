package com.massimotter.weave.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExactMountedSecretRefAccessTest {
  private static final String REF = "credentialref://weave/keycloak/weave-identity-admin";

  @TempDir Path temporary;

  @Test
  void readsOnlyTheBoundOwnerOnlyFileAndClearsTheCallbackBuffer() throws Exception {
    Path secret = privateFile("identity-admin", "protected-value");
    var retained = new AtomicReference<byte[]>();
    var access = new ExactMountedSecretRefAccess(REF, secret);

    String result =
        access.withSecret(
            REF,
            value -> {
              retained.set(value);
              return new String(value, StandardCharsets.UTF_8);
            });

    assertThat(result).isEqualTo("protected-value");
    assertThat(retained.get()).containsOnly((byte) 0);
  }

  @Test
  void rejectsAnotherLogicalReference() throws Exception {
    var access = new ExactMountedSecretRefAccess(REF, privateFile("identity-admin", "value"));

    assertThatThrownBy(
            () ->
                access.withSecret(
                    "credentialref://weave/keycloak/weave-agent-runtime-admin",
                    value -> null))
        .isInstanceOf(RuntimeWorkloadIdentityException.class)
        .hasMessageContaining("outside the qualified entitlement boundary");
  }

  @Test
  void rejectsSymlinksAndBroadPermissions() throws Exception {
    Path secret = privateFile("identity-admin", "value");
    Path link = temporary.resolve("identity-admin-link");
    Files.createSymbolicLink(link, secret.getFileName());

    assertThatThrownBy(() -> new ExactMountedSecretRefAccess(REF, link).withSecret(REF, value -> null))
        .isInstanceOf(RuntimeWorkloadIdentityException.class)
        .hasMessageContaining("regular non-symlink file");

    if (Files.getFileStore(secret).supportsFileAttributeView("posix")) {
      Files.setPosixFilePermissions(secret, PosixFilePermissions.fromString("rw-r-----"));
      assertThatThrownBy(
              () ->
                  new ExactMountedSecretRefAccess(REF, secret)
                      .withSecret(REF, value -> null))
          .isInstanceOf(RuntimeWorkloadIdentityException.class)
          .hasMessageContaining("permissions are too broad");
    }
  }

  private Path privateFile(String name, String value) throws Exception {
    Path secret = Files.writeString(temporary.resolve(name), value, StandardCharsets.UTF_8);
    try {
      Files.setPosixFilePermissions(secret, PosixFilePermissions.fromString("rw-------"));
    } catch (UnsupportedOperationException ignored) {
      // Non-POSIX platforms retain type, symlink, size, and exact-reference checks.
    }
    return secret;
  }
}

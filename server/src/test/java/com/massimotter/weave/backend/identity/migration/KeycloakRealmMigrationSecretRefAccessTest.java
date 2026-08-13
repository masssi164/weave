package com.massimotter.weave.backend.identity.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KeycloakRealmMigrationSecretRefAccessTest {
  private static final String REF =
      "credentialref://weave/keycloak/realm-migration/bootstrap-admin";

  @TempDir Path temporary;

  @Test
  void readsOnlyTheExactMode0600BootstrapSecretAndClearsTheCallbackCopy() throws Exception {
    Path secret = temporary.resolve("bootstrap-secret");
    Files.writeString(
        secret,
        "qualified-temporary-bootstrap-secret-value",
        StandardCharsets.UTF_8);
    mode0600(secret);
    byte[][] callbackValue = new byte[1][];

    String digest =
        new KeycloakRealmMigrationSecretRefAccess(REF, secret)
            .withSecret(
                REF,
                value -> {
                  callbackValue[0] = value;
                  return Integer.toString(value.length);
                });

    assertThat(digest).isEqualTo("42");
    assertThat(callbackValue[0]).containsOnly((byte) 0);
  }

  @Test
  void rejectsSymlinksAndBroaderPermissions() throws Exception {
    Path secret = temporary.resolve("bootstrap-secret");
    Files.writeString(
        secret,
        "qualified-temporary-bootstrap-secret-value",
        StandardCharsets.UTF_8);
    mode0600(secret);
    Path symlink = temporary.resolve("bootstrap-link");
    Files.createSymbolicLink(symlink, secret);

    assertThatThrownBy(
            () ->
                new KeycloakRealmMigrationSecretRefAccess(REF, symlink)
                    .withSecret(REF, value -> null))
        .isInstanceOf(RuntimeWorkloadIdentityException.class)
        .hasMessage("The Keycloak realm-migration bootstrap SecretRef is unavailable");

    if (Files.getFileStore(secret).supportsFileAttributeView("posix")) {
      Files.setPosixFilePermissions(secret, PosixFilePermissions.fromString("rw-r-----"));
      assertThatThrownBy(
              () ->
                  new KeycloakRealmMigrationSecretRefAccess(REF, secret)
                      .withSecret(REF, value -> null))
          .isInstanceOf(RuntimeWorkloadIdentityException.class)
          .hasMessage("The Keycloak realm-migration bootstrap SecretRef is unavailable");
    }
  }

  private static void mode0600(Path path) throws Exception {
    if (Files.getFileStore(path).supportsFileAttributeView("posix")) {
      Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
    }
  }
}

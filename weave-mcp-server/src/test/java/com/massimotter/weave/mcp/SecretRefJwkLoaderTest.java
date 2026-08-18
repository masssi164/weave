package com.massimotter.weave.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SecretRefJwkLoaderTest {

  @TempDir java.nio.file.Path temporary;

  @Test
  void loadsPermissionRestrictedPkcs8PemForPrivateKeyJwt() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    byte[] encoded = generator.generateKeyPair().getPrivate().getEncoded();
    String pem =
        "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(encoded)
            + "\n-----END PRIVATE KEY-----\n";
    var file = temporary.resolve("mcp-private.pem");
    Files.writeString(file, pem, StandardCharsets.US_ASCII);
    try {
      Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
    } catch (UnsupportedOperationException ignored) {
      // Loader retains its platform-independent regular-file and no-symlink checks.
    }

    assertThat(SecretRefJwkLoader.loadPrivateJwk(file).isPrivate()).isTrue();
  }
}

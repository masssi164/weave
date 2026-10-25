package com.massimotter.weave.backend.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.config.IdentityInvitationProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IdentityOpaqueReferenceCodecTest {

  @TempDir Path temporaryDirectory;
  private int secretNumber;

  @Test
  void derivesStableOrganizationBoundHandlesFromAnExactSecretRef() throws Exception {
    Path secret = secret("0123456789abcdef0123456789abcdef");
    IdentityOpaqueReferenceCodec codec = codec(secret);

    codec.requireReady();

    assertThat(codec.invitation("organization-1", "provider-invitation-1"))
        .startsWith("inv_")
        .isEqualTo(codec.invitation("organization-1", "provider-invitation-1"))
        .isNotEqualTo(codec.invitation("organization-2", "provider-invitation-1"));
    assertThat(codec.member("organization-1", "provider-invitation-1")).startsWith("mem_");
    assertThat(codec.cursor("organization-1", "provider-invitation-1")).startsWith("cur_");
  }

  @Test
  void rejectsMissingShortAndSymlinkedSecretRefsBeforeUse() throws Exception {
    IdentityInvitationProperties missing = new IdentityInvitationProperties();
    assertThatThrownBy(() -> new IdentityOpaqueReferenceCodec(missing))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not configured");

    Path shortSecret = secret("too-short");
    assertThatThrownBy(() -> codec(shortSecret))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("at least 32 bytes");

    Path target = secret("abcdef0123456789abcdef0123456789");
    Path symlink = temporaryDirectory.resolve("identity-reference-link");
    Files.createSymbolicLink(symlink, target.getFileName());
    assertThatThrownBy(() -> codec(symlink))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("unreadable");
  }

  private Path secret(String value) throws Exception {
    Path path = temporaryDirectory.resolve("identity-reference-" + secretNumber++);
    Files.writeString(path, value);
    return path;
  }

  private IdentityOpaqueReferenceCodec codec(Path secret) {
    IdentityInvitationProperties properties = new IdentityInvitationProperties();
    properties.keycloak().setReferenceHmacSecretFile(secret.toString());
    return new IdentityOpaqueReferenceCodec(properties);
  }
}

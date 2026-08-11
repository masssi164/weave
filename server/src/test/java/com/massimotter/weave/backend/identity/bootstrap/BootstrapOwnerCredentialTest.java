package com.massimotter.weave.backend.identity.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.config.IdentityInvitationProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BootstrapOwnerCredentialTest {
  private static final String FIRST_TOKEN =
      "8ebf346fb3617f1e3356c59f337c6230601730c39e9f5adbfbb2a93b13cd99ec";
  private static final String ROTATED_TOKEN =
      "e704fa892730297ae9be7d1b39ce3a24980f948245855b284b40aba1e301335a";

  @TempDir Path temporaryDirectory;

  @Test
  void readsTheSecretRefForEveryComparisonSoRotationDoesNotRequireRestart() throws IOException {
    Path tokenFile = privateTokenFile(FIRST_TOKEN);
    BootstrapOwnerCredential credential = credential(tokenFile);

    assertThat(credential.matches(FIRST_TOKEN)).isTrue();
    assertThat(credential.matches(ROTATED_TOKEN)).isFalse();

    Files.writeString(tokenFile, ROTATED_TOKEN + System.lineSeparator());
    setPrivatePermissions(tokenFile);

    assertThat(credential.matches(FIRST_TOKEN)).isFalse();
    assertThat(credential.matches(ROTATED_TOKEN)).isTrue();
  }

  @Test
  void consumesTheCredentialWithoutMutatingTheDeploymentOwnedSecretRef() throws IOException {
    Path tokenFile = privateTokenFile(FIRST_TOKEN);
    BootstrapOwnerCredential credential = credential(tokenFile);

    assertThat(credential.matches(FIRST_TOKEN)).isTrue();

    credential.consumeAfterSuccess();

    assertThat(Files.exists(tokenFile)).isTrue();
    assertThat(credential.matches(FIRST_TOKEN)).isFalse();
    credential.consumeAfterSuccess();
  }

  @Test
  void rejectsBlankShortAndIncorrectCredentialsWithoutExposingTheExpectedValue()
      throws IOException {
    BootstrapOwnerCredential credential = credential(privateTokenFile(FIRST_TOKEN));

    assertThat(credential.matches(null)).isFalse();
    assertThat(credential.matches("")).isFalse();
    assertThat(credential.matches("too-short")).isFalse();
    assertThat(credential.matches(ROTATED_TOKEN)).isFalse();
  }

  @Test
  void failsClosedWhenTheSecretRefIsGroupOrWorldReadable() throws IOException {
    Path tokenFile = privateTokenFile(FIRST_TOKEN);
    try {
      Files.setPosixFilePermissions(
          tokenFile,
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.GROUP_READ));
    } catch (UnsupportedOperationException unsupported) {
      return;
    }

    assertThatThrownBy(() -> credential(tokenFile).matches(FIRST_TOKEN))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Owner bootstrap token SecretRef is unavailable")
        .hasMessageNotContaining(FIRST_TOKEN);
  }

  @Test
  void failsClosedForASymbolicLink() throws IOException {
    Path target = privateTokenFile(FIRST_TOKEN);
    Path link = temporaryDirectory.resolve("bootstrap-owner-link.token");
    try {
      Files.createSymbolicLink(link, target);
    } catch (UnsupportedOperationException unsupported) {
      return;
    }

    assertThatThrownBy(() -> credential(link).matches(FIRST_TOKEN))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Owner bootstrap token SecretRef is unavailable");
  }

  private BootstrapOwnerCredential credential(Path tokenFile) {
    IdentityInvitationProperties properties = new IdentityInvitationProperties();
    properties.bootstrapOwner().setTokenFile(tokenFile.toString());
    return new BootstrapOwnerCredential(properties);
  }

  private Path privateTokenFile(String token) throws IOException {
    Path tokenFile = temporaryDirectory.resolve("bootstrap-owner.token");
    Files.writeString(tokenFile, token + System.lineSeparator());
    setPrivatePermissions(tokenFile);
    return tokenFile;
  }

  private void setPrivatePermissions(Path tokenFile) throws IOException {
    try {
      Files.setPosixFilePermissions(
          tokenFile,
          Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
    } catch (UnsupportedOperationException ignored) {
      // The production component keeps its no-symlink/regular-file checks on non-POSIX systems.
    }
  }
}

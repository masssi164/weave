package com.massimotter.weave.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CollaborationJourneyTest {
  @TempDir Path temporaryDirectory;

  @Test
  void retainsActualFailedProcessOutputPrivatelyWithoutPublishingItsContents() throws Exception {
    Path root = temporaryDirectory.toRealPath();
    Path published = Files.createDirectories(root.resolve("build/test-app"));
    Path parent = root.resolve("client/build/private-ios-qualification");
    Path script = root.resolve("control.sh");
    Files.writeString(script, "printf '%s\\n' 'private-credential-fixture' "
        + "'WEAVE_COMPOSE_ERROR collaboration service control Docker operation failed'\nexit 7\n");
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    PrintStream original = System.out;
    try {
      System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
      assertThatThrownBy(() -> CollaborationJourney.control("collaboration-restart-proof", "expected-marker",
          script, parent, "owned-run", "a".repeat(40)))
          .isInstanceOf(ProductFlowException.class)
          .hasMessage("collaboration service control failed reason=docker-operation");
    } finally {
      System.setOut(original);
    }
    try (var logs = Files.list(parent); var artifacts = Files.list(published)) {
      var retained = logs.toList();
      assertThat(retained).hasSize(1);
      assertThat(Files.readString(retained.getFirst())).contains("private-credential-fixture");
      assertThat(Files.getPosixFilePermissions(retained.getFirst()))
          .isEqualTo(PosixFilePermissions.fromString("rw-------"));
      assertThat(artifacts.toList()).isEmpty();
    }
    assertThat(Files.getPosixFilePermissions(parent)).isEqualTo(PosixFilePermissions.fromString("rwx------"));
    assertThat(captured.toString(StandardCharsets.UTF_8)).contains("exitCode=7 reason=docker-operation")
        .doesNotContain("private-credential-fixture", "WEAVE_COMPOSE_ERROR");
  }

  @Test
  void removesSuccessfulProcessTranscriptAndRejectsUnsafePrivateParents() throws Exception {
    Path root = temporaryDirectory.toRealPath();
    Path parent = root.resolve("private");
    Path script = root.resolve("control.sh");
    Files.writeString(script, "printf '%s\\n' 'expected-marker'\n");
    CollaborationJourney.control("chat-provider-start-proof", "expected-marker", script, parent,
        "owned-run", "a".repeat(40));
    try (var files = Files.list(parent)) {
      assertThat(files.toList()).isEmpty();
    }
    Files.setPosixFilePermissions(parent, PosixFilePermissions.fromString("rwxr-xr-x"));
    assertThatThrownBy(() -> CollaborationJourney.control("chat-provider-start-proof", "expected-marker",
        script, parent, "owned-run", "a".repeat(40))).isInstanceOf(ProductFlowException.class);
    Path link = root.resolve("private-alias");
    Files.createSymbolicLink(link, parent);
    assertThatThrownBy(() -> CollaborationJourney.control("chat-provider-start-proof", "expected-marker",
        script, link, "owned-run", "a".repeat(40))).isInstanceOf(ProductFlowException.class);
  }

  @Test
  void classifiesOnlyAllowlistedServiceControlFailures() {
    assertThat(
            CollaborationJourney.supportSafeControlFailure(
                "WEAVE_COMPOSE_ERROR collaboration service control exceeded its bounded timeout"))
        .isEqualTo("control-timeout");
    assertThat(
            CollaborationJourney.supportSafeControlFailure(
                "WEAVE_COMPOSE_ERROR collaboration service control Docker operation exceeded its bounded timeout"))
        .isEqualTo("docker-timeout");
    assertThat(
            CollaborationJourney.supportSafeControlFailure(
                "WEAVE_COMPOSE_ERROR collaboration service restart identity did not advance exactly"))
        .isEqualTo("restart-identity");
  }

  @Test
  void doesNotExposeUntrustedControlOutput() {
    assertThat(
            CollaborationJourney.supportSafeControlFailure(
                "WEAVE_COMPOSE_ERROR provider payload token=secret https://private.example"))
        .isEqualTo("unspecified");
    assertThat(CollaborationJourney.supportSafeControlFailure(
        "private WEAVE_COMPOSE_ERROR collaboration service control Docker operation failed"))
        .isEqualTo("unspecified");
    assertThat(CollaborationJourney.supportSafeControlFailure(
        "WEAVE_COMPOSE_ERROR collaboration service control Docker operation failed token=private"))
        .isEqualTo("unspecified");
  }
}

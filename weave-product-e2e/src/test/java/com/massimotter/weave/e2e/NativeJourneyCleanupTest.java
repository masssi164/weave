package com.massimotter.weave.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NativeJourneyCleanupTest {
  @TempDir Path directory;

  @Test
  void preservesPrimaryFailureAndAttemptsEveryPrivateFileAfterProcessCleanupFails() throws Exception {
    Process process = new ProcessBuilder("python3", "-c",
        "import signal,time; signal.signal(signal.SIGTERM, signal.SIG_IGN); print('ready', flush=True); time.sleep(60)")
        .redirectError(ProcessBuilder.Redirect.DISCARD).start();
    try {
      assertThat(process.inputReader().readLine()).isEqualTo("ready");
      Path blocked = Files.createDirectory(directory.resolve("blocked"));
      Files.writeString(blocked.resolve("retained"), "fixture");
      Path removable = Files.writeString(directory.resolve("owned-input"), "fixture");
      Path unrelated = Files.writeString(directory.resolve("other-run"), "fixture");
      ProductFlowException primary = new ProductFlowException("fixture authorization read denial failed");

      FreshProductFlow.cleanupNativeJourney(process, List.of(blocked, removable), primary, Duration.ofMillis(25));

      assertThat(primary).hasMessage("fixture authorization read denial failed");
      assertThat(primary.getSuppressed()).singleElement().satisfies(cleanup -> {
        assertThat(cleanup).hasMessage("native qualification process cleanup failed");
        assertThat(cleanup.getSuppressed()).singleElement().isInstanceOfSatisfying(
            ProductFlowException.class, failure -> assertThat(failure).hasMessage("native private input cleanup failed"));
      });
      assertThat(process.isAlive()).isFalse();
      assertThat(removable).doesNotExist();
      assertThat(blocked.resolve("retained")).exists();
      assertThat(unrelated).exists();
    } finally {
      process.destroyForcibly().waitFor();
    }
  }

  @Test
  void interruptedCallerStillStopsItsChildDeletesPrivateInputAndRetainsInterruption() throws Exception {
    Process process = new ProcessBuilder("sleep", "60").start();
    Path input = Files.writeString(directory.resolve("owned-input"), "fixture");
    InterruptedException cause = new InterruptedException("fixture interruption");
    ProductFlowException primary = new ProductFlowException("native Flutter acceptance was interrupted", cause);
    Thread.currentThread().interrupt();
    try {
      FreshProductFlow.cleanupNativeJourney(process, List.of(input), primary, Duration.ofSeconds(2));
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
      assertThat(primary).hasCause(cause).hasNoSuppressedExceptions();
      assertThat(process.isAlive()).isFalse();
      assertThat(input).doesNotExist();
    } finally {
      Thread.interrupted();
      process.destroyForcibly().waitFor();
    }
  }

  @Test
  void successfulJourneyCannotHidePrivateCleanupFailure() throws Exception {
    Path blocked = Files.createDirectory(directory.resolve("blocked"));
    Files.writeString(blocked.resolve("retained"), "fixture");
    Path removable = Files.writeString(directory.resolve("owned-input"), "fixture");
    assertThatThrownBy(() -> FreshProductFlow.cleanupNativeJourney(
        null, List.of(blocked, removable), null, Duration.ofSeconds(2)))
        .isInstanceOf(ProductFlowException.class).hasMessage("native private input cleanup failed");
    assertThat(removable).doesNotExist();
  }
}

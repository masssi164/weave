package com.massimotter.weave.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BoundedProcessTreeTest {

  @Test
  void preservesTimeoutFailureWhenProcessCleanupFails() {
    Process process = startCompletedProcess();
    try {
      ProductFlowException timeout = new ProductFlowException("fixture operation timed out");

      ProductFlowException failure =
          BoundedProcessTree.terminatePreservingFailure(process, Duration.ZERO, timeout);

      assertThat(failure).isSameAs(timeout).hasMessage("fixture operation timed out");
      assertCleanupFailureIsSuppressed(failure);
    } finally {
      process.destroyForcibly();
    }
  }

  @Test
  void preservesInterruptionWhenProcessCleanupFails() {
    InterruptedException interrupted = new InterruptedException("fixture interruption");
    Process process = startCompletedProcess();
    try {
      ProductFlowException failure =
          BoundedProcessTree.interruptedFailure(
              process,
              Duration.ZERO,
              "fixture operation was interrupted",
              interrupted);

      assertThat(failure)
          .hasMessage("fixture operation was interrupted")
          .hasCause(interrupted);
      assertCleanupFailureIsSuppressed(failure);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
      process.destroyForcibly();
    }
  }

  private static Process startCompletedProcess() {
    try {
      return new ProcessBuilder()
          .command("true")
          .redirectOutput(ProcessBuilder.Redirect.DISCARD)
          .redirectError(ProcessBuilder.Redirect.DISCARD)
          .start();
    } catch (java.io.IOException unexpected) {
      throw new AssertionError(unexpected);
    }
  }

  private static void assertCleanupFailureIsSuppressed(ProductFlowException failure) {
    assertThat(failure.getSuppressed())
        .singleElement()
        .isInstanceOfSatisfying(
            IllegalArgumentException.class,
            cleanup -> assertThat(cleanup).hasMessage("process cleanup timeout must be positive"));
  }

  @Test
  void forciblyTerminatesTheObservedRootAndDescendantProcesses() throws Exception {
    Process process =
        new ProcessBuilder(
                "bash",
                "-c",
                "trap '' TERM; bash -c 'trap \"\" TERM; sleep 60 & wait' & wait")
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start();
    List<ProcessHandle> observed = new ArrayList<>();
    try {
      long discoveryDeadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
      while (System.nanoTime() < discoveryDeadline) {
        observed = process.toHandle().descendants().toList();
        if (observed.size() >= 2) {
          break;
        }
        Thread.sleep(25);
      }
      assertThat(observed).hasSizeGreaterThanOrEqualTo(2);

      long started = System.nanoTime();
      BoundedProcessTree.terminate(process, Duration.ofSeconds(1));
      Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

      assertThat(elapsed).isLessThan(Duration.ofSeconds(3));
      assertThat(process.isAlive()).isFalse();
      assertThat(observed).allMatch(handle -> !handle.isAlive());
    } finally {
      process.destroyForcibly();
      observed.forEach(ProcessHandle::destroyForcibly);
    }
  }
}

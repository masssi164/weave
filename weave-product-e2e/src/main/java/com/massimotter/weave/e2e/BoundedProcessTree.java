package com.massimotter.weave.e2e;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Terminates one spawned process tree against one absolute, bounded deadline. */
final class BoundedProcessTree {
  private static final long POLL_NANOS = TimeUnit.MILLISECONDS.toNanos(25);

  private BoundedProcessTree() {}

  static void terminate(Process process, Duration timeout) {
    if (timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException("process cleanup timeout must be positive");
    }

    ProcessHandle root = process.toHandle();
    List<ProcessHandle> observed = new ArrayList<>();
    observed.add(root);
    root.descendants().forEach(observed::add);

    root.destroyForcibly();
    observed.stream().skip(1).forEach(ProcessHandle::destroyForcibly);

    long timeoutNanos;
    try {
      timeoutNanos = timeout.toNanos();
    } catch (ArithmeticException overflow) {
      timeoutNanos = Long.MAX_VALUE;
    }
    long started = System.nanoTime();
    boolean interrupted = false;
    try {
      while (observed.stream().anyMatch(ProcessHandle::isAlive)) {
        long elapsed = System.nanoTime() - started;
        if (elapsed >= timeoutNanos) {
          throw new ProductFlowException("bounded child process cleanup did not complete");
        }
        long remaining = timeoutNanos - elapsed;
        try {
          TimeUnit.NANOSECONDS.sleep(Math.min(POLL_NANOS, remaining));
        } catch (InterruptedException ignored) {
          interrupted = true;
        }
      }
    } finally {
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }
}

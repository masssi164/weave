package com.massimotter.weave.backend.runner.application;

import com.massimotter.weave.backend.runner.application.RunnerTaskStore.Claim;
import com.massimotter.weave.backend.runner.application.RunnerTaskStore.Lease;
import com.massimotter.weave.backend.runner.application.RunnerTaskStore.NewTask;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** Coordinates post-commit enqueue wake-ups and bounded, transaction-free claim waits. */
public final class RunnerTaskQueue {

    private final RunnerTaskStore tasks;
    private final RunnerTaskAvailabilitySignal availability;
    private final Clock clock;

    public RunnerTaskQueue(
            RunnerTaskStore tasks,
            RunnerTaskAvailabilitySignal availability,
            Clock clock) {
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.availability = Objects.requireNonNull(availability, "availability");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void enqueue(NewTask task) {
        tasks.enqueue(Objects.requireNonNull(task, "task"));
        availability.signal();
    }

    /**
     * Performs an immediate authoritative database claim and then waits only on a non-authoritative
     * availability revision. Every retry refreshes the server timestamp while preserving the
     * authenticated Runner and persisted public-bundle identity.
     */
    public Optional<Lease> claim(Claim request, Duration maximumWait) {
        Claim validated = Objects.requireNonNull(request, "request");
        Duration wait = Objects.requireNonNull(maximumWait, "maximumWait");
        if (wait.isNegative() || wait.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException("maximumWait must be between zero and 30 seconds");
        }
        long maximumWaitNanos = wait.toNanos();
        long deadlineNanos = System.nanoTime() + maximumWaitNanos;

        while (true) {
            // Observe before the authoritative database check. A signal between this read and
            // awaitChange changes the revision and therefore cannot be lost.
            long observedRevision = availability.revision();
            Claim current = new Claim(
                    validated.organizationRef(),
                    validated.runnerId(),
                    validated.publicBundleDigest(),
                    clock.instant(),
                    validated.leaseDuration());
            Optional<Lease> lease = tasks.claim(current);
            if (lease.isPresent()) {
                return lease;
            }

            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                return Optional.empty();
            }

            try {
                availability.awaitChange(observedRevision, Duration.ofNanos(remainingNanos));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new ClaimInterruptedException(interrupted);
            }
        }
    }

    public static final class ClaimInterruptedException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public ClaimInterruptedException(InterruptedException cause) {
            super("bounded Runner task claim was interrupted", cause);
        }
    }
}

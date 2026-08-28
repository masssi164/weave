package com.massimotter.weave.backend.runner.application;

import com.massimotter.weave.backend.runner.application.RunnerTaskStore.Claim;
import com.massimotter.weave.backend.runner.application.RunnerTaskStore.Lease;
import com.massimotter.weave.backend.runner.application.RunnerTaskStore.NewTask;
import com.massimotter.weave.backend.runner.domain.RunnerControl.CapabilityRef;
import com.massimotter.weave.backend.runner.domain.RunnerControl.RunnerId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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
        throw new UnsupportedOperationException(
                "post-commit task signalling is the current red TDD boundary");
    }

    public Optional<Lease> claim(LongPollClaim request) {
        throw new UnsupportedOperationException(
                "bounded transaction-free claim waiting is the current red TDD boundary");
    }

    public record LongPollClaim(
            String organizationRef,
            RunnerId runnerId,
            String bundleDigest,
            Set<CapabilityRef> capabilities,
            Duration leaseDuration,
            Duration maximumWait) {

        public LongPollClaim {
            Claim validated = new Claim(
                    organizationRef,
                    runnerId,
                    bundleDigest,
                    capabilities,
                    Instant.EPOCH,
                    leaseDuration);
            organizationRef = validated.organizationRef();
            runnerId = validated.runnerId();
            bundleDigest = validated.bundleDigest();
            capabilities = validated.capabilities();
            leaseDuration = validated.leaseDuration();
            maximumWait = Objects.requireNonNull(maximumWait, "maximumWait");
            if (maximumWait.isNegative() || maximumWait.compareTo(Duration.ofSeconds(30)) > 0) {
                throw new IllegalArgumentException("maximumWait must be between zero and 30 seconds");
            }
        }

        Claim at(Instant instant) {
            return new Claim(
                    organizationRef,
                    runnerId,
                    bundleDigest,
                    capabilities,
                    instant,
                    leaseDuration);
        }
    }

    public static final class ClaimInterruptedException extends RuntimeException {

        public ClaimInterruptedException(InterruptedException cause) {
            super("bounded Runner task claim was interrupted", cause);
        }
    }
}

package com.massimotter.weave.backend.runner.application;

import com.massimotter.weave.backend.runner.application.RunnerClaimHttpSemantics.ClaimHttpResponse;
import com.massimotter.weave.backend.runner.application.RunnerTaskStore.Lease;
import com.massimotter.weave.backend.runner.domain.RunnerControl.CapabilityRef;
import com.massimotter.weave.backend.runner.domain.RunnerControl.RunnerId;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Authenticated application boundary for one bounded Runner task claim. */
public final class RunnerTaskClaimService {

    private final RunnerTaskQueue queue;
    private final Clock clock;
    private final Duration leaseDuration;

    public RunnerTaskClaimService(
            RunnerTaskQueue queue,
            Clock clock,
            Duration leaseDuration) {
        this.queue = Objects.requireNonNull(queue, "queue");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.leaseDuration = Objects.requireNonNull(leaseDuration, "leaseDuration");
        if (leaseDuration.compareTo(Duration.ofSeconds(5)) < 0
                || leaseDuration.compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalArgumentException(
                    "leaseDuration must be between five seconds and five minutes");
        }
    }

    public ClaimHttpResponse<Lease> claim(
            RunnerWorkloadIdentity identity,
            List<String> preferHeaders,
            ClaimCommand command) {
        throw new UnsupportedOperationException(
                "authenticated Runner claim binding is the current red TDD boundary");
    }

    public record ClaimCommand(
            RunnerId runnerId,
            String bundleDigest,
            Set<CapabilityRef> capabilities,
            int availableSlots) {

        public ClaimCommand {
            runnerId = Objects.requireNonNull(runnerId, "runnerId");
            bundleDigest = Objects.requireNonNull(bundleDigest, "bundleDigest");
            if (!RunnerTaskStore.SHA256.matcher(bundleDigest).matches()) {
                throw new IllegalArgumentException("bundleDigest must be a sha256 digest");
            }
            capabilities = Set.copyOf(capabilities == null ? Set.of() : capabilities);
            if (capabilities.isEmpty()
                    || capabilities.size() > 128
                    || capabilities.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException(
                        "capabilities must contain between one and 128 values");
            }
            if (availableSlots < 1 || availableSlots > 1024) {
                throw new IllegalArgumentException(
                        "availableSlots must be between one and 1024");
            }
        }
    }
}

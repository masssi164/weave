package com.massimotter.weave.backend.runner.application;

import com.massimotter.weave.backend.runner.application.RunnerClaimHttpSemantics.ClaimHttpResponse;
import com.massimotter.weave.backend.runner.application.RunnerTaskStore.Lease;
import com.massimotter.weave.backend.runner.domain.RunnerControl.RunnerId;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

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
        RunnerWorkloadIdentity workload = Objects.requireNonNull(identity, "identity");
        ClaimCommand request = Objects.requireNonNull(command, "command");
        workload.requireActive(clock.instant());
        workload.requireRunner(request.runnerId());

        RunnerClaimHttpSemantics.ClaimPreference preference =
                RunnerClaimHttpSemantics.parsePrefer(preferHeaders);
        RunnerTaskStore.Claim claim = new RunnerTaskStore.Claim(
                workload.organizationRef(),
                workload.runnerId(),
                request.publicBundleDigest(),
                clock.instant(),
                leaseDuration);
        return RunnerClaimHttpSemantics.respond(
                queue.claim(claim, Duration.ofSeconds(preference.waitSeconds())),
                preference);
    }

    public record ClaimCommand(
            RunnerId runnerId,
            String publicBundleDigest,
            int availableSlots) {

        public ClaimCommand {
            runnerId = Objects.requireNonNull(runnerId, "runnerId");
            publicBundleDigest = Objects.requireNonNull(
                    publicBundleDigest,
                    "publicBundleDigest");
            if (!RunnerTaskStore.SHA256.matcher(publicBundleDigest).matches()) {
                throw new IllegalArgumentException(
                        "publicBundleDigest must be a sha256 digest");
            }
            if (availableSlots < 1 || availableSlots > 1024) {
                throw new IllegalArgumentException(
                        "availableSlots must be between one and 1024");
            }
        }
    }
}

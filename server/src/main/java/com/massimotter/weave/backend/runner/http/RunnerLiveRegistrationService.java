package com.massimotter.weave.backend.runner.http;

import com.massimotter.weave.backend.runner.application.RunnerCapabilityRegistry;
import com.massimotter.weave.backend.runner.application.RunnerCapabilityRegistry.AvailabilityObservation;
import com.massimotter.weave.backend.runner.application.RunnerCapabilityRegistry.AvailabilityResult;
import com.massimotter.weave.backend.runner.application.RunnerCapabilityRegistry.PublicBundlePublication;
import com.massimotter.weave.backend.runner.application.RunnerCapabilityRegistry.PublicationResult;
import com.massimotter.weave.backend.runner.application.RunnerWorkloadIdentity;
import com.massimotter.weave.backend.runner.domain.RunnerControl.RunnerId;
import com.massimotter.weave.backend.runner.domain.RunnerControl.RunnerState;
import com.massimotter.weave.backend.runner.http.RunnerPublicCapabilityBundleVerifier.PublicBundleRequest;
import java.time.Clock;
import java.util.Objects;

/** Application boundary for authenticated public-bundle publication and Runner liveness. */
public final class RunnerLiveRegistrationService {

    private final RunnerCapabilityRegistry registry;
    private final RunnerPublicCapabilityBundleVerifier verifier;
    private final Clock clock;

    public RunnerLiveRegistrationService(
            RunnerCapabilityRegistry registry,
            RunnerPublicCapabilityBundleVerifier verifier,
            Clock clock) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public PublicationResult publish(
            RunnerWorkloadIdentity identity,
            PublicBundleRequest request) {
        RunnerWorkloadIdentity workload = requireIdentity(identity);
        var verified = verifier.verify(request);
        return registry.publish(new PublicBundlePublication(
                workload.runnerId(),
                workload.organizationRef(),
                verified.bundleId(),
                verified.bundleVersion(),
                verified.bundleDigest(),
                verified.capabilities(),
                RunnerState.ENROLLING,
                1,
                0,
                clock.instant()));
    }

    public AvailabilityResult heartbeat(
            RunnerWorkloadIdentity identity,
            HeartbeatCommand command) {
        RunnerWorkloadIdentity workload = requireIdentity(identity);
        HeartbeatCommand request = Objects.requireNonNull(command, "command");
        workload.requireRunner(request.runnerId());
        return registry.observeAvailability(new AvailabilityObservation(
                workload.runnerId(),
                workload.organizationRef(),
                request.publicBundleDigest(),
                request.runnerVersion(),
                RunnerState.ONLINE,
                request.capacity(),
                request.runningTasks(),
                clock.instant()));
    }

    private RunnerWorkloadIdentity requireIdentity(RunnerWorkloadIdentity identity) {
        RunnerWorkloadIdentity workload = Objects.requireNonNull(identity, "identity");
        workload.requireUsableAt(clock.instant());
        return workload;
    }

    public record HeartbeatCommand(
            RunnerId runnerId,
            String runnerVersion,
            String publicBundleDigest,
            int runningTasks,
            int capacity) {
        public HeartbeatCommand {
            runnerId = Objects.requireNonNull(runnerId, "runnerId");
            runnerVersion = required(runnerVersion, "runnerVersion", 96);
            publicBundleDigest = required(publicBundleDigest, "publicBundleDigest", 71);
            if (!RunnerCapabilityRegistry.DIGEST.matcher(publicBundleDigest).matches()) {
                throw new IllegalArgumentException("publicBundleDigest must be a sha256 digest");
            }
            if (!RunnerCapabilityRegistry.VERSION.matcher(runnerVersion).matches()) {
                throw new IllegalArgumentException("runnerVersion must be semantic versioning");
            }
            if (capacity < 1 || capacity > 1024) {
                throw new IllegalArgumentException("capacity must be between one and 1024");
            }
            if (runningTasks < 0 || runningTasks > capacity) {
                throw new IllegalArgumentException("runningTasks must be between zero and capacity");
            }
        }
    }

    private static String required(String value, String field, int maximum) {
        if (value == null || value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException(field + " must not be blank or padded");
        }
        if (value.length() > maximum) {
            throw new IllegalArgumentException(field + " exceeds the supported bound");
        }
        return value;
    }
}

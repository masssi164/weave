package com.massimotter.weave.backend.runner.adapter;

import com.massimotter.weave.backend.runner.application.RunnerCapabilityRegistry.AvailabilityDisposition;
import com.massimotter.weave.backend.runner.application.RunnerCapabilityRegistry.AvailabilityObservation;
import com.massimotter.weave.backend.runner.application.RunnerCapabilityRegistry.RunnerSession;
import com.massimotter.weave.backend.runner.domain.RunnerControl.RunnerId;
import com.massimotter.weave.backend.runner.domain.RunnerControl.RunnerState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;

@Entity
@Table(name = "weave_runner_sessions")
class RunnerSessionJpaEntity {

    @Id
    @Column(name = "runner_id", nullable = false, length = 135, updatable = false)
    private String runnerId;

    @Column(name = "organization_ref", nullable = false, length = 256, updatable = false)
    private String organizationRef;

    @Column(name = "public_bundle_digest", nullable = false, length = 71)
    private String publicBundleDigest;

    @Column(name = "runner_version", nullable = false, length = 96)
    private String runnerVersion;

    @Column(name = "runner_state", nullable = false, length = 16)
    private String runnerState;

    @Column(name = "capacity", nullable = false)
    private int capacity;

    @Column(name = "running_tasks", nullable = false)
    private int runningTasks;

    @Column(name = "available_slots", nullable = false)
    private int availableSlots;

    @Column(name = "observed_at_utc", nullable = false)
    private OffsetDateTime observedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected RunnerSessionJpaEntity() {}

    static RunnerSessionJpaEntity create(AvailabilityObservation observation) {
        RunnerSessionJpaEntity entity = new RunnerSessionJpaEntity();
        entity.runnerId = observation.runnerId().value();
        entity.organizationRef = observation.organizationRef();
        entity.apply(observation);
        return entity;
    }

    AvailabilityDisposition observe(AvailabilityObservation observation) {
        requireIdentity(observation);
        if (observation.observedAt().isBefore(observedAt.toInstant())) {
            throw new IllegalStateException("stale Runner availability observation");
        }
        boolean same = publicBundleDigest.equals(observation.publicBundleDigest())
                && runnerVersion.equals(observation.runnerVersion())
                && runnerState.equals(observation.runnerState().name())
                && capacity == observation.capacity()
                && runningTasks == observation.runningTasks();
        if (observation.observedAt().equals(observedAt.toInstant())) {
            if (same) {
                return AvailabilityDisposition.IDEMPOTENT_REPLAY;
            }
            throw new IllegalStateException(
                    "conflicting Runner availability observation at the same observedAt");
        }
        apply(observation);
        return AvailabilityDisposition.UPDATED;
    }

    RunnerSession snapshot() {
        return new RunnerSession(
                new RunnerId(runnerId),
                organizationRef,
                publicBundleDigest,
                runnerVersion,
                RunnerState.valueOf(runnerState),
                capacity,
                runningTasks,
                availableSlots,
                observedAt.toInstant());
    }

    private void apply(AvailabilityObservation observation) {
        publicBundleDigest = observation.publicBundleDigest();
        runnerVersion = observation.runnerVersion();
        runnerState = observation.runnerState().name();
        capacity = observation.capacity();
        runningTasks = observation.runningTasks();
        availableSlots = observation.availableSlots();
        observedAt = RunnerPersistenceTime.utc(observation.observedAt());
    }

    private void requireIdentity(AvailabilityObservation observation) {
        if (!runnerId.equals(observation.runnerId().value())
                || !organizationRef.equals(observation.organizationRef())) {
            throw new IllegalArgumentException("Runner session identity mismatch");
        }
    }
}

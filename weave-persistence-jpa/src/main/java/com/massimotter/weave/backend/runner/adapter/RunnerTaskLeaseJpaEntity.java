package com.massimotter.weave.backend.runner.adapter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "weave_runner_task_leases",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_weave_runner_task_lease_fence",
                    columnNames = {"task_id", "fencing_token"}),
            @UniqueConstraint(
                    name = "uk_weave_runner_task_lease_attempt",
                    columnNames = {"task_id", "attempt_number"})
        },
        indexes = @Index(name = "ix_weave_runner_lease_task", columnList = "task_id,lease_state"))
class RunnerTaskLeaseJpaEntity {

    @Id
    @Column(name = "lease_id", nullable = false, updatable = false)
    private UUID leaseId;

    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;

    @Column(name = "attempt_id", nullable = false, updatable = false)
    private UUID attemptId;

    @Column(name = "attempt_number", nullable = false, updatable = false)
    private int attemptNumber;

    @Column(name = "runner_id", nullable = false, length = 135, updatable = false)
    private String runnerId;

    @Column(name = "fencing_token", nullable = false, updatable = false)
    private long fencingToken;

    @Column(name = "lease_state", nullable = false, length = 32)
    private String state;

    @Column(name = "issued_at_utc", nullable = false, updatable = false)
    private OffsetDateTime issuedAt;

    @Column(name = "expires_at_utc", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "closed_at_utc")
    private OffsetDateTime closedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected RunnerTaskLeaseJpaEntity() {}

    static RunnerTaskLeaseJpaEntity create(
            RunnerTaskJpaEntity task,
            RunnerTaskJpaEntity.LeaseCoordinates coordinates) {
        RunnerTaskLeaseJpaEntity entity = new RunnerTaskLeaseJpaEntity();
        entity.leaseId = coordinates.leaseId();
        entity.taskId = task.taskId();
        entity.attemptId = coordinates.attemptId();
        entity.attemptNumber = coordinates.attempt();
        entity.runnerId = coordinates.runnerId().value();
        entity.fencingToken = coordinates.fencingToken();
        entity.state = "ACTIVE";
        entity.issuedAt = RunnerPersistenceTime.utc(coordinates.issuedAt());
        entity.expiresAt = RunnerPersistenceTime.utc(coordinates.expiresAt());
        return entity;
    }

    boolean active() {
        return "ACTIVE".equals(state);
    }

    void extend(Instant instant) {
        if (!active()) {
            throw new IllegalStateException("only an active lease can be extended");
        }
        OffsetDateTime requested = RunnerPersistenceTime.utc(instant);
        if (requested.isBefore(expiresAt)) {
            throw new IllegalArgumentException("a lease extension must not shorten the lease");
        }
        expiresAt = requested;
    }

    void expire(Instant instant) {
        state = "EXPIRED";
        closedAt = RunnerPersistenceTime.utc(instant);
    }

    void complete(Instant instant) {
        state = "COMPLETED";
        closedAt = RunnerPersistenceTime.utc(instant);
    }
}

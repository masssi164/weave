package com.massimotter.weave.backend.runner.adapter;

import com.massimotter.weave.backend.runner.application.RunnerTaskStore;
import com.massimotter.weave.backend.runner.domain.RunnerControl.TaskState;
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
        name = "weave_runner_task_attempts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_weave_runner_task_attempt",
                columnNames = {"task_id", "attempt_number"}),
        indexes = @Index(name = "ix_weave_runner_attempt_task", columnList = "task_id,attempt_number"))
class RunnerTaskAttemptJpaEntity {

    @Id
    @Column(name = "attempt_id", nullable = false, updatable = false)
    private UUID attemptId;

    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;

    @Column(name = "attempt_number", nullable = false, updatable = false)
    private int attemptNumber;

    @Column(name = "lease_id", nullable = false, updatable = false)
    private UUID leaseId;

    @Column(name = "runner_id", nullable = false, length = 135, updatable = false)
    private String runnerId;

    @Column(name = "fencing_token", nullable = false, updatable = false)
    private long fencingToken;

    @Column(name = "attempt_state", nullable = false, length = 32)
    private String state;

    @Column(name = "started_at_utc", nullable = false, updatable = false)
    private OffsetDateTime startedAt;

    @Column(name = "completed_at_utc")
    private OffsetDateTime completedAt;

    @Column(name = "outcome_digest", length = 71)
    private String outcomeDigest;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected RunnerTaskAttemptJpaEntity() {}

    static RunnerTaskAttemptJpaEntity create(
            RunnerTaskJpaEntity task,
            RunnerTaskJpaEntity.LeaseCoordinates coordinates) {
        RunnerTaskAttemptJpaEntity entity = new RunnerTaskAttemptJpaEntity();
        entity.attemptId = coordinates.attemptId();
        entity.taskId = task.taskId();
        entity.attemptNumber = coordinates.attempt();
        entity.leaseId = coordinates.leaseId();
        entity.runnerId = coordinates.runnerId().value();
        entity.fencingToken = coordinates.fencingToken();
        entity.state = TaskState.LEASED.name();
        entity.startedAt = RunnerPersistenceTime.utc(coordinates.issuedAt());
        return entity;
    }

    void heartbeat() {
        if (TaskState.LEASED.name().equals(state)) {
            state = TaskState.RUNNING.name();
            return;
        }
        if (!TaskState.RUNNING.name().equals(state)) {
            throw new IllegalStateException("only an active task attempt can receive a heartbeat");
        }
    }

    void loseLease(Instant instant) {
        state = "LEASE_LOST";
        completedAt = RunnerPersistenceTime.utc(instant);
    }

    void complete(RunnerTaskStore.Completion completion) {
        state = completion.state().name();
        completedAt = RunnerPersistenceTime.utc(completion.completedAt());
        outcomeDigest = completion.outcomeDigest();
    }
}

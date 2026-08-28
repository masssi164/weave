package com.massimotter.weave.backend.runner.adapter;

import com.massimotter.weave.backend.runner.application.RunnerTaskStore;
import com.massimotter.weave.backend.runner.domain.RunnerControl;
import com.massimotter.weave.backend.runner.domain.RunnerControl.CapabilityId;
import com.massimotter.weave.backend.runner.domain.RunnerControl.CapabilityRef;
import com.massimotter.weave.backend.runner.domain.RunnerControl.RunnerId;
import com.massimotter.weave.backend.runner.domain.RunnerControl.TaskError;
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
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "weave_runner_tasks",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_weave_runner_task_idempotency",
                columnNames = {"organization_ref", "idempotency_key"}),
        indexes = {
            @Index(
                    name = "ix_weave_runner_task_claim",
                    columnList = "organization_ref,bundle_digest,capability_coordinate,task_state,available_at_utc,lease_expires_at_utc,priority,created_at_utc")
        })
class RunnerTaskJpaEntity {

    @Id
    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;

    @Column(name = "organization_ref", nullable = false, length = 256, updatable = false)
    private String organizationRef;

    @Column(name = "capability_id", nullable = false, length = 128, updatable = false)
    private String capabilityId;

    @Column(name = "capability_version", nullable = false, length = 96, updatable = false)
    private String capabilityVersion;

    @Column(name = "capability_coordinate", nullable = false, length = 225, updatable = false)
    private String capabilityCoordinate;

    @Column(name = "bundle_digest", nullable = false, length = 71, updatable = false)
    private String bundleDigest;

    @Column(name = "idempotency_key", nullable = false, length = 256, updatable = false)
    private String idempotencyKey;

    @Column(name = "payload_json", nullable = false, length = Integer.MAX_VALUE, updatable = false)
    private String payloadJson;

    @Column(name = "context_refs_json", nullable = false, length = Integer.MAX_VALUE, updatable = false)
    private String contextRefsJson;

    @Column(name = "resource_grants_json", nullable = false, length = Integer.MAX_VALUE, updatable = false)
    private String resourceGrantsJson;

    @Column(name = "priority", nullable = false, updatable = false)
    private int priority;

    @Column(name = "created_at_utc", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "available_at_utc", nullable = false)
    private OffsetDateTime availableAt;

    @Column(name = "deadline_at_utc", nullable = false, updatable = false)
    private OffsetDateTime deadlineAt;

    @Column(name = "traceparent", length = 55, updatable = false)
    private String traceparent;

    @Column(name = "task_state", nullable = false, length = 32)
    private String state;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "fencing_token", nullable = false)
    private long fencingToken;

    @Column(name = "current_attempt_id")
    private UUID currentAttemptId;

    @Column(name = "current_lease_id")
    private UUID currentLeaseId;

    @Column(name = "current_runner_id", length = 135)
    private String currentRunnerId;

    @Column(name = "lease_issued_at_utc")
    private OffsetDateTime leaseIssuedAt;

    @Column(name = "lease_expires_at_utc")
    private OffsetDateTime leaseExpiresAt;

    @Column(name = "cancel_requested_at_utc")
    private OffsetDateTime cancelRequestedAt;

    @Column(name = "cancel_reason_code", length = 64)
    private String cancelReasonCode;

    @Column(name = "outcome_digest", length = 71)
    private String terminalOutcomeDigest;

    @Column(name = "result_json", length = Integer.MAX_VALUE)
    private String resultJson;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "error_retryable")
    private Boolean errorRetryable;

    @Column(name = "completed_at_utc")
    private OffsetDateTime completedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected RunnerTaskJpaEntity() {}

    static RunnerTaskJpaEntity create(RunnerTaskStore.NewTask task) {
        RunnerTaskJpaEntity entity = new RunnerTaskJpaEntity();
        entity.taskId = task.taskId();
        entity.organizationRef = task.organizationRef();
        entity.capabilityId = task.capability().id().value();
        entity.capabilityVersion = task.capability().version();
        entity.capabilityCoordinate = task.capability().coordinate();
        entity.bundleDigest = task.bundleDigest();
        entity.idempotencyKey = task.idempotencyKey();
        entity.payloadJson = task.payloadJson();
        entity.contextRefsJson = task.contextRefsJson();
        entity.resourceGrantsJson = task.resourceGrantsJson();
        entity.priority = task.priority();
        entity.createdAt = RunnerPersistenceTime.utc(task.createdAt());
        entity.availableAt = RunnerPersistenceTime.utc(task.availableAt());
        entity.deadlineAt = RunnerPersistenceTime.utc(task.deadline());
        entity.traceparent = task.traceparent();
        entity.state = TaskState.READY.name();
        return entity;
    }

    boolean matches(RunnerTaskStore.NewTask task) {
        return taskId.equals(task.taskId())
                && organizationRef.equals(task.organizationRef())
                && capabilityCoordinate.equals(task.capability().coordinate())
                && bundleDigest.equals(task.bundleDigest())
                && idempotencyKey.equals(task.idempotencyKey())
                && payloadJson.equals(task.payloadJson())
                && contextRefsJson.equals(task.contextRefsJson())
                && resourceGrantsJson.equals(task.resourceGrantsJson())
                && priority == task.priority()
                && createdAt.toInstant().equals(RunnerPersistenceTime.instant(task.createdAt()))
                && availableAt.toInstant().equals(RunnerPersistenceTime.instant(task.availableAt()))
                && deadlineAt.toInstant().equals(RunnerPersistenceTime.instant(task.deadline()))
                && Objects.equals(traceparent, task.traceparent());
    }

    boolean claimableBy(RunnerTaskStore.Claim claim) {
        Instant now = claim.now();
        TaskState current = TaskState.valueOf(state);
        boolean stateEligible = current == TaskState.READY
                || ((current == TaskState.LEASED || current == TaskState.RUNNING)
                        && hasExpiredLease(now));
        return stateEligible
                && cancelRequestedAt == null
                && organizationRef.equals(claim.organizationRef())
                && bundleDigest.equals(claim.bundleDigest())
                && claim.capabilityCoordinates().contains(capabilityCoordinate)
                && !availableAt.toInstant().isAfter(now)
                && deadlineAt.toInstant().isAfter(now);
    }

    boolean hasExpiredLease(Instant now) {
        return currentLeaseId != null
                && leaseExpiresAt != null
                && !leaseExpiresAt.toInstant().isAfter(now);
    }

    LeaseCoordinates beginLease(RunnerTaskStore.Claim claim) {
        attemptCount++;
        fencingToken++;
        currentAttemptId = UUID.randomUUID();
        currentLeaseId = UUID.randomUUID();
        currentRunnerId = claim.runnerId().value();
        leaseIssuedAt = RunnerPersistenceTime.utc(claim.now());
        Instant requestedExpiry = claim.now().plus(claim.leaseDuration());
        Instant boundedExpiry = requestedExpiry.isAfter(deadlineAt.toInstant())
                ? deadlineAt.toInstant()
                : requestedExpiry;
        leaseExpiresAt = RunnerPersistenceTime.utc(boundedExpiry);
        state = TaskState.LEASED.name();
        return new LeaseCoordinates(
                currentAttemptId,
                currentLeaseId,
                attemptCount,
                fencingToken,
                claim.runnerId(),
                claim.now(),
                boundedExpiry);
    }

    RunnerTaskStore.Lease toLease() {
        return new RunnerTaskStore.Lease(
                taskId,
                currentLeaseId,
                fencingToken,
                new RunnerId(currentRunnerId),
                new CapabilityRef(new CapabilityId(capabilityId), capabilityVersion),
                bundleDigest,
                attemptCount,
                idempotencyKey,
                payloadJson,
                contextRefsJson,
                resourceGrantsJson,
                leaseIssuedAt.toInstant(),
                leaseExpiresAt.toInstant(),
                deadlineAt.toInstant(),
                traceparent);
    }

    HeartbeatCoordinates heartbeat(RunnerTaskStore.Heartbeat heartbeat) {
        requireCurrentFence(heartbeat.leaseId(), heartbeat.fencingToken());
        requireCurrentRunner(heartbeat.runnerId());
        if (!leaseActiveAt(heartbeat.observedAt())) {
            throw new RunnerControl.StaleTaskLeaseException(taskId);
        }

        state = TaskState.RUNNING.name();
        Instant currentExpiry = leaseExpiresAt.toInstant();
        if (cancelRequestedAt != null) {
            return new HeartbeatCoordinates(currentExpiry, true);
        }

        Instant requestedExpiry = heartbeat.observedAt().plus(heartbeat.leaseDuration());
        Instant deadline = deadlineAt.toInstant();
        Instant boundedExpiry = requestedExpiry.isAfter(deadline) ? deadline : requestedExpiry;
        Instant effectiveExpiry = boundedExpiry.isAfter(currentExpiry) ? boundedExpiry : currentExpiry;
        leaseExpiresAt = RunnerPersistenceTime.utc(effectiveExpiry);
        return new HeartbeatCoordinates(effectiveExpiry, false);
    }

    RunnerTaskStore.CancellationDisposition requestCancellation(
            RunnerTaskStore.CancellationRequest request) {
        if (!organizationRef.equals(request.organizationRef())) {
            throw new IllegalArgumentException("task does not exist");
        }
        if (terminal()) {
            throw new IllegalStateException("a terminal task cannot be cancelled");
        }
        if (cancelRequestedAt != null) {
            if (cancelReasonCode.equals(request.reasonCode())) {
                return RunnerTaskStore.CancellationDisposition.IDEMPOTENT_REPLAY;
            }
            throw new IllegalStateException(
                    "task cancellation was already requested with a different reason");
        }
        if (!leaseActiveAt(request.requestedAt())) {
            throw new IllegalStateException("task has no active lease to cancel");
        }
        cancelRequestedAt = RunnerPersistenceTime.utc(request.requestedAt());
        cancelReasonCode = request.reasonCode();
        return RunnerTaskStore.CancellationDisposition.APPLIED;
    }

    void requireCurrentFence(UUID leaseId, long presentedFencingToken) {
        if (!Objects.equals(currentLeaseId, leaseId) || fencingToken != presentedFencingToken) {
            throw new RunnerControl.StaleTaskLeaseException(taskId);
        }
    }

    private void requireCurrentRunner(RunnerId runnerId) {
        if (!Objects.equals(currentRunnerId, runnerId.value())) {
            throw new RunnerControl.StaleTaskLeaseException(taskId);
        }
    }

    boolean leaseActiveAt(Instant instant) {
        TaskState current = TaskState.valueOf(state);
        return (current == TaskState.LEASED || current == TaskState.RUNNING)
                && leaseIssuedAt != null
                && leaseExpiresAt != null
                && !instant.isBefore(leaseIssuedAt.toInstant())
                && instant.isBefore(leaseExpiresAt.toInstant())
                && !instant.isAfter(deadlineAt.toInstant());
    }

    void complete(RunnerTaskStore.Completion completion) {
        state = completion.state().name();
        terminalOutcomeDigest = completion.outcomeDigest();
        resultJson = completion.resultJson();
        TaskError error = completion.error();
        errorCode = error == null ? null : error.code();
        errorMessage = error == null ? null : error.message();
        errorRetryable = error == null ? null : error.retryable();
        completedAt = RunnerPersistenceTime.utc(completion.completedAt());
    }

    boolean terminal() {
        return TaskState.valueOf(state).terminal();
    }

    String outcomeDigest() {
        return terminalOutcomeDigest;
    }

    UUID currentAttemptId() {
        return currentAttemptId;
    }

    UUID currentLeaseId() {
        return currentLeaseId;
    }

    UUID taskId() {
        return taskId;
    }

    RunnerTaskStore.TaskSnapshot snapshot() {
        return new RunnerTaskStore.TaskSnapshot(
                taskId,
                TaskState.valueOf(state),
                attemptCount,
                fencingToken,
                currentLeaseId,
                currentRunnerId == null ? null : new RunnerId(currentRunnerId),
                leaseExpiresAt == null ? null : leaseExpiresAt.toInstant(),
                cancelRequestedAt != null,
                terminalOutcomeDigest);
    }

    record LeaseCoordinates(
            UUID attemptId,
            UUID leaseId,
            int attempt,
            long fencingToken,
            RunnerId runnerId,
            Instant issuedAt,
            Instant expiresAt) {}

    record HeartbeatCoordinates(Instant expiresAt, boolean cancelRequested) {}
}

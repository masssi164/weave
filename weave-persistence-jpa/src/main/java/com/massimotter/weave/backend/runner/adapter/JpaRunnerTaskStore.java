package com.massimotter.weave.backend.runner.adapter;

import com.massimotter.weave.backend.runner.application.RunnerTaskStore;
import com.massimotter.weave.backend.runner.domain.RunnerControl;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** Entity-first PostgreSQL task queue with registry-qualified skip-locked claims. */
public class JpaRunnerTaskStore implements RunnerTaskStore {

    private final EntityManager entityManager;

    public JpaRunnerTaskStore(EntityManager entityManager) {
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager");
    }

    @Override
    @Transactional
    public void enqueue(NewTask task) {
        Objects.requireNonNull(task, "task");
        RunnerTaskJpaEntity byId = entityManager.find(RunnerTaskJpaEntity.class, task.taskId());
        if (byId != null) {
            if (byId.matches(task)) {
                return;
            }
            throw new IllegalStateException("taskId already exists with different task content");
        }

        List<RunnerTaskJpaEntity> byIdempotencyKey = entityManager.createQuery(
                        """
                        select task
                        from RunnerTaskJpaEntity task
                        where task.organizationRef = :organizationRef
                          and task.idempotencyKey = :idempotencyKey
                        """,
                        RunnerTaskJpaEntity.class)
                .setParameter("organizationRef", task.organizationRef())
                .setParameter("idempotencyKey", task.idempotencyKey())
                .setMaxResults(1)
                .getResultList();
        if (!byIdempotencyKey.isEmpty()) {
            if (byIdempotencyKey.getFirst().matches(task)) {
                return;
            }
            throw new IllegalStateException(
                    "organization idempotency key already exists with different task content");
        }

        entityManager.persist(RunnerTaskJpaEntity.create(task));
        entityManager.flush();
    }

    @Override
    @Transactional
    public Optional<Lease> claim(Claim claim) {
        Objects.requireNonNull(claim, "claim");
        List<?> candidates = entityManager.createNativeQuery(
                        """
                        select task.task_id
                        from weave_runner_tasks task
                        join weave_runner_capability_definitions definition
                          on definition.organization_ref = task.organization_ref
                         and definition.capability_id = task.capability_id
                         and definition.capability_version = task.capability_version
                         and definition.contract_digest = task.capability_contract_digest
                        join weave_runner_capability_offerings offering
                          on offering.organization_ref = task.organization_ref
                         and offering.capability_definition_id = definition.capability_definition_id
                        where task.organization_ref = :organizationRef
                          and offering.runner_id = :runnerId
                          and offering.public_bundle_digest = :publicBundleDigest
                          and offering.active = true
                          and offering.runner_state in ('ONLINE', 'DEGRADED')
                          and offering.available_slots > 0
                          and task.cancel_requested_at_utc is null
                          and task.available_at_utc <= :now
                          and task.deadline_at_utc > :now
                          and (
                                task.task_state = 'READY'
                                or (
                                    task.task_state in ('LEASED', 'RUNNING')
                                    and task.lease_expires_at_utc <= :now
                                )
                          )
                        order by task.priority desc, task.created_at_utc, task.task_id
                        limit 1
                        for update of task skip locked
                        """)
                .setParameter("organizationRef", claim.organizationRef())
                .setParameter("runnerId", claim.runnerId().value())
                .setParameter("publicBundleDigest", claim.publicBundleDigest())
                .setParameter("now", RunnerPersistenceTime.utc(claim.now()))
                .getResultList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        UUID taskId = uuid(candidates.getFirst());
        RunnerTaskJpaEntity task =
                entityManager.find(RunnerTaskJpaEntity.class, taskId, LockModeType.PESSIMISTIC_WRITE);
        if (task == null || !task.claimableBy(claim)) {
            return Optional.empty();
        }

        expirePreviousLease(task, claim.now());
        RunnerTaskJpaEntity.LeaseCoordinates coordinates = task.beginLease(claim);
        RunnerTaskAttemptJpaEntity attempt = RunnerTaskAttemptJpaEntity.create(task, coordinates);
        RunnerTaskLeaseJpaEntity lease = RunnerTaskLeaseJpaEntity.create(task, coordinates);
        entityManager.persist(attempt);
        entityManager.persist(lease);
        entityManager.flush();
        return Optional.of(task.toLease());
    }

    @Override
    @Transactional
    public LeaseDirective heartbeat(Heartbeat heartbeat) {
        Objects.requireNonNull(heartbeat, "heartbeat");
        RunnerTaskJpaEntity task = entityManager.find(
                RunnerTaskJpaEntity.class,
                heartbeat.taskId(),
                LockModeType.PESSIMISTIC_WRITE);
        if (task == null) {
            throw new IllegalArgumentException("task does not exist");
        }

        RunnerTaskJpaEntity.HeartbeatCoordinates coordinates = task.heartbeat(heartbeat);
        RunnerTaskAttemptJpaEntity attempt = entityManager.find(
                RunnerTaskAttemptJpaEntity.class,
                task.currentAttemptId(),
                LockModeType.PESSIMISTIC_WRITE);
        RunnerTaskLeaseJpaEntity lease = entityManager.find(
                RunnerTaskLeaseJpaEntity.class,
                heartbeat.leaseId(),
                LockModeType.PESSIMISTIC_WRITE);
        if (attempt == null || lease == null || !lease.active()) {
            throw new RunnerControl.StaleTaskLeaseException(heartbeat.taskId());
        }

        attempt.heartbeat();
        lease.extend(coordinates.expiresAt());
        entityManager.flush();
        return new LeaseDirective(
                heartbeat.leaseId(),
                heartbeat.fencingToken(),
                coordinates.expiresAt(),
                coordinates.cancelRequested());
    }

    @Override
    @Transactional
    public CancellationDisposition requestCancellation(CancellationRequest request) {
        Objects.requireNonNull(request, "request");
        RunnerTaskJpaEntity task = entityManager.find(
                RunnerTaskJpaEntity.class,
                request.taskId(),
                LockModeType.PESSIMISTIC_WRITE);
        if (task == null) {
            throw new IllegalArgumentException("task does not exist");
        }
        CancellationDisposition disposition = task.requestCancellation(request);
        entityManager.flush();
        return disposition;
    }

    @Override
    @Transactional
    public CompletionDisposition complete(Completion completion) {
        Objects.requireNonNull(completion, "completion");
        RunnerTaskJpaEntity task = entityManager.find(
                RunnerTaskJpaEntity.class,
                completion.taskId(),
                LockModeType.PESSIMISTIC_WRITE);
        if (task == null) {
            throw new IllegalArgumentException("task does not exist");
        }

        if (task.terminal()) {
            task.requireCurrentFence(completion.leaseId(), completion.fencingToken());
            if (Objects.equals(task.outcomeDigest(), completion.outcomeDigest())) {
                return CompletionDisposition.IDEMPOTENT_REPLAY;
            }
            throw new IllegalStateException(
                    "the current lease already completed with a different outcome");
        }

        task.requireCurrentFence(completion.leaseId(), completion.fencingToken());
        if (!task.leaseActiveAt(completion.completedAt())) {
            throw new RunnerControl.StaleTaskLeaseException(completion.taskId());
        }

        RunnerTaskAttemptJpaEntity attempt = entityManager.find(
                RunnerTaskAttemptJpaEntity.class,
                task.currentAttemptId(),
                LockModeType.PESSIMISTIC_WRITE);
        RunnerTaskLeaseJpaEntity lease = entityManager.find(
                RunnerTaskLeaseJpaEntity.class,
                completion.leaseId(),
                LockModeType.PESSIMISTIC_WRITE);
        if (attempt == null || lease == null || !lease.active()) {
            throw new RunnerControl.StaleTaskLeaseException(completion.taskId());
        }

        task.complete(completion);
        attempt.complete(completion);
        lease.complete(completion.completedAt());
        entityManager.flush();
        return CompletionDisposition.APPLIED;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TaskSnapshot> find(UUID taskId) {
        Objects.requireNonNull(taskId, "taskId");
        return Optional.ofNullable(entityManager.find(RunnerTaskJpaEntity.class, taskId))
                .map(RunnerTaskJpaEntity::snapshot);
    }

    private void expirePreviousLease(RunnerTaskJpaEntity task, Instant now) {
        if (!task.hasExpiredLease(now)) {
            return;
        }
        RunnerTaskLeaseJpaEntity lease = entityManager.find(
                RunnerTaskLeaseJpaEntity.class,
                task.currentLeaseId(),
                LockModeType.PESSIMISTIC_WRITE);
        RunnerTaskAttemptJpaEntity attempt = entityManager.find(
                RunnerTaskAttemptJpaEntity.class,
                task.currentAttemptId(),
                LockModeType.PESSIMISTIC_WRITE);
        if (lease != null) {
            lease.expire(now);
        }
        if (attempt != null) {
            attempt.loseLease(now);
        }
    }

    private static UUID uuid(Object value) {
        if (value instanceof UUID result) {
            return result;
        }
        return UUID.fromString(Objects.requireNonNull(value, "taskId").toString());
    }
}

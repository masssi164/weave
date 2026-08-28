package com.massimotter.weave.backend.runner.adapter;

import com.massimotter.weave.backend.runner.application.RunnerTaskStore;
import jakarta.persistence.EntityManager;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL-backed task queue. The first TDD commit intentionally leaves behavior red. */
public class JpaRunnerTaskStore implements RunnerTaskStore {

    private final EntityManager entityManager;

    public JpaRunnerTaskStore(EntityManager entityManager) {
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager");
    }

    @Override
    @Transactional
    public void enqueue(NewTask task) {
        throw notImplemented();
    }

    @Override
    @Transactional
    public Optional<Lease> claim(Claim claim) {
        throw notImplemented();
    }

    @Override
    @Transactional
    public CompletionDisposition complete(Completion completion) {
        throw notImplemented();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TaskSnapshot> find(UUID taskId) {
        Objects.requireNonNull(taskId, "taskId");
        throw notImplemented();
    }

    private UnsupportedOperationException notImplemented() {
        return new UnsupportedOperationException(
                "PostgreSQL Runner task leasing is the current red TDD boundary for " + entityManager);
    }
}

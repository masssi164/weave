package com.massimotter.weave.backend.data.adapter.persistence.jpa;

import static com.massimotter.weave.backend.data.domain.CanonicalData.Checkpoint;
import static com.massimotter.weave.backend.data.domain.CanonicalData.CheckpointKey;
import static com.massimotter.weave.backend.data.transfer.CanonicalTransfer.CheckpointRepository;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class JpaTransferCheckpointAdapter implements CheckpointRepository {

    private final SpringDataTransferCheckpointRepository repository;
    private final Clock clock;

    public JpaTransferCheckpointAdapter(SpringDataTransferCheckpointRepository repository) {
        this(repository, Clock.systemUTC());
    }

    JpaTransferCheckpointAdapter(
            SpringDataTransferCheckpointRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Checkpoint> find(CheckpointKey key) {
        Objects.requireNonNull(key, "key must not be null");
        return repository.findById(id(key)).map(JpaTransferCheckpointEntity::canonicalCheckpoint);
    }

    @Override
    public void save(CheckpointKey key, Checkpoint checkpoint) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(checkpoint, "checkpoint must not be null");

        JpaTransferCheckpointEntity.JpaTransferCheckpointId id = id(key);
        JpaTransferCheckpointEntity entity = repository.findById(id)
                .map(existing -> update(existing, checkpoint))
                .orElseGet(() -> JpaTransferCheckpointEntity.from(key, checkpoint, clock.instant()));
        repository.saveAndFlush(entity);
    }

    private JpaTransferCheckpointEntity update(
            JpaTransferCheckpointEntity existing, Checkpoint next) {
        Checkpoint current = existing.canonicalCheckpoint();
        validateAdvance(current, next);
        if (!current.equals(next)) {
            existing.replaceCheckpoint(next, clock.instant());
        }
        return existing;
    }

    private static void validateAdvance(Checkpoint current, Checkpoint next) {
        if (current.complete() && !current.equals(next)) {
            throw new IllegalStateException("completed transfer checkpoint cannot advance");
        }
        if (next.sequence() < current.sequence()) {
            throw new IllegalStateException("transfer checkpoint cannot move backwards");
        }
        if (next.sequence() == current.sequence()
                && !next.complete()
                && !current.equals(next)) {
            throw new IllegalStateException("transfer checkpoint cursor cannot change without progress");
        }
    }

    private static JpaTransferCheckpointEntity.JpaTransferCheckpointId id(CheckpointKey key) {
        return new JpaTransferCheckpointEntity.JpaTransferCheckpointId(
                key.runId().value(), key.stage().name());
    }
}

package com.massimotter.weave.backend.matrix;

import com.massimotter.weave.backend.persistence.jpa.matrix.MatrixE2eeSnapshotJpaEntity;
import com.massimotter.weave.backend.persistence.jpa.matrix.MatrixE2eeSnapshotJpaRepository;
import java.time.Clock;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MatrixE2eeSnapshotStore {

    private final MatrixE2eeSnapshotJpaRepository repository;
    private final Clock clock;

    public MatrixE2eeSnapshotStore(
            ObjectProvider<MatrixE2eeSnapshotJpaRepository> repositoryProvider,
            ObjectProvider<Clock> clockProvider) {
        this.repository = repositoryProvider.getIfAvailable();
        this.clock = clockProvider.getIfAvailable(Clock::systemUTC);
    }

    @Transactional(readOnly = true)
    public Optional<SnapshotDocument> load(String tenantId) {
        if (repository == null) {
            return Optional.empty();
        }
        return repository.findById(tenantId)
                .map(entity -> new SnapshotDocument(entity.sequence(), entity.payloadJson()));
    }

    @Transactional
    public void save(String tenantId, long sequence, String payloadJson) {
        if (repository == null) {
            return;
        }
        var now = clock.instant();
        var observed = repository.findById(tenantId);
        MatrixE2eeSnapshotJpaEntity snapshot = observed.orElseGet(() ->
                new MatrixE2eeSnapshotJpaEntity(tenantId, sequence, payloadJson, now));
        if (observed.isPresent()) {
            snapshot.replace(sequence, payloadJson, now);
        }
        repository.saveAndFlush(snapshot);
    }

    public boolean durable() {
        return repository != null;
    }

    public record SnapshotDocument(long sequence, String payloadJson) {
    }
}

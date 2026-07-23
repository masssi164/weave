package com.massimotter.weave.backend.matrix;

import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import static java.util.Objects.requireNonNull;

/** Durable, optimistic-locking snapshot adapter for client-owned Matrix E2EE state. */
@Component
@Transactional(readOnly = true)
public class MatrixE2eeSnapshotStore {

    private final MatrixE2eeSnapshotJpaRepository snapshots;

    public MatrixE2eeSnapshotStore(MatrixE2eeSnapshotJpaRepository snapshots) {
        this.snapshots = requireNonNull(snapshots, "snapshots");
    }

    public Optional<SnapshotDocument> load(String tenantId) {
        return snapshots.findById(requireNonNull(tenantId, "tenantId"))
                .map(MatrixE2eeSnapshotJpaEntity::document);
    }

    @Transactional
    public void save(String tenantId, long sequence, String payloadJson) {
        String organization = requireNonNull(tenantId, "tenantId");
        MatrixE2eeSnapshotJpaEntity snapshot = snapshots.findById(organization)
                .orElseGet(() -> MatrixE2eeSnapshotJpaEntity.create(organization));
        snapshot.advance(sequence, requireNonNull(payloadJson, "payloadJson"), Instant.now());
        snapshots.saveAndFlush(snapshot);
    }

    public boolean durable() {
        return true;
    }

    public record SnapshotDocument(long sequence, String payloadJson) {
    }
}

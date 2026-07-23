package com.massimotter.weave.backend.matrix;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.data.jpa.repository.JpaRepository;

@Entity
@Table(name = "weave_matrix_e2ee_snapshots")
class MatrixE2eeSnapshotJpaEntity {

    @Id
    @Column(name = "tenant_id", nullable = false, length = 160, updatable = false)
    private String tenantId;

    @Column(name = "sequence_value", nullable = false)
    private long sequence;

    @Column(name = "payload_json", nullable = false)
    private String payloadJson;

    @Column(name = "updated_at_utc", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected MatrixE2eeSnapshotJpaEntity() {
    }

    static MatrixE2eeSnapshotJpaEntity create(String tenantId) {
        MatrixE2eeSnapshotJpaEntity entity = new MatrixE2eeSnapshotJpaEntity();
        entity.tenantId = tenantId;
        entity.sequence = -1;
        return entity;
    }

    void advance(long nextSequence, String nextPayload, Instant now) {
        if (nextSequence < sequence) {
            throw new IllegalArgumentException("Matrix E2EE snapshot sequence cannot move backwards.");
        }
        sequence = nextSequence;
        payloadJson = nextPayload;
        updatedAt = now.atOffset(ZoneOffset.UTC);
    }

    MatrixE2eeSnapshotStore.SnapshotDocument document() {
        return new MatrixE2eeSnapshotStore.SnapshotDocument(sequence, payloadJson);
    }
}

interface MatrixE2eeSnapshotJpaRepository
        extends JpaRepository<MatrixE2eeSnapshotJpaEntity, String> {
}

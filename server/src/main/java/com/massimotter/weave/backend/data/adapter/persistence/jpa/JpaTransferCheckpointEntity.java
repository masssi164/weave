package com.massimotter.weave.backend.data.adapter.persistence.jpa;

import static com.massimotter.weave.backend.data.domain.CanonicalData.Checkpoint;
import static com.massimotter.weave.backend.data.domain.CanonicalData.CheckpointKey;
import static com.massimotter.weave.backend.data.domain.CanonicalData.TransferRunId;
import static com.massimotter.weave.backend.data.domain.CanonicalData.TransferStage;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "weave_transfer_checkpoint")
public class JpaTransferCheckpointEntity {

    @EmbeddedId
    private JpaTransferCheckpointId id;

    @Column(name = "checkpoint_sequence", nullable = false)
    private long checkpointSequence;

    @Column(name = "checkpoint_cursor", length = 2048)
    private String checkpointCursor;

    @Column(name = "is_complete", nullable = false)
    private boolean complete;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected JpaTransferCheckpointEntity() {
        // JPA
    }

    private JpaTransferCheckpointEntity(
            JpaTransferCheckpointId id,
            long checkpointSequence,
            String checkpointCursor,
            boolean complete,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.checkpointSequence = checkpointSequence;
        this.checkpointCursor = checkpointCursor;
        this.complete = complete;
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    static JpaTransferCheckpointEntity from(
            CheckpointKey key, Checkpoint checkpoint, Instant updatedAt) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(checkpoint, "checkpoint must not be null");
        return new JpaTransferCheckpointEntity(
                new JpaTransferCheckpointId(key.runId().value(), key.stage().name()),
                checkpoint.sequence(),
                checkpoint.cursor(),
                checkpoint.complete(),
                updatedAt);
    }

    CheckpointKey canonicalKey() {
        return new CheckpointKey(
                new TransferRunId(id.runId),
                TransferStage.valueOf(id.transferStage));
    }

    Checkpoint canonicalCheckpoint() {
        return new Checkpoint(checkpointSequence, checkpointCursor, complete);
    }

    void replaceCheckpoint(Checkpoint checkpoint, Instant replacementTime) {
        Objects.requireNonNull(checkpoint, "checkpoint must not be null");
        checkpointSequence = checkpoint.sequence();
        checkpointCursor = checkpoint.cursor();
        complete = checkpoint.complete();
        updatedAt = Objects.requireNonNull(replacementTime, "replacementTime must not be null");
    }

    @Embeddable
    public static class JpaTransferCheckpointId implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        @Column(name = "run_id", nullable = false, length = 160)
        private String runId;

        @Column(name = "transfer_stage", nullable = false, length = 16)
        private String transferStage;

        protected JpaTransferCheckpointId() {
            // JPA
        }

        JpaTransferCheckpointId(String runId, String transferStage) {
            this.runId = Objects.requireNonNull(runId, "runId must not be null");
            this.transferStage = Objects.requireNonNull(transferStage, "transferStage must not be null");
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof JpaTransferCheckpointId that)) {
                return false;
            }
            return runId.equals(that.runId) && transferStage.equals(that.transferStage);
        }

        @Override
        public int hashCode() {
            return Objects.hash(runId, transferStage);
        }
    }
}

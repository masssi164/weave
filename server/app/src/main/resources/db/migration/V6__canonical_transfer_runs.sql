CREATE TABLE weave_transfer_runs (
    run_id VARCHAR(255) PRIMARY KEY,
    organization_ref VARCHAR(255) NOT NULL,
    canonical_model_version VARCHAR(80) NOT NULL,
    transfer_format_version INTEGER NOT NULL,
    state_revision BIGINT NOT NULL,
    run_status VARCHAR(32) NOT NULL,
    checkpoint_cursor VARCHAR(1024),
    checkpoint_sequence BIGINT,
    batches_applied BIGINT NOT NULL,
    items_applied BIGINT NOT NULL,
    last_aggregate_digest VARCHAR(128) NOT NULL,
    failure_reason VARCHAR(4000),
    updated_at_utc TIMESTAMPTZ NOT NULL,
    persistence_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_transfer_format_version_positive
        CHECK (transfer_format_version > 0),
    CONSTRAINT ck_transfer_state_revision_positive
        CHECK (state_revision > 0),
    CONSTRAINT ck_transfer_counters_non_negative
        CHECK (batches_applied >= 0 AND items_applied >= 0),
    CONSTRAINT ck_transfer_checkpoint_complete
        CHECK ((checkpoint_cursor IS NULL) = (checkpoint_sequence IS NULL)),
    CONSTRAINT ck_transfer_checkpoint_sequence_non_negative
        CHECK (checkpoint_sequence IS NULL OR checkpoint_sequence >= 0),
    CONSTRAINT ck_transfer_failure_state
        CHECK ((run_status = 'FAILED') = (failure_reason IS NOT NULL)),
    CONSTRAINT ck_transfer_status
        CHECK (run_status IN ('ACTIVE', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_transfer_runs_organization_status
    ON weave_transfer_runs (organization_ref, run_status);

CREATE TABLE weave_transfer_run_losses (
    run_id VARCHAR(255) NOT NULL,
    canonical_object_id VARCHAR(255) NOT NULL,
    field_key VARCHAR(255) NOT NULL,
    loss_class VARCHAR(32) NOT NULL,
    reason VARCHAR(2000) NOT NULL,
    CONSTRAINT pk_transfer_run_losses
        PRIMARY KEY (run_id, canonical_object_id, field_key),
    CONSTRAINT fk_transfer_run_losses_run
        FOREIGN KEY (run_id)
        REFERENCES weave_transfer_runs (run_id)
        ON DELETE CASCADE,
    CONSTRAINT ck_transfer_loss_class
        CHECK (loss_class IN (
            'PORTABLE',
            'LOSSY',
            'UNSUPPORTED',
            'MANUAL_REVIEW',
            'VENDOR_LOCKED',
            'ARCHIVE_ONLY'
        ))
);

CREATE TABLE weave_transfer_checkpoint (
    run_id VARCHAR(160) NOT NULL,
    transfer_stage VARCHAR(16) NOT NULL,
    checkpoint_sequence BIGINT NOT NULL,
    checkpoint_cursor VARCHAR(2048),
    is_complete BOOLEAN NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_weave_transfer_checkpoint PRIMARY KEY (run_id, transfer_stage),
    CONSTRAINT ck_weave_transfer_checkpoint_stage
        CHECK (transfer_stage IN ('IMPORT', 'EXPORT')),
    CONSTRAINT ck_weave_transfer_checkpoint_sequence
        CHECK (checkpoint_sequence >= 0),
    CONSTRAINT ck_weave_transfer_checkpoint_cursor
        CHECK (is_complete OR checkpoint_sequence = 0 OR checkpoint_cursor IS NOT NULL)
);

CREATE INDEX idx_weave_transfer_checkpoint_updated_at
    ON weave_transfer_checkpoint (updated_at);

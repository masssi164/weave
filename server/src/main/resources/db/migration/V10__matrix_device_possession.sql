-- An explicit Matrix device ID needs a verifier independent of the member bearer.
-- Existing keyed devices are deliberately not backfilled: adoption requires verified recovery.
CREATE TABLE weave_matrix_device_proofs (
    tenant_id varchar(160) NOT NULL,
    user_id varchar(512) NOT NULL,
    device_id varchar(128) NOT NULL,
    proof_hash varchar(64) NOT NULL,
    bound_at_utc timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, user_id, device_id),
    CHECK (proof_hash ~ '^[0-9a-f]{64}$')
);

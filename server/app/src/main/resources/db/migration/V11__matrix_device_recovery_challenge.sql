-- A legacy keyed Matrix device proves continuity before gaining a new possession verifier.
-- One live challenge per scoped device limits replay and unbounded challenge accumulation.
CREATE TABLE weave_matrix_device_recovery_challenges (
    tenant_id varchar(160) NOT NULL,
    user_id varchar(512) NOT NULL,
    device_id varchar(128) NOT NULL,
    challenge_id uuid NOT NULL,
    challenge_text text NOT NULL,
    proof_hash varchar(64) NOT NULL,
    expires_at_utc timestamptz NOT NULL,
    consumed_at_utc timestamptz,
    PRIMARY KEY (tenant_id, user_id, device_id),
    CHECK (proof_hash ~ '^[0-9a-f]{64}$')
);

CREATE TABLE weave_runner_sessions (
    runner_id VARCHAR(135) PRIMARY KEY,
    organization_ref VARCHAR(256) NOT NULL,
    public_bundle_digest VARCHAR(71) NOT NULL,
    runner_version VARCHAR(96) NOT NULL,
    runner_state VARCHAR(16) NOT NULL,
    capacity INTEGER NOT NULL,
    running_tasks INTEGER NOT NULL,
    available_slots INTEGER NOT NULL,
    observed_at_utc TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT uk_weave_runner_session_organization
        UNIQUE (organization_ref, runner_id),
    CONSTRAINT ck_weave_runner_session_identity
        CHECK (runner_id ~ '^runner_[A-Za-z0-9_-]{8,128}$'),
    CONSTRAINT ck_weave_runner_session_bundle
        CHECK (public_bundle_digest ~ '^sha256:[a-f0-9]{64}$'),
    CONSTRAINT ck_weave_runner_session_version
        CHECK (runner_version ~ '^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?$'),
    CONSTRAINT ck_weave_runner_session_state
        CHECK (runner_state IN ('ONLINE', 'DEGRADED', 'OFFLINE', 'REVOKED')),
    CONSTRAINT ck_weave_runner_session_capacity
        CHECK (
            capacity BETWEEN 1 AND 1024
            AND running_tasks BETWEEN 0 AND capacity
            AND available_slots = capacity - running_tasks
        )
);

CREATE INDEX ix_weave_runner_session_liveness
    ON weave_runner_sessions (
        organization_ref,
        runner_state,
        observed_at_utc
    );

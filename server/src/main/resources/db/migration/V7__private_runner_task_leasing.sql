CREATE TABLE weave_runner_tasks (
    task_id UUID PRIMARY KEY,
    organization_ref VARCHAR(256) NOT NULL,
    capability_id VARCHAR(128) NOT NULL,
    capability_version VARCHAR(96) NOT NULL,
    capability_coordinate VARCHAR(225) NOT NULL,
    bundle_digest VARCHAR(71) NOT NULL,
    idempotency_key VARCHAR(256) NOT NULL,
    payload_json TEXT NOT NULL,
    context_refs_json TEXT NOT NULL,
    resource_grants_json TEXT NOT NULL,
    priority INTEGER NOT NULL,
    created_at_utc TIMESTAMPTZ NOT NULL,
    available_at_utc TIMESTAMPTZ NOT NULL,
    deadline_at_utc TIMESTAMPTZ NOT NULL,
    traceparent VARCHAR(55),
    task_state VARCHAR(32) NOT NULL,
    attempt_count INTEGER NOT NULL,
    fencing_token BIGINT NOT NULL,
    current_attempt_id UUID,
    current_lease_id UUID,
    current_runner_id VARCHAR(135),
    lease_issued_at_utc TIMESTAMPTZ,
    lease_expires_at_utc TIMESTAMPTZ,
    outcome_digest VARCHAR(71),
    result_json TEXT,
    error_code VARCHAR(64),
    error_message VARCHAR(1000),
    error_retryable BOOLEAN,
    completed_at_utc TIMESTAMPTZ,
    version BIGINT NOT NULL,
    CONSTRAINT uk_weave_runner_task_idempotency
        UNIQUE (organization_ref, idempotency_key),
    CONSTRAINT ck_weave_runner_task_state
        CHECK (task_state IN ('READY', 'LEASED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_weave_runner_task_counters
        CHECK (attempt_count >= 0 AND fencing_token >= 0),
    CONSTRAINT ck_weave_runner_task_schedule
        CHECK (available_at_utc >= created_at_utc AND deadline_at_utc > available_at_utc),
    CONSTRAINT ck_weave_runner_task_current_lease
        CHECK (
            (current_attempt_id IS NULL
                AND current_lease_id IS NULL
                AND current_runner_id IS NULL
                AND lease_issued_at_utc IS NULL
                AND lease_expires_at_utc IS NULL)
            OR
            (current_attempt_id IS NOT NULL
                AND current_lease_id IS NOT NULL
                AND current_runner_id IS NOT NULL
                AND lease_issued_at_utc IS NOT NULL
                AND lease_expires_at_utc IS NOT NULL
                AND lease_expires_at_utc > lease_issued_at_utc
                AND deadline_at_utc >= lease_expires_at_utc)
        ),
    CONSTRAINT ck_weave_runner_task_terminal_outcome
        CHECK (
            (task_state IN ('SUCCEEDED', 'FAILED', 'CANCELLED'))
                = (outcome_digest IS NOT NULL AND completed_at_utc IS NOT NULL)
        ),
    CONSTRAINT ck_weave_runner_task_failure
        CHECK (
            (task_state = 'FAILED')
                = (error_code IS NOT NULL AND error_message IS NOT NULL AND error_retryable IS NOT NULL)
        ),
    CONSTRAINT ck_weave_runner_task_success_result
        CHECK (task_state <> 'SUCCEEDED' OR result_json IS NOT NULL)
);

CREATE INDEX ix_weave_runner_task_claim
    ON weave_runner_tasks (
        organization_ref,
        bundle_digest,
        capability_coordinate,
        task_state,
        available_at_utc,
        lease_expires_at_utc,
        priority,
        created_at_utc
    );

CREATE TABLE weave_runner_task_attempts (
    attempt_id UUID PRIMARY KEY,
    task_id UUID NOT NULL,
    attempt_number INTEGER NOT NULL,
    lease_id UUID NOT NULL,
    runner_id VARCHAR(135) NOT NULL,
    fencing_token BIGINT NOT NULL,
    attempt_state VARCHAR(32) NOT NULL,
    started_at_utc TIMESTAMPTZ NOT NULL,
    completed_at_utc TIMESTAMPTZ,
    outcome_digest VARCHAR(71),
    version BIGINT NOT NULL,
    CONSTRAINT uk_weave_runner_task_attempt
        UNIQUE (task_id, attempt_number),
    CONSTRAINT fk_weave_runner_attempt_task
        FOREIGN KEY (task_id)
        REFERENCES weave_runner_tasks (task_id)
        ON DELETE CASCADE,
    CONSTRAINT ck_weave_runner_attempt_number
        CHECK (attempt_number > 0 AND fencing_token > 0),
    CONSTRAINT ck_weave_runner_attempt_state
        CHECK (attempt_state IN (
            'LEASED',
            'RUNNING',
            'SUCCEEDED',
            'FAILED',
            'CANCELLED',
            'LEASE_LOST'
        )),
    CONSTRAINT ck_weave_runner_attempt_outcome
        CHECK (
            (attempt_state IN ('SUCCEEDED', 'FAILED', 'CANCELLED'))
                = (outcome_digest IS NOT NULL AND completed_at_utc IS NOT NULL)
            OR attempt_state = 'LEASE_LOST'
        )
);

CREATE INDEX ix_weave_runner_attempt_task
    ON weave_runner_task_attempts (task_id, attempt_number);

CREATE TABLE weave_runner_task_leases (
    lease_id UUID PRIMARY KEY,
    task_id UUID NOT NULL,
    attempt_id UUID NOT NULL,
    attempt_number INTEGER NOT NULL,
    runner_id VARCHAR(135) NOT NULL,
    fencing_token BIGINT NOT NULL,
    lease_state VARCHAR(32) NOT NULL,
    issued_at_utc TIMESTAMPTZ NOT NULL,
    expires_at_utc TIMESTAMPTZ NOT NULL,
    closed_at_utc TIMESTAMPTZ,
    version BIGINT NOT NULL,
    CONSTRAINT uk_weave_runner_task_lease_fence
        UNIQUE (task_id, fencing_token),
    CONSTRAINT uk_weave_runner_task_lease_attempt
        UNIQUE (task_id, attempt_number),
    CONSTRAINT fk_weave_runner_lease_task
        FOREIGN KEY (task_id)
        REFERENCES weave_runner_tasks (task_id)
        ON DELETE CASCADE,
    CONSTRAINT ck_weave_runner_lease_coordinates
        CHECK (attempt_number > 0 AND fencing_token > 0),
    CONSTRAINT ck_weave_runner_lease_time
        CHECK (expires_at_utc > issued_at_utc),
    CONSTRAINT ck_weave_runner_lease_state
        CHECK (lease_state IN ('ACTIVE', 'EXPIRED', 'COMPLETED')),
    CONSTRAINT ck_weave_runner_lease_closed
        CHECK ((lease_state = 'ACTIVE') = (closed_at_utc IS NULL))
);

CREATE INDEX ix_weave_runner_lease_task
    ON weave_runner_task_leases (task_id, lease_state);

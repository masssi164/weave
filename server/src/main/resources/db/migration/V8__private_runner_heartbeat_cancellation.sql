ALTER TABLE weave_runner_tasks
    ADD COLUMN cancel_requested_at_utc TIMESTAMPTZ,
    ADD COLUMN cancel_reason_code VARCHAR(64),
    ADD CONSTRAINT ck_weave_runner_task_cancellation_pair
        CHECK (
            (cancel_requested_at_utc IS NULL AND cancel_reason_code IS NULL)
            OR
            (cancel_requested_at_utc IS NOT NULL AND cancel_reason_code IS NOT NULL)
        ),
    ADD CONSTRAINT ck_weave_runner_task_cancellation_reason
        CHECK (
            cancel_reason_code IS NULL
            OR cancel_reason_code ~ '^[A-Z][A-Z0-9_]{1,63}$'
        );

ALTER TABLE weave_runner_tasks
    RENAME COLUMN bundle_digest TO capability_contract_digest;

ALTER TABLE weave_runner_tasks
    ADD COLUMN current_public_bundle_digest VARCHAR(71);

-- Legacy attempts used one digest for both task identity and the selected Runner bundle.
-- Preserve that auditable meaning while all new attempts write the two coordinates separately.
UPDATE weave_runner_tasks
SET current_public_bundle_digest = capability_contract_digest
WHERE current_lease_id IS NOT NULL;

ALTER TABLE weave_runner_tasks
    DROP CONSTRAINT ck_weave_runner_task_current_lease;

ALTER TABLE weave_runner_tasks
    ADD CONSTRAINT ck_weave_runner_task_contract_digest
        CHECK (capability_contract_digest ~ '^sha256:[a-f0-9]{64}$'),
    ADD CONSTRAINT ck_weave_runner_task_public_bundle_digest
        CHECK (
            current_public_bundle_digest IS NULL
            OR current_public_bundle_digest ~ '^sha256:[a-f0-9]{64}$'
        ),
    ADD CONSTRAINT ck_weave_runner_task_current_lease
        CHECK (
            (current_attempt_id IS NULL
                AND current_lease_id IS NULL
                AND current_runner_id IS NULL
                AND current_public_bundle_digest IS NULL
                AND lease_issued_at_utc IS NULL
                AND lease_expires_at_utc IS NULL)
            OR
            (current_attempt_id IS NOT NULL
                AND current_lease_id IS NOT NULL
                AND current_runner_id IS NOT NULL
                AND current_public_bundle_digest IS NOT NULL
                AND lease_issued_at_utc IS NOT NULL
                AND lease_expires_at_utc IS NOT NULL
                AND lease_expires_at_utc > lease_issued_at_utc
                AND deadline_at_utc >= lease_expires_at_utc)
        );

DROP INDEX ix_weave_runner_task_claim;

CREATE INDEX ix_weave_runner_task_claim
    ON weave_runner_tasks (
        organization_ref,
        capability_contract_digest,
        capability_coordinate,
        task_state,
        available_at_utc,
        lease_expires_at_utc,
        priority,
        created_at_utc
    );

ALTER TABLE weave_runner_task_attempts
    ADD COLUMN capability_contract_digest VARCHAR(71),
    ADD COLUMN public_bundle_digest VARCHAR(71);

UPDATE weave_runner_task_attempts attempt
SET capability_contract_digest = task.capability_contract_digest,
    public_bundle_digest = task.capability_contract_digest
FROM weave_runner_tasks task
WHERE task.task_id = attempt.task_id;

ALTER TABLE weave_runner_task_attempts
    ALTER COLUMN capability_contract_digest SET NOT NULL,
    ALTER COLUMN public_bundle_digest SET NOT NULL,
    ADD CONSTRAINT ck_weave_runner_attempt_contract_digests
        CHECK (
            capability_contract_digest ~ '^sha256:[a-f0-9]{64}$'
            AND public_bundle_digest ~ '^sha256:[a-f0-9]{64}$'
        );

ALTER TABLE weave_runner_task_leases
    ADD COLUMN capability_contract_digest VARCHAR(71),
    ADD COLUMN public_bundle_digest VARCHAR(71);

UPDATE weave_runner_task_leases lease
SET capability_contract_digest = task.capability_contract_digest,
    public_bundle_digest = task.capability_contract_digest
FROM weave_runner_tasks task
WHERE task.task_id = lease.task_id;

ALTER TABLE weave_runner_task_leases
    ALTER COLUMN capability_contract_digest SET NOT NULL,
    ALTER COLUMN public_bundle_digest SET NOT NULL,
    ADD CONSTRAINT ck_weave_runner_lease_contract_digests
        CHECK (
            capability_contract_digest ~ '^sha256:[a-f0-9]{64}$'
            AND public_bundle_digest ~ '^sha256:[a-f0-9]{64}$'
        );

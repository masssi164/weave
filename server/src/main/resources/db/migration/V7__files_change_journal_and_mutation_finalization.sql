-- Native Files V7 is deliberately reset-gated. This guard is the first executable
-- statement: populated V1-V6 Files state must be handled by an accepted environment
-- reset or a future migration contract, never by this migration.
DO $weave_files_v7_reset_guard$
BEGIN
    IF EXISTS (SELECT 1 FROM weave_files_objects)
       OR EXISTS (SELECT 1 FROM weave_file_locks)
       OR EXISTS (
            SELECT 1
              FROM weave_operation_intents intent
             WHERE intent.domain_key = 'files')
       OR EXISTS (
            SELECT 1
              FROM weave_operation_outbox outbox
              JOIN weave_operation_intents intent
                ON intent.operation_ref = outbox.operation_ref
             WHERE intent.domain_key = 'files') THEN
        RAISE EXCEPTION USING
            ERRCODE = 'P0001',
            MESSAGE = 'native Files V7 requires empty pre-V7 Files state';
    END IF;
END
$weave_files_v7_reset_guard$;

CREATE TABLE weave_files_volume_authorities (
    authority_key VARCHAR(64) NOT NULL,
    volume_ref VARCHAR(36) NOT NULL,
    generation_ref VARCHAR(36) NOT NULL,
    transition_kind VARCHAR(32) NOT NULL,
    transition_ref VARCHAR(36) NOT NULL,
    transition_receipt_digest VARCHAR(71) NOT NULL,
    schema_history_fingerprint VARCHAR(64) NOT NULL,
    root_marker_digest VARCHAR(71) NOT NULL,
    created_at_utc TIMESTAMPTZ(6) NOT NULL,
    CONSTRAINT pk_weave_files_volume_authorities
        PRIMARY KEY (authority_key),
    CONSTRAINT uq_weave_files_volume_authorities_volume
        UNIQUE (volume_ref),
    CONSTRAINT uq_weave_files_volume_authorities_generation
        UNIQUE (generation_ref),
    CONSTRAINT uq_weave_files_volume_authorities_transition
        UNIQUE (transition_ref),
    CONSTRAINT ck_weave_files_volume_authorities_key
        CHECK (authority_key = 'native-files'),
    CONSTRAINT ck_weave_files_volume_authorities_volume_ref
        CHECK (volume_ref ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'),
    CONSTRAINT ck_weave_files_volume_authorities_generation_ref
        CHECK (generation_ref ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'),
    CONSTRAINT ck_weave_files_volume_authorities_transition_kind
        CHECK (transition_kind IN ('INITIAL_PROVISION', 'AUTHORIZED_RESET')),
    CONSTRAINT ck_weave_files_volume_authorities_transition_ref
        CHECK (transition_ref ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'),
    CONSTRAINT ck_weave_files_volume_authorities_transition_digest
        CHECK (transition_receipt_digest ~ '^sha256:[0-9a-f]{64}$'),
    CONSTRAINT ck_weave_files_volume_authorities_schema_fingerprint
        CHECK (schema_history_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_weave_files_volume_authorities_marker_digest
        CHECK (root_marker_digest ~ '^sha256:[0-9a-f]{64}$')
);

CREATE FUNCTION weave_files_v7_volume_authority_immutable_guard()
RETURNS trigger
LANGUAGE plpgsql
AS $weave_files_v7_volume_authority_immutable_guard$
BEGIN
    RAISE EXCEPTION 'native Files volume authority is immutable';
END
$weave_files_v7_volume_authority_immutable_guard$;

CREATE TRIGGER trg_weave_files_v7_volume_authority_immutable
BEFORE UPDATE OR DELETE ON weave_files_volume_authorities
FOR EACH ROW
EXECUTE FUNCTION weave_files_v7_volume_authority_immutable_guard();

ALTER TABLE weave_operation_intents
    ADD CONSTRAINT uq_weave_operation_intents_scope_idempotency
        UNIQUE (organization_ref, idempotency_key),
    ADD CONSTRAINT uq_weave_operation_intents_initial_outbox_ref
        UNIQUE (initial_outbox_ref),
    ADD CONSTRAINT uq_weave_operation_intents_plan_link
        UNIQUE (
            operation_ref,
            organization_ref,
            canonical_arguments_digest,
            provider_binding_revision);

ALTER TABLE weave_operation_outbox
    ADD COLUMN available_at_utc TIMESTAMPTZ(6),
    ADD COLUMN lease_token VARCHAR(255),
    ADD COLUMN lease_owner VARCHAR(255),
    ADD COLUMN lease_until_utc TIMESTAMPTZ(6),
    ADD COLUMN last_diagnostic_code VARCHAR(120),
    ADD CONSTRAINT ck_weave_operation_outbox_lease_shape
        CHECK (
            (lease_token IS NULL
                AND lease_owner IS NULL
                AND lease_until_utc IS NULL)
            OR (lease_token IS NOT NULL
                AND lease_owner IS NOT NULL
                AND lease_until_utc IS NOT NULL
                AND delivery_state = 'DELIVERING'
                AND available_at_utc = lease_until_utc)),
    ADD CONSTRAINT ck_weave_operation_outbox_pending_available
        CHECK (
            delivery_state <> 'PENDING'
            OR (available_at_utc IS NOT NULL
                AND lease_token IS NULL
                AND lease_owner IS NULL
                AND lease_until_utc IS NULL))
        NOT VALID,
    ADD CONSTRAINT ck_weave_operation_outbox_delivering_lease
        CHECK (
            delivery_state <> 'DELIVERING'
            OR (available_at_utc IS NOT NULL
                AND lease_token IS NOT NULL
                AND lease_owner IS NOT NULL
                AND lease_until_utc IS NOT NULL
                AND available_at_utc = lease_until_utc))
        NOT VALID,
    ADD CONSTRAINT ck_weave_operation_outbox_terminal_lease_clear
        CHECK (
            delivery_state NOT IN ('DELIVERED', 'FAILED')
            OR (available_at_utc IS NULL
                AND lease_token IS NULL
                AND lease_owner IS NULL
                AND lease_until_utc IS NULL)),
    ADD CONSTRAINT ck_weave_operation_outbox_diagnostic_code
        CHECK (
            last_diagnostic_code IS NULL
            OR last_diagnostic_code ~ '^[a-z0-9][a-z0-9._-]{0,119}$'),
    ADD CONSTRAINT uq_weave_operation_outbox_outbox_ref
        UNIQUE (outbox_ref),
    ADD CONSTRAINT fk_weave_operation_outbox_intent
        FOREIGN KEY (operation_ref)
        REFERENCES weave_operation_intents (operation_ref);

CREATE INDEX idx_weave_operation_outbox_cleanup_availability
    ON weave_operation_outbox (available_at_utc, sequence_id)
    WHERE delivery_state IN ('PENDING', 'DELIVERING')
      AND event_type IN ('operation.denied', 'operation.failed');

CREATE TABLE weave_files_stream_heads (
    organization_ref VARCHAR(255) NOT NULL,
    space_ref VARCHAR(255) NOT NULL,
    latest_revision BIGINT NOT NULL DEFAULT 0,
    reset_required_floor BIGINT NOT NULL DEFAULT 0,
    lock_version BIGINT NOT NULL DEFAULT 0,
    updated_at_utc TIMESTAMPTZ(6) NOT NULL,
    CONSTRAINT pk_weave_files_stream_heads
        PRIMARY KEY (organization_ref, space_ref),
    CONSTRAINT ck_weave_files_stream_heads_revisions
        CHECK (
            latest_revision >= 0
            AND reset_required_floor >= 0
            AND reset_required_floor <= latest_revision),
    CONSTRAINT ck_weave_files_stream_heads_lock_version
        CHECK (lock_version >= 0)
);

CREATE TABLE weave_files_mutation_plans (
    operation_ref VARCHAR(255) NOT NULL,
    organization_ref VARCHAR(255) NOT NULL,
    space_ref VARCHAR(255) NOT NULL,
    plan_version VARCHAR(64) NOT NULL,
    canonical_arguments_digest VARCHAR(71) NOT NULL,
    operation_kind VARCHAR(32) NOT NULL,
    provider_binding_revision BIGINT NOT NULL,
    if_match_condition VARCHAR(4096) NOT NULL,
    if_none_match_condition VARCHAR(4096) NOT NULL,
    destination_must_remain_absent BOOLEAN NOT NULL,
    plan_state VARCHAR(16) NOT NULL,
    target_count INTEGER NOT NULL,
    targets_digest VARCHAR(71) NOT NULL,
    fence_count INTEGER NOT NULL,
    fences_digest VARCHAR(71) NOT NULL,
    sealed_at_utc TIMESTAMPTZ(6),
    CONSTRAINT pk_weave_files_mutation_plans
        PRIMARY KEY (operation_ref),
    CONSTRAINT uq_weave_files_mutation_plans_scope_link
        UNIQUE (
            operation_ref,
            organization_ref,
            space_ref,
            provider_binding_revision),
    CONSTRAINT fk_weave_files_mutation_plans_intent
        FOREIGN KEY (
            operation_ref,
            organization_ref,
            canonical_arguments_digest,
            provider_binding_revision)
        REFERENCES weave_operation_intents (
            operation_ref,
            organization_ref,
            canonical_arguments_digest,
            provider_binding_revision),
    CONSTRAINT ck_weave_files_mutation_plans_version
        CHECK (plan_version = 'weave.files-mutation-plan/v1'),
    CONSTRAINT ck_weave_files_mutation_plans_operation_kind
        CHECK (operation_kind IN ('PUT', 'MKCOL', 'COPY', 'MOVE', 'DELETE')),
    CONSTRAINT ck_weave_files_mutation_plans_binding_revision
        CHECK (provider_binding_revision > 0),
    CONSTRAINT ck_weave_files_mutation_plans_state
        CHECK (plan_state IN ('OPEN', 'SEALED')),
    CONSTRAINT ck_weave_files_mutation_plans_target_count
        CHECK (target_count > 0),
    CONSTRAINT ck_weave_files_mutation_plans_fence_count
        CHECK (fence_count > 0),
    CONSTRAINT ck_weave_files_mutation_plans_conditions
        CHECK (
            if_match_condition ~ '^(NOT_SUPPLIED|ANY|ETAG_SET:\[.*\])$'
            AND if_none_match_condition ~ '^(NOT_SUPPLIED|ANY|ETAG_SET:\[.*\])$'),
    CONSTRAINT ck_weave_files_mutation_plans_destination_absence
        CHECK (
            NOT destination_must_remain_absent
            OR operation_kind IN ('COPY', 'MOVE')),
    CONSTRAINT ck_weave_files_mutation_plans_arguments_digest
        CHECK (canonical_arguments_digest ~ '^sha256:[0-9a-f]{64}$'),
    CONSTRAINT ck_weave_files_mutation_plans_targets_digest
        CHECK (targets_digest ~ '^sha256:[0-9a-f]{64}$'),
    CONSTRAINT ck_weave_files_mutation_plans_fences_digest
        CHECK (fences_digest ~ '^sha256:[0-9a-f]{64}$'),
    CONSTRAINT ck_weave_files_mutation_plans_sealed_at
        CHECK (
            (plan_state = 'OPEN' AND sealed_at_utc IS NULL)
            OR (plan_state = 'SEALED' AND sealed_at_utc IS NOT NULL))
);

CREATE TABLE weave_files_mutation_targets (
    operation_ref VARCHAR(255) NOT NULL,
    target_ordinal INTEGER NOT NULL,
    target_version VARCHAR(64) NOT NULL,
    change_kind VARCHAR(32) NOT NULL,
    source_file_ref VARCHAR(255),
    target_file_ref VARCHAR(255) NOT NULL,
    source_path VARCHAR(2048),
    target_path VARCHAR(2048),
    object_kind VARCHAR(32) NOT NULL,
    result_lifecycle_state VARCHAR(32) NOT NULL,
    source_read_blob_binding VARCHAR(1024),
    source_size BIGINT,
    source_media_type VARCHAR(255),
    source_content_digest VARCHAR(71),
    source_file_version VARCHAR(1024),
    source_strong_etag VARCHAR(1024),
    source_modified_at_utc TIMESTAMPTZ(6),
    source_hidden BOOLEAN,
    source_observed_at_utc TIMESTAMPTZ(6),
    source_lifecycle_state VARCHAR(32),
    result_blob_binding VARCHAR(1024),
    result_size BIGINT NOT NULL,
    result_media_type VARCHAR(255),
    result_content_digest VARCHAR(71),
    result_file_version VARCHAR(1024),
    result_strong_etag VARCHAR(1024),
    result_modified_at_utc TIMESTAMPTZ(6) NOT NULL,
    result_hidden BOOLEAN NOT NULL,
    result_observed_at_utc TIMESTAMPTZ(6) NOT NULL,
    CONSTRAINT pk_weave_files_mutation_targets
        PRIMARY KEY (operation_ref, target_ordinal),
    CONSTRAINT fk_weave_files_mutation_targets_plan
        FOREIGN KEY (operation_ref)
        REFERENCES weave_files_mutation_plans (operation_ref),
    CONSTRAINT ck_weave_files_mutation_targets_version
        CHECK (target_version = 'weave.files-mutation-target/v1'),
    CONSTRAINT ck_weave_files_mutation_targets_ordinal
        CHECK (target_ordinal >= 0),
    CONSTRAINT ck_weave_files_mutation_targets_change_kind
        CHECK (change_kind IN (
            'CREATED',
            'CONTENT_UPDATED',
            'COPIED',
            'MOVED',
            'TOMBSTONED')),
    CONSTRAINT ck_weave_files_mutation_targets_object_kind
        CHECK (object_kind IN ('FILE', 'COLLECTION')),
    CONSTRAINT ck_weave_files_mutation_targets_lifecycle
        CHECK (result_lifecycle_state IN ('ACTIVE', 'TOMBSTONED')),
    CONSTRAINT ck_weave_files_mutation_targets_source_lifecycle
        CHECK (source_lifecycle_state IS NULL OR source_lifecycle_state = 'ACTIVE'),
    CONSTRAINT ck_weave_files_mutation_targets_source_size
        CHECK (source_size IS NULL OR source_size BETWEEN 0 AND 9007199254740991),
    CONSTRAINT ck_weave_files_mutation_targets_result_size
        CHECK (result_size BETWEEN 0 AND 9007199254740991),
    CONSTRAINT ck_weave_files_mutation_targets_source_digest
        CHECK (
            source_content_digest IS NULL
            OR source_content_digest ~ '^sha256:[0-9a-f]{64}$'),
    CONSTRAINT ck_weave_files_mutation_targets_result_digest
        CHECK (
            result_content_digest IS NULL
            OR result_content_digest ~ '^sha256:[0-9a-f]{64}$'),
    CONSTRAINT ck_weave_files_mutation_targets_source_content
        CHECK (
            (source_read_blob_binding IS NULL
                AND source_size IS NULL
                AND source_media_type IS NULL
                AND source_content_digest IS NULL
                AND source_file_version IS NULL
                AND source_strong_etag IS NULL
                AND source_modified_at_utc IS NULL
                AND source_hidden IS NULL
                AND source_observed_at_utc IS NULL
                AND source_lifecycle_state IS NULL)
            OR
            (source_read_blob_binding IS NOT NULL
                AND source_size IS NOT NULL
                AND source_content_digest IS NOT NULL
                AND source_file_version IS NOT NULL
                AND source_strong_etag IS NOT NULL
                AND source_modified_at_utc IS NOT NULL
                AND source_hidden IS NOT NULL
                AND source_observed_at_utc IS NOT NULL
                AND source_lifecycle_state = 'ACTIVE')),
    CONSTRAINT ck_weave_files_mutation_targets_result_content
        CHECK (
            (object_kind = 'COLLECTION'
                AND source_read_blob_binding IS NULL
                AND result_blob_binding IS NULL
                AND result_size = 0
                AND result_media_type IS NULL
                AND result_content_digest IS NULL
                AND result_file_version IS NULL
                AND result_strong_etag IS NULL)
            OR
            (object_kind = 'FILE'
                AND result_blob_binding IS NOT NULL
                AND result_content_digest IS NOT NULL
                AND result_file_version IS NOT NULL
                AND result_strong_etag IS NOT NULL)),
    CONSTRAINT ck_weave_files_mutation_targets_paths
        CHECK (
            (change_kind IN ('CREATED', 'CONTENT_UPDATED') AND target_path IS NOT NULL)
            OR
            (change_kind = 'COPIED'
                AND source_file_ref IS NOT NULL
                AND source_path IS NOT NULL
                AND target_path IS NOT NULL
                AND (object_kind <> 'FILE' OR source_read_blob_binding IS NOT NULL))
            OR
            (change_kind = 'MOVED'
                AND source_file_ref IS NOT NULL
                AND source_path IS NOT NULL
                AND target_path IS NOT NULL)
            OR
            (change_kind = 'TOMBSTONED'
                AND source_path IS NOT NULL
                AND target_path IS NULL)),
    CONSTRAINT ck_weave_files_mutation_targets_result_lifecycle
        CHECK (
            (change_kind = 'TOMBSTONED' AND result_lifecycle_state = 'TOMBSTONED')
            OR
            (change_kind <> 'TOMBSTONED' AND result_lifecycle_state = 'ACTIVE')),
    CONSTRAINT ck_weave_files_mutation_targets_source_path_shape
        CHECK (source_path IS NULL OR source_path LIKE '/%'),
    CONSTRAINT ck_weave_files_mutation_targets_target_path_shape
        CHECK (target_path IS NULL OR target_path LIKE '/%')
);

CREATE INDEX idx_weave_files_mutation_targets_source_binding
    ON weave_files_mutation_targets (source_read_blob_binding)
    WHERE source_read_blob_binding IS NOT NULL;

CREATE INDEX idx_weave_files_mutation_targets_result_binding
    ON weave_files_mutation_targets (result_blob_binding)
    WHERE result_blob_binding IS NOT NULL;

CREATE TABLE weave_files_mutation_fences (
    operation_ref VARCHAR(255) NOT NULL,
    fence_ordinal INTEGER NOT NULL,
    fence_version VARCHAR(64) NOT NULL,
    fence_role VARCHAR(32) NOT NULL,
    canonical_path VARCHAR(2048) NOT NULL,
    expected_presence VARCHAR(16) NOT NULL,
    expected_file_ref VARCHAR(255),
    expected_object_kind VARCHAR(32),
    expected_lifecycle_state VARCHAR(32),
    expected_row_version BIGINT,
    expected_strong_etag VARCHAR(1024),
    expected_subtree_digest VARCHAR(71),
    snapshot_digest VARCHAR(71) NOT NULL,
    CONSTRAINT pk_weave_files_mutation_fences
        PRIMARY KEY (operation_ref, fence_ordinal),
    CONSTRAINT fk_weave_files_mutation_fences_plan
        FOREIGN KEY (operation_ref)
        REFERENCES weave_files_mutation_plans (operation_ref),
    CONSTRAINT ck_weave_files_mutation_fences_version
        CHECK (fence_version = 'weave.files-mutation-fence/v1'),
    CONSTRAINT ck_weave_files_mutation_fences_ordinal
        CHECK (fence_ordinal >= 0),
    CONSTRAINT ck_weave_files_mutation_fences_role
        CHECK (fence_role IN (
            'REQUEST_TARGET',
            'SOURCE_MEMBER',
            'DESTINATION_TARGET',
            'DESTINATION_MEMBER')),
    CONSTRAINT ck_weave_files_mutation_fences_path
        CHECK (canonical_path LIKE '/%'),
    CONSTRAINT ck_weave_files_mutation_fences_presence
        CHECK (expected_presence IN ('ABSENT', 'PRESENT')),
    CONSTRAINT ck_weave_files_mutation_fences_snapshot
        CHECK (
            (expected_presence = 'ABSENT'
                AND expected_file_ref IS NULL
                AND expected_object_kind IS NULL
                AND expected_lifecycle_state IS NULL
                AND expected_row_version IS NULL
                AND expected_strong_etag IS NULL
                AND expected_subtree_digest IS NULL)
            OR
            (expected_presence = 'PRESENT'
                AND expected_file_ref IS NOT NULL
                AND expected_object_kind IN ('FILE', 'COLLECTION')
                AND expected_lifecycle_state = 'ACTIVE'
                AND expected_row_version >= 0)),
    CONSTRAINT ck_weave_files_mutation_fences_subtree_digest
        CHECK (
            expected_subtree_digest IS NULL
            OR expected_subtree_digest ~ '^sha256:[0-9a-f]{64}$'),
    CONSTRAINT ck_weave_files_mutation_fences_snapshot_digest
        CHECK (snapshot_digest ~ '^sha256:[0-9a-f]{64}$')
);

CREATE TABLE weave_files_blob_cleanup_dispositions (
    operation_ref VARCHAR(255) NOT NULL,
    binding_digest VARCHAR(71) NOT NULL,
    disposition_version VARCHAR(64) NOT NULL,
    private_blob_binding VARCHAR(1024) NOT NULL,
    disposition VARCHAR(32) NOT NULL,
    recorded_at_utc TIMESTAMPTZ(6) NOT NULL,
    CONSTRAINT pk_weave_files_blob_cleanup_dispositions
        PRIMARY KEY (operation_ref, binding_digest),
    CONSTRAINT uq_weave_files_blob_cleanup_dispositions_binding
        UNIQUE (operation_ref, private_blob_binding),
    CONSTRAINT fk_weave_files_blob_cleanup_dispositions_plan
        FOREIGN KEY (operation_ref)
        REFERENCES weave_files_mutation_plans (operation_ref),
    CONSTRAINT ck_weave_files_blob_cleanup_dispositions_version
        CHECK (disposition_version = 'weave.files-blob-cleanup-disposition/v1'),
    CONSTRAINT ck_weave_files_blob_cleanup_dispositions_digest_shape
        CHECK (binding_digest ~ '^sha256:[0-9a-f]{64}$'),
    CONSTRAINT ck_weave_files_blob_cleanup_dispositions_binding_shape
        CHECK (
            char_length(private_blob_binding) <= 1024
            AND private_blob_binding ~ '^[a-z0-9][a-z0-9/_-]*$'
            AND private_blob_binding NOT LIKE '%//%'
            AND private_blob_binding NOT LIKE '%/'),
    CONSTRAINT ck_weave_files_blob_cleanup_dispositions_exact_digest
        CHECK (
            binding_digest = 'sha256:'
                || encode(sha256(convert_to(private_blob_binding, 'UTF8')), 'hex')),
    CONSTRAINT ck_weave_files_blob_cleanup_dispositions_closed_value
        CHECK (disposition IN (
            'STILL_REFERENCED',
            'STILL_PROTECTED',
            'DELETED',
            'ALREADY_ABSENT'))
);

CREATE INDEX idx_weave_files_blob_cleanup_dispositions_binding
    ON weave_files_blob_cleanup_dispositions (private_blob_binding, operation_ref);

CREATE TABLE weave_files_changes (
    organization_ref VARCHAR(255) NOT NULL,
    space_ref VARCHAR(255) NOT NULL,
    revision BIGINT NOT NULL,
    operation_ref VARCHAR(255) NOT NULL,
    change_kind VARCHAR(32) NOT NULL,
    file_ref VARCHAR(255) NOT NULL,
    source_file_ref VARCHAR(255),
    source_path VARCHAR(2048),
    target_path VARCHAR(2048),
    object_kind VARCHAR(32) NOT NULL,
    lifecycle_state VARCHAR(32) NOT NULL,
    provider_binding_revision BIGINT NOT NULL,
    resulting_size BIGINT NOT NULL,
    resulting_media_type VARCHAR(255),
    resulting_content_digest VARCHAR(71),
    resulting_file_version VARCHAR(1024),
    resulting_etag VARCHAR(1024),
    resulting_modified_at_utc TIMESTAMPTZ(6) NOT NULL,
    resulting_hidden BOOLEAN NOT NULL,
    resulting_observed_at_utc TIMESTAMPTZ(6) NOT NULL,
    range_start BIGINT NOT NULL,
    range_end BIGINT NOT NULL,
    committed_at_utc TIMESTAMPTZ(6) NOT NULL,
    CONSTRAINT pk_weave_files_changes
        PRIMARY KEY (organization_ref, space_ref, revision),
    CONSTRAINT uq_weave_files_changes_operation_file
        UNIQUE (organization_ref, space_ref, operation_ref, file_ref),
    CONSTRAINT fk_weave_files_changes_head
        FOREIGN KEY (organization_ref, space_ref)
        REFERENCES weave_files_stream_heads (organization_ref, space_ref),
    CONSTRAINT fk_weave_files_changes_plan
        FOREIGN KEY (
            operation_ref,
            organization_ref,
            space_ref,
            provider_binding_revision)
        REFERENCES weave_files_mutation_plans (
            operation_ref,
            organization_ref,
            space_ref,
            provider_binding_revision),
    CONSTRAINT ck_weave_files_changes_revision
        CHECK (revision > 0),
    CONSTRAINT ck_weave_files_changes_range
        CHECK (
            range_start > 0
            AND range_end >= range_start
            AND revision BETWEEN range_start AND range_end),
    CONSTRAINT ck_weave_files_changes_change_kind
        CHECK (change_kind IN (
            'CREATED',
            'CONTENT_UPDATED',
            'COPIED',
            'MOVED',
            'TOMBSTONED')),
    CONSTRAINT ck_weave_files_changes_object_kind
        CHECK (object_kind IN ('FILE', 'COLLECTION')),
    CONSTRAINT ck_weave_files_changes_lifecycle
        CHECK (lifecycle_state IN ('ACTIVE', 'TOMBSTONED')),
    CONSTRAINT ck_weave_files_changes_binding_revision
        CHECK (provider_binding_revision > 0),
    CONSTRAINT ck_weave_files_changes_result_size
        CHECK (resulting_size BETWEEN 0 AND 9007199254740991),
    CONSTRAINT ck_weave_files_changes_result_digest
        CHECK (
            resulting_content_digest IS NULL
            OR resulting_content_digest ~ '^sha256:[0-9a-f]{64}$'),
    CONSTRAINT ck_weave_files_changes_result_snapshot
        CHECK (
            (object_kind = 'COLLECTION'
                AND resulting_size = 0
                AND resulting_media_type IS NULL
                AND resulting_content_digest IS NULL
                AND resulting_file_version IS NULL
                AND resulting_etag IS NULL)
            OR
            (object_kind = 'FILE'
                AND resulting_content_digest IS NOT NULL
                AND resulting_file_version IS NOT NULL
                AND resulting_etag IS NOT NULL)),
    CONSTRAINT ck_weave_files_changes_paths
        CHECK (
            (change_kind IN ('CREATED', 'CONTENT_UPDATED') AND target_path IS NOT NULL)
            OR
            (change_kind IN ('COPIED', 'MOVED')
                AND source_file_ref IS NOT NULL
                AND source_path IS NOT NULL
                AND target_path IS NOT NULL)
            OR
            (change_kind = 'TOMBSTONED'
                AND source_path IS NOT NULL
                AND target_path IS NULL)),
    CONSTRAINT ck_weave_files_changes_result_lifecycle
        CHECK (
            (change_kind = 'TOMBSTONED' AND lifecycle_state = 'TOMBSTONED')
            OR
            (change_kind <> 'TOMBSTONED' AND lifecycle_state = 'ACTIVE')),
    CONSTRAINT ck_weave_files_changes_source_path_shape
        CHECK (source_path IS NULL OR source_path LIKE '/%'),
    CONSTRAINT ck_weave_files_changes_target_path_shape
        CHECK (target_path IS NULL OR target_path LIKE '/%')
);

CREATE INDEX idx_weave_files_changes_operation
    ON weave_files_changes (operation_ref, revision);

CREATE FUNCTION weave_files_v7_change_immutable_guard()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $weave_files_v7_change_immutable_guard$
BEGIN
    RAISE EXCEPTION 'Files change journal entries are insert-only';
END
$weave_files_v7_change_immutable_guard$;

CREATE TRIGGER trg_weave_files_v7_change_immutable
BEFORE UPDATE OR DELETE ON weave_files_changes
FOR EACH ROW
EXECUTE FUNCTION weave_files_v7_change_immutable_guard();

CREATE FUNCTION weave_files_v7_plan_immutability_guard()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $weave_files_v7_plan_immutability_guard$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.plan_state <> 'OPEN' OR NEW.sealed_at_utc IS NOT NULL THEN
            RAISE EXCEPTION 'Files mutation plans must be inserted OPEN';
        END IF;
        RETURN NEW;
    END IF;

    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Files mutation plans are immutable';
    END IF;

    IF OLD.plan_state <> 'OPEN'
       OR NEW.plan_state <> 'SEALED'
       OR NEW.sealed_at_utc IS NULL
       OR ROW(
            NEW.operation_ref,
            NEW.organization_ref,
            NEW.space_ref,
            NEW.plan_version,
            NEW.canonical_arguments_digest,
            NEW.operation_kind,
            NEW.provider_binding_revision,
            NEW.if_match_condition,
            NEW.if_none_match_condition,
            NEW.destination_must_remain_absent,
            NEW.target_count,
            NEW.targets_digest,
            NEW.fence_count,
            NEW.fences_digest)
          IS DISTINCT FROM
          ROW(
            OLD.operation_ref,
            OLD.organization_ref,
            OLD.space_ref,
            OLD.plan_version,
            OLD.canonical_arguments_digest,
            OLD.operation_kind,
            OLD.provider_binding_revision,
            OLD.if_match_condition,
            OLD.if_none_match_condition,
            OLD.destination_must_remain_absent,
            OLD.target_count,
            OLD.targets_digest,
            OLD.fence_count,
            OLD.fences_digest) THEN
        RAISE EXCEPTION 'Files mutation plan sealing may only change OPEN to SEALED';
    END IF;
    RETURN NEW;
END
$weave_files_v7_plan_immutability_guard$;

CREATE TRIGGER trg_weave_files_v7_plan_immutability
BEFORE INSERT OR UPDATE OR DELETE ON weave_files_mutation_plans
FOR EACH ROW
EXECUTE FUNCTION weave_files_v7_plan_immutability_guard();

CREATE FUNCTION weave_files_v7_plan_intent_guard()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $weave_files_v7_plan_intent_guard$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM weave_operation_intents intent
         WHERE intent.operation_ref = NEW.operation_ref
           AND intent.organization_ref = NEW.organization_ref
           AND intent.canonical_arguments_digest = NEW.canonical_arguments_digest
           AND intent.provider_binding_revision = NEW.provider_binding_revision
           AND intent.domain_key = 'files'
           AND intent.intent_state = 'CREATED') THEN
        RAISE EXCEPTION 'Files mutation plan does not match one CREATED Files intent';
    END IF;
    RETURN NEW;
END
$weave_files_v7_plan_intent_guard$;

CREATE TRIGGER trg_weave_files_v7_plan_intent
BEFORE INSERT ON weave_files_mutation_plans
FOR EACH ROW
EXECUTE FUNCTION weave_files_v7_plan_intent_guard();

CREATE FUNCTION weave_files_v7_target_immutability_guard()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $weave_files_v7_target_immutability_guard$
DECLARE
    current_plan_state VARCHAR(16);
BEGIN
    IF TG_OP <> 'INSERT' THEN
        RAISE EXCEPTION 'Files mutation targets are insert-only';
    END IF;

    SELECT plan.plan_state
      INTO current_plan_state
      FROM weave_files_mutation_plans plan
     WHERE plan.operation_ref = NEW.operation_ref;
    IF current_plan_state IS DISTINCT FROM 'OPEN' THEN
        RAISE EXCEPTION 'Files mutation targets require one OPEN plan';
    END IF;
    RETURN NEW;
END
$weave_files_v7_target_immutability_guard$;

CREATE TRIGGER trg_weave_files_v7_target_immutability
BEFORE INSERT OR UPDATE OR DELETE ON weave_files_mutation_targets
FOR EACH ROW
EXECUTE FUNCTION weave_files_v7_target_immutability_guard();

CREATE FUNCTION weave_files_v7_fence_immutability_guard()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $weave_files_v7_fence_immutability_guard$
DECLARE
    current_plan_state VARCHAR(16);
BEGIN
    IF TG_OP <> 'INSERT' THEN
        RAISE EXCEPTION 'Files mutation fences are insert-only';
    END IF;

    SELECT plan.plan_state
      INTO current_plan_state
      FROM weave_files_mutation_plans plan
     WHERE plan.operation_ref = NEW.operation_ref;
    IF current_plan_state IS DISTINCT FROM 'OPEN' THEN
        RAISE EXCEPTION 'Files mutation fences require one OPEN plan';
    END IF;
    RETURN NEW;
END
$weave_files_v7_fence_immutability_guard$;

CREATE TRIGGER trg_weave_files_v7_fence_immutability
BEFORE INSERT OR UPDATE OR DELETE ON weave_files_mutation_fences
FOR EACH ROW
EXECUTE FUNCTION weave_files_v7_fence_immutability_guard();

CREATE FUNCTION weave_files_v7_cleanup_disposition_guard()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $weave_files_v7_cleanup_disposition_guard$
DECLARE
    terminal_state VARCHAR(32);
    reserved_outbox_ref VARCHAR(255);
BEGIN
    IF TG_OP <> 'INSERT' THEN
        RAISE EXCEPTION 'Files blob cleanup dispositions are insert-only';
    END IF;

    SELECT intent.intent_state, intent.initial_outbox_ref
      INTO terminal_state, reserved_outbox_ref
      FROM weave_operation_intents intent
      JOIN weave_files_mutation_plans plan
        ON plan.operation_ref = intent.operation_ref
     WHERE plan.operation_ref = NEW.operation_ref
       AND plan.organization_ref = intent.organization_ref
       AND plan.canonical_arguments_digest = intent.canonical_arguments_digest
       AND plan.provider_binding_revision = intent.provider_binding_revision
       AND plan.plan_state = 'SEALED'
       AND intent.domain_key = 'files'
       AND intent.intent_state IN ('DENIED', 'FAILED');

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Files blob cleanup disposition requires one sealed terminal-failure plan';
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM weave_operation_outbox outbox
         WHERE outbox.operation_ref = NEW.operation_ref
           AND outbox.outbox_ref = reserved_outbox_ref
           AND outbox.event_type = CASE terminal_state
                WHEN 'DENIED' THEN 'operation.denied'
                ELSE 'operation.failed'
               END) THEN
        RAISE EXCEPTION 'Files blob cleanup disposition requires the reserved failure outbox row';
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM weave_files_mutation_targets target
         WHERE target.operation_ref = NEW.operation_ref
           AND target.object_kind = 'FILE'
           AND (target.source_read_blob_binding = NEW.private_blob_binding
                OR target.result_blob_binding = NEW.private_blob_binding)) THEN
        RAISE EXCEPTION 'Files blob cleanup disposition binding is not in the sealed plan';
    END IF;
    RETURN NEW;
END
$weave_files_v7_cleanup_disposition_guard$;

CREATE TRIGGER trg_weave_files_v7_cleanup_disposition
BEFORE INSERT OR UPDATE OR DELETE ON weave_files_blob_cleanup_dispositions
FOR EACH ROW
EXECUTE FUNCTION weave_files_v7_cleanup_disposition_guard();

CREATE FUNCTION weave_files_v7_plan_completeness_guard()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $weave_files_v7_plan_completeness_guard$
DECLARE
    checked_operation_ref VARCHAR(255);
    checked_plan_state VARCHAR(16);
    checked_operation_kind VARCHAR(32);
    checked_target_count INTEGER;
    checked_fence_count INTEGER;
    actual_target_count BIGINT;
    actual_fence_count BIGINT;
    minimum_ordinal INTEGER;
    maximum_ordinal INTEGER;
    minimum_fence_ordinal INTEGER;
    maximum_fence_ordinal INTEGER;
    request_fence_count BIGINT;
    destination_fence_count BIGINT;
    invalid_member_count BIGINT;
BEGIN
    checked_operation_ref := NEW.operation_ref;
    SELECT plan.plan_state, plan.operation_kind, plan.target_count, plan.fence_count
      INTO checked_plan_state, checked_operation_kind, checked_target_count, checked_fence_count
      FROM weave_files_mutation_plans plan
     WHERE plan.operation_ref = checked_operation_ref;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Files mutation plan is missing at deferred validation';
    END IF;

    SELECT COUNT(*), MIN(target.target_ordinal), MAX(target.target_ordinal)
      INTO actual_target_count, minimum_ordinal, maximum_ordinal
      FROM weave_files_mutation_targets target
     WHERE target.operation_ref = checked_operation_ref;

    SELECT COUNT(*),
           MIN(fence.fence_ordinal),
           MAX(fence.fence_ordinal),
           COUNT(*) FILTER (WHERE fence.fence_role = 'REQUEST_TARGET'),
           COUNT(*) FILTER (WHERE fence.fence_role = 'DESTINATION_TARGET'),
           COUNT(*) FILTER (
               WHERE fence.fence_role IN ('SOURCE_MEMBER', 'DESTINATION_MEMBER')
                 AND fence.expected_presence <> 'PRESENT')
      INTO actual_fence_count,
           minimum_fence_ordinal,
           maximum_fence_ordinal,
           request_fence_count,
           destination_fence_count,
           invalid_member_count
      FROM weave_files_mutation_fences fence
     WHERE fence.operation_ref = checked_operation_ref;

    IF checked_plan_state <> 'SEALED'
       OR checked_target_count < 1
       OR actual_target_count <> checked_target_count
       OR minimum_ordinal <> 0
       OR maximum_ordinal <> checked_target_count - 1
       OR checked_fence_count < 1
       OR actual_fence_count <> checked_fence_count
       OR minimum_fence_ordinal <> 0
       OR maximum_fence_ordinal <> checked_fence_count - 1
       OR request_fence_count <> 1
       OR invalid_member_count <> 0
       OR (checked_operation_kind IN ('COPY', 'MOVE') AND destination_fence_count <> 1)
       OR (checked_operation_kind NOT IN ('COPY', 'MOVE') AND destination_fence_count <> 0) THEN
        RAISE EXCEPTION 'Files mutation plan is unsealed, incomplete, or noncontiguous';
    END IF;
    RETURN NULL;
END
$weave_files_v7_plan_completeness_guard$;

CREATE CONSTRAINT TRIGGER trg_weave_files_v7_plan_complete
AFTER INSERT OR UPDATE ON weave_files_mutation_plans
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION weave_files_v7_plan_completeness_guard();

CREATE CONSTRAINT TRIGGER trg_weave_files_v7_target_complete
AFTER INSERT ON weave_files_mutation_targets
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION weave_files_v7_plan_completeness_guard();

CREATE CONSTRAINT TRIGGER trg_weave_files_v7_fence_complete
AFTER INSERT ON weave_files_mutation_fences
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION weave_files_v7_plan_completeness_guard();

CREATE FUNCTION weave_files_v7_outbox_link_guard()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $weave_files_v7_outbox_link_guard$
DECLARE
    reserved_outbox_ref VARCHAR(255);
BEGIN
    SELECT intent.initial_outbox_ref
      INTO reserved_outbox_ref
      FROM weave_operation_intents intent
     WHERE intent.operation_ref = NEW.operation_ref;

    IF EXISTS (
        SELECT 1
          FROM weave_files_mutation_plans plan
         WHERE plan.operation_ref = NEW.operation_ref) THEN
        IF NEW.outbox_ref <> reserved_outbox_ref THEN
            RAISE EXCEPTION 'Files outbox row must use the intent reserved outbox reference';
        END IF;
        IF EXISTS (
            SELECT 1
              FROM weave_operation_outbox existing
             WHERE existing.operation_ref = NEW.operation_ref
               AND (TG_OP = 'INSERT' OR existing.sequence_id <> NEW.sequence_id)) THEN
            RAISE EXCEPTION 'Files intent may have only one finalization outbox row';
        END IF;
    END IF;
    RETURN NEW;
END
$weave_files_v7_outbox_link_guard$;

CREATE TRIGGER trg_weave_files_v7_outbox_link
BEFORE INSERT OR UPDATE OF outbox_ref, operation_ref ON weave_operation_outbox
FOR EACH ROW
EXECUTE FUNCTION weave_files_v7_outbox_link_guard();

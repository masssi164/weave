-- Native Calendar/Chat normalization for persistent dogfood.
-- Forward-only migration. No external-provider content is imported.

-- Calendar recurrence profile widened from the pre-Flyway DAILY/WEEKLY shape.
ALTER TABLE public.weave_calendar_events
    DROP CONSTRAINT IF EXISTS weave_calendar_events_recurrence_frequency_check;
ALTER TABLE public.weave_calendar_events
    ADD CONSTRAINT weave_calendar_events_recurrence_frequency_check
    CHECK (recurrence_frequency IS NULL OR recurrence_frequency IN ('DAILY','WEEKLY','MONTHLY','YEARLY'));

CREATE TABLE public.weave_calendar_sync_heads (
    calendar_id varchar(96) NOT NULL,
    scope_key varchar(768) NOT NULL,
    revision bigint NOT NULL DEFAULT 0,
    row_version bigint NOT NULL DEFAULT 0,
    updated_at_utc timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (calendar_id, scope_key),
    CHECK (revision >= 0)
);

INSERT INTO public.weave_calendar_sync_heads(calendar_id, scope_key, revision, row_version, updated_at_utc)
SELECT calendar_id, scope_key, latest_change_sequence, 0, updated_at_utc
FROM public.weave_calendar_collections
ON CONFLICT (calendar_id, scope_key) DO NOTHING;

CREATE TABLE public.weave_calendar_event_temporals (
    calendar_id varchar(96) NOT NULL,
    scope_key varchar(768) NOT NULL,
    event_id varchar(512) NOT NULL,
    temporal_kind varchar(16) NOT NULL,
    start_date date,
    end_date date,
    start_local timestamp without time zone,
    end_local timestamp without time zone,
    start_instant timestamptz,
    end_instant timestamptz,
    timezone_id varchar(255),
    PRIMARY KEY (calendar_id, scope_key, event_id),
    CHECK (temporal_kind IN ('DATE','FLOATING','UTC','ZONED')),
    CHECK (
        (temporal_kind = 'DATE' AND start_date IS NOT NULL AND end_date IS NOT NULL
            AND start_local IS NULL AND end_local IS NULL AND start_instant IS NULL AND end_instant IS NULL AND timezone_id IS NULL)
        OR
        (temporal_kind = 'FLOATING' AND start_local IS NOT NULL AND end_local IS NOT NULL
            AND start_date IS NULL AND end_date IS NULL AND start_instant IS NULL AND end_instant IS NULL AND timezone_id IS NULL)
        OR
        (temporal_kind = 'UTC' AND start_instant IS NOT NULL AND end_instant IS NOT NULL
            AND start_date IS NULL AND end_date IS NULL AND start_local IS NULL AND end_local IS NULL AND timezone_id IS NULL)
        OR
        (temporal_kind = 'ZONED' AND start_local IS NOT NULL AND end_local IS NOT NULL AND timezone_id IS NOT NULL
            AND start_date IS NULL AND end_date IS NULL AND start_instant IS NULL AND end_instant IS NULL)
    ),
    CHECK ((temporal_kind <> 'DATE') OR end_date > start_date),
    CHECK ((temporal_kind NOT IN ('FLOATING','ZONED')) OR end_local > start_local),
    CHECK ((temporal_kind <> 'UTC') OR end_instant > start_instant)
);

-- Existing pre-normalization Calendar rows are ZONED or DATE by construction.
INSERT INTO public.weave_calendar_event_temporals(
    calendar_id, scope_key, event_id, temporal_kind,
    start_date, end_date, start_local, end_local, start_instant, end_instant, timezone_id)
SELECT calendar_id, scope_key, event_id,
       CASE WHEN all_day THEN 'DATE' ELSE 'ZONED' END,
       CASE WHEN all_day THEN local_start::date END,
       CASE WHEN all_day THEN local_end::date END,
       CASE WHEN NOT all_day THEN local_start END,
       CASE WHEN NOT all_day THEN local_end END,
       NULL, NULL,
       CASE WHEN NOT all_day THEN timezone_id END
FROM public.weave_calendar_events
ON CONFLICT DO NOTHING;

CREATE INDEX weave_calendar_event_temporals_date_window_idx
    ON public.weave_calendar_event_temporals(calendar_id, scope_key, start_date, end_date)
    WHERE temporal_kind = 'DATE';
CREATE INDEX weave_calendar_event_temporals_local_window_idx
    ON public.weave_calendar_event_temporals(calendar_id, scope_key, start_local, end_local)
    WHERE temporal_kind IN ('FLOATING','ZONED');
CREATE INDEX weave_calendar_event_temporals_instant_window_idx
    ON public.weave_calendar_event_temporals(calendar_id, scope_key, start_instant, end_instant)
    WHERE temporal_kind = 'UTC';

CREATE TABLE public.weave_calendar_attendees (
    calendar_id varchar(96) NOT NULL,
    scope_key varchar(768) NOT NULL,
    event_id varchar(512) NOT NULL,
    ordinal integer NOT NULL,
    member_ref varchar(512),
    display_name varchar(1024),
    address varchar(2048),
    attendee_role varchar(128),
    response_state varchar(128),
    PRIMARY KEY (calendar_id, scope_key, event_id, ordinal),
    CHECK (ordinal >= 0),
    CHECK (member_ref IS NOT NULL OR address IS NOT NULL)
);

CREATE TABLE public.weave_calendar_recurrence_rules (
    calendar_id varchar(96) NOT NULL,
    scope_key varchar(768) NOT NULL,
    event_id varchar(512) NOT NULL,
    frequency varchar(16) NOT NULL,
    interval_value integer NOT NULL DEFAULT 1,
    count_value integer,
    until_local timestamp without time zone,
    until_instant timestamptz,
    until_timezone_id varchar(255),
    by_day text,
    by_month_day text,
    by_month text,
    by_set_pos text,
    week_start varchar(2),
    PRIMARY KEY (calendar_id, scope_key, event_id),
    CHECK (frequency IN ('DAILY','WEEKLY','MONTHLY','YEARLY')),
    CHECK (interval_value > 0),
    CHECK (count_value IS NULL OR count_value > 0),
    CHECK (NOT (count_value IS NOT NULL AND (until_local IS NOT NULL OR until_instant IS NOT NULL)))
);

CREATE TABLE public.weave_calendar_recurrence_dates (
    calendar_id varchar(96) NOT NULL,
    scope_key varchar(768) NOT NULL,
    event_id varchar(512) NOT NULL,
    recurrence_type varchar(8) NOT NULL,
    ordinal integer NOT NULL,
    temporal_kind varchar(16) NOT NULL,
    date_value date,
    local_value timestamp without time zone,
    instant_value timestamptz,
    timezone_id varchar(255),
    PRIMARY KEY (calendar_id, scope_key, event_id, recurrence_type, ordinal),
    CHECK (recurrence_type IN ('RDATE','EXDATE')),
    CHECK (temporal_kind IN ('DATE','FLOATING','UTC','ZONED')),
    CHECK (ordinal >= 0),
    CHECK (
        (temporal_kind = 'DATE' AND date_value IS NOT NULL AND local_value IS NULL AND instant_value IS NULL AND timezone_id IS NULL)
        OR (temporal_kind = 'FLOATING' AND date_value IS NULL AND local_value IS NOT NULL AND instant_value IS NULL AND timezone_id IS NULL)
        OR (temporal_kind = 'UTC' AND date_value IS NULL AND local_value IS NULL AND instant_value IS NOT NULL AND timezone_id IS NULL)
        OR (temporal_kind = 'ZONED' AND date_value IS NULL AND local_value IS NOT NULL AND instant_value IS NULL AND timezone_id IS NOT NULL)
    )
);

CREATE TABLE public.weave_calendar_event_overrides (
    calendar_id varchar(96) NOT NULL,
    scope_key varchar(768) NOT NULL,
    event_id varchar(512) NOT NULL,
    recurrence_id_key varchar(768) NOT NULL,
    temporal_kind varchar(16) NOT NULL,
    recurrence_date date,
    recurrence_local timestamp without time zone,
    recurrence_instant timestamptz,
    recurrence_timezone_id varchar(255),
    cancelled boolean NOT NULL DEFAULT false,
    start_date date,
    end_date date,
    start_local timestamp without time zone,
    end_local timestamp without time zone,
    start_instant timestamptz,
    end_instant timestamptz,
    timezone_id varchar(255),
    title varchar(1024),
    description varchar(8192),
    location varchar(2048),
    PRIMARY KEY (calendar_id, scope_key, event_id, recurrence_id_key),
    CHECK (temporal_kind IN ('DATE','FLOATING','UTC','ZONED'))
);

CREATE TABLE public.weave_calendar_timezone_definitions (
    timezone_id varchar(255) NOT NULL,
    definition_hash varchar(64) NOT NULL,
    icalendar_definition text NOT NULL,
    observed_at_utc timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (timezone_id, definition_hash)
);

CREATE TABLE public.weave_calendar_extension_properties (
    calendar_id varchar(96) NOT NULL,
    scope_key varchar(768) NOT NULL,
    event_id varchar(512) NOT NULL,
    property_name varchar(255) NOT NULL,
    ordinal integer NOT NULL,
    property_value text NOT NULL,
    PRIMARY KEY (calendar_id, scope_key, event_id, property_name, ordinal),
    CHECK (property_name LIKE 'X-%'),
    CHECK (ordinal >= 0)
);

-- Chat commit-ordered synchronization and exact idempotency authority.
CREATE TABLE public.weave_chat_sync_heads (
    tenant_id varchar(160) PRIMARY KEY,
    revision bigint NOT NULL DEFAULT 0,
    row_version bigint NOT NULL DEFAULT 0,
    updated_at_utc timestamptz NOT NULL DEFAULT now(),
    CHECK (revision >= 0)
);

CREATE TABLE public.weave_chat_idempotency (
    tenant_id varchar(160) NOT NULL,
    user_id varchar(512) NOT NULL,
    device_id varchar(128) NOT NULL,
    http_method varchar(16) NOT NULL,
    endpoint_identity varchar(512) NOT NULL,
    transaction_id varchar(255) NOT NULL,
    request_digest varchar(64) NOT NULL,
    response_status integer,
    response_body text,
    response_headers_json text,
    lifecycle_state varchar(24) NOT NULL DEFAULT 'pending',
    created_at_utc timestamptz NOT NULL DEFAULT now(),
    completed_at_utc timestamptz,
    PRIMARY KEY (tenant_id, user_id, device_id, http_method, endpoint_identity, transaction_id),
    CHECK (lifecycle_state IN ('pending','committed','expired')),
    CHECK (request_digest ~ '^[0-9a-f]{64}$')
);
CREATE INDEX weave_chat_idempotency_retention_idx
    ON public.weave_chat_idempotency(created_at_utc, lifecycle_state);

-- Matrix facade routing metadata. These are protocol-edge tables, not a second Chat authority.
CREATE TABLE public.weave_matrix_devices (
    tenant_id varchar(160) NOT NULL,
    user_id varchar(512) NOT NULL,
    device_id varchar(128) NOT NULL,
    device_keys_json text,
    changed_revision bigint NOT NULL DEFAULT 0,
    revoked boolean NOT NULL DEFAULT false,
    row_version bigint NOT NULL DEFAULT 0,
    updated_at_utc timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, user_id, device_id),
    CHECK (changed_revision >= 0)
);

CREATE TABLE public.weave_matrix_one_time_keys (
    tenant_id varchar(160) NOT NULL,
    user_id varchar(512) NOT NULL,
    device_id varchar(128) NOT NULL,
    key_id varchar(320) NOT NULL,
    algorithm varchar(128) NOT NULL,
    key_json text NOT NULL,
    uploaded_at_utc timestamptz NOT NULL DEFAULT now(),
    claimed_at_utc timestamptz,
    claim_ref uuid,
    PRIMARY KEY (tenant_id, user_id, device_id, key_id)
);
CREATE INDEX weave_matrix_otk_claim_idx
    ON public.weave_matrix_one_time_keys(tenant_id, user_id, device_id, algorithm, key_id)
    WHERE claimed_at_utc IS NULL;

CREATE TABLE public.weave_matrix_fallback_keys (
    tenant_id varchar(160) NOT NULL,
    user_id varchar(512) NOT NULL,
    device_id varchar(128) NOT NULL,
    key_id varchar(320) NOT NULL,
    algorithm varchar(128) NOT NULL,
    key_json text NOT NULL,
    used boolean NOT NULL DEFAULT false,
    updated_at_utc timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, user_id, device_id, key_id)
);

CREATE TABLE public.weave_matrix_cross_signing_keys (
    tenant_id varchar(160) NOT NULL,
    user_id varchar(512) NOT NULL,
    usage varchar(32) NOT NULL,
    key_id varchar(320) NOT NULL,
    key_json text NOT NULL,
    changed_revision bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, user_id, usage),
    CHECK (usage IN ('master','self_signing','user_signing'))
);

CREATE TABLE public.weave_matrix_to_device_messages (
    sequence_id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    tenant_id varchar(160) NOT NULL,
    target_user_id varchar(512) NOT NULL,
    target_device_id varchar(128) NOT NULL,
    sender_user_id varchar(512) NOT NULL,
    event_type varchar(255) NOT NULL,
    transaction_id varchar(255) NOT NULL,
    content_json text NOT NULL,
    created_at_utc timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, sender_user_id, transaction_id, target_user_id, target_device_id, event_type)
);
CREATE INDEX weave_matrix_to_device_sync_idx
    ON public.weave_matrix_to_device_messages(tenant_id, target_user_id, target_device_id, sequence_id);

CREATE TABLE public.weave_matrix_device_sync_progress (
    tenant_id varchar(160) NOT NULL,
    user_id varchar(512) NOT NULL,
    device_id varchar(128) NOT NULL,
    to_device_sequence bigint NOT NULL DEFAULT 0,
    device_list_revision bigint NOT NULL DEFAULT 0,
    row_version bigint NOT NULL DEFAULT 0,
    updated_at_utc timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, user_id, device_id),
    CHECK (to_device_sequence >= 0),
    CHECK (device_list_revision >= 0)
);

CREATE TABLE public.weave_matrix_account_data (
    tenant_id varchar(160) NOT NULL,
    user_id varchar(512) NOT NULL,
    event_type varchar(255) NOT NULL,
    content_json text NOT NULL,
    changed_revision bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, user_id, event_type)
);

CREATE TABLE public.weave_matrix_key_backup_versions (
    tenant_id varchar(160) NOT NULL,
    user_id varchar(512) NOT NULL,
    version_id bigint NOT NULL,
    algorithm varchar(128) NOT NULL,
    auth_data_json text NOT NULL,
    current_version boolean NOT NULL DEFAULT false,
    revision bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, user_id, version_id)
);
CREATE UNIQUE INDEX weave_matrix_key_backup_current_idx
    ON public.weave_matrix_key_backup_versions(tenant_id, user_id)
    WHERE current_version;

CREATE TABLE public.weave_matrix_key_backup_sessions (
    tenant_id varchar(160) NOT NULL,
    user_id varchar(512) NOT NULL,
    version_id bigint NOT NULL,
    room_id varchar(512) NOT NULL,
    session_id varchar(512) NOT NULL,
    payload_json text NOT NULL,
    PRIMARY KEY (tenant_id, user_id, version_id, room_id, session_id)
);

CREATE TABLE public.weave_matrix_device_list_revisions (
    tenant_id varchar(160) NOT NULL,
    observing_user_id varchar(512) NOT NULL,
    observing_device_id varchar(128) NOT NULL,
    subject_user_id varchar(512) NOT NULL,
    shared boolean NOT NULL,
    changed_revision bigint NOT NULL,
    PRIMARY KEY (tenant_id, observing_user_id, observing_device_id, subject_user_id)
);

CREATE TABLE public.weave_matrix_oidc_device_bindings (
    tenant_id varchar(160) NOT NULL,
    user_id varchar(512) NOT NULL,
    oidc_session_hash varchar(64) NOT NULL,
    device_id varchar(128) NOT NULL,
    bound_at_utc timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, user_id, oidc_session_hash),
    UNIQUE (tenant_id, user_id, device_id, oidc_session_hash)
);

-- The legacy tenant-wide snapshot is retained only until the application cutover
-- in V3. New code must not write it. V3 drops it after normalized rows are authoritative.

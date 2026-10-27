--
-- PostgreSQL database dump
--


-- Dumped from database version 16.9
-- Dumped by pg_dump version 16.14 (Ubuntu 16.14-1.pgdg24.04+1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: weave_agent_runtime_audit_correlations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_agent_runtime_audit_correlations (
    record_id uuid NOT NULL,
    correlation_ref character varying(255) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    domain_audit_ref_hash character varying(71),
    keycloak_ref_hash character varying(71),
    matrix_ref_hash character varying(71),
    mcp_ref_hash character varying(71),
    occurred_at timestamp(6) with time zone NOT NULL,
    openclaw_ref_hash character varying(71),
    orchestrator_ref_hash character varying(71),
    organization_ref_hash character varying(71) NOT NULL,
    person_ref_hash character varying(71) NOT NULL
);


--
-- Name: weave_agent_runtime_cells; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_agent_runtime_cells (
    record_id uuid NOT NULL,
    audit_ref character varying(255) NOT NULL,
    cell_ref character varying(255) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    desired_state character varying(32) NOT NULL,
    entitlement_revision character varying(255) NOT NULL,
    entitlement_state character varying(32) NOT NULL,
    fencing_epoch bigint NOT NULL,
    lease_expires_at timestamp(6) with time zone,
    lease_id uuid,
    member_issuer character varying(500) NOT NULL,
    member_subject character varying(255) NOT NULL,
    observed_state character varying(32) NOT NULL,
    organization_ref character varying(255) NOT NULL,
    person_ref character varying(255) NOT NULL,
    runtime_profile_hash character varying(71),
    runtime_profile_id character varying(255),
    runtime_state_store_ref character varying(1000) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    version bigint NOT NULL,
    workload_authentication_method character varying(64) NOT NULL,
    workload_client_id character varying(255) NOT NULL,
    workload_credential_ref character varying(1000) NOT NULL,
    workload_issuer character varying(500) NOT NULL,
    workload_subject character varying(255) NOT NULL,
    workspace_manifest_ref character varying(1000) NOT NULL,
    workspace_revision character varying(255) NOT NULL,
    CONSTRAINT weave_agent_runtime_cells_desired_state_check CHECK (((desired_state)::text = ANY ((ARRAY['ABSENT'::character varying, 'PROVISIONING'::character varying, 'STOPPED'::character varying, 'STARTING'::character varying, 'MATERIALIZING'::character varying, 'READY'::character varying, 'BUSY'::character varying, 'SYNCING'::character varying, 'DEGRADED'::character varying, 'SUSPENDED'::character varying, 'REVOKING'::character varying, 'DELETING'::character varying, 'DELETED'::character varying])::text[]))),
    CONSTRAINT weave_agent_runtime_cells_entitlement_state_check CHECK (((entitlement_state)::text = ANY ((ARRAY['ENTITLED'::character varying, 'NOT_ENTITLED'::character varying, 'REVOKED'::character varying])::text[]))),
    CONSTRAINT weave_agent_runtime_cells_observed_state_check CHECK (((observed_state)::text = ANY ((ARRAY['ABSENT'::character varying, 'PROVISIONING'::character varying, 'STOPPED'::character varying, 'STARTING'::character varying, 'MATERIALIZING'::character varying, 'READY'::character varying, 'BUSY'::character varying, 'SYNCING'::character varying, 'DEGRADED'::character varying, 'SUSPENDED'::character varying, 'REVOKING'::character varying, 'DELETING'::character varying, 'DELETED'::character varying])::text[]))),
    CONSTRAINT weave_agent_runtime_cells_workload_authentication_method_check CHECK (((workload_authentication_method)::text = ANY ((ARRAY['PRIVATE_KEY_JWT'::character varying, 'CLIENT_SECRET_BASIC'::character varying])::text[])))
);


--
-- Name: weave_agent_runtime_commands; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_agent_runtime_commands (
    idempotency_key character varying(128) NOT NULL,
    organization_ref character varying(255) NOT NULL,
    person_ref character varying(255) NOT NULL,
    audit_ref character varying(255) NOT NULL,
    cell_ref character varying(255) NOT NULL,
    command character varying(64) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    failure_code character varying(100),
    runtime_version bigint,
    status character varying(32) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT weave_agent_runtime_commands_status_check CHECK (((status)::text = ANY ((ARRAY['STARTED'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: weave_agent_runtime_entitlements; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_agent_runtime_entitlements (
    record_id uuid NOT NULL,
    audit_ref character varying(255) NOT NULL,
    capability_revision character varying(71) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    effective_at timestamp(6) with time zone NOT NULL,
    entitlement_ref character varying(255) NOT NULL,
    entitlement_revision character varying(71) NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL,
    last_observed_at timestamp(6) with time zone NOT NULL,
    member_issuer character varying(500) NOT NULL,
    member_subject character varying(255) NOT NULL,
    organization_ref character varying(255) NOT NULL,
    person_ref character varying(255) NOT NULL,
    revocation_ref character varying(255),
    revoked_at timestamp(6) with time zone,
    source_group_ref character varying(71) NOT NULL,
    source_provider character varying(64) NOT NULL,
    entitlement_state character varying(32) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT weave_agent_runtime_entitlements_entitlement_state_check CHECK (((entitlement_state)::text = ANY ((ARRAY['ENTITLED'::character varying, 'NOT_ENTITLED'::character varying, 'REVOKED'::character varying])::text[])))
);


--
-- Name: weave_agent_runtime_profile_signatures; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_agent_runtime_profile_signatures (
    key_id character varying(255) NOT NULL,
    profile_hash character varying(71) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    protected_header text NOT NULL,
    signature text NOT NULL
);


--
-- Name: weave_agent_runtime_profiles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_agent_runtime_profiles (
    profile_hash character varying(71) NOT NULL,
    cell_ref character varying(255) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL,
    issued_at timestamp(6) with time zone NOT NULL,
    organization_ref character varying(255) NOT NULL,
    payload text NOT NULL,
    person_ref character varying(255) NOT NULL,
    profile_id character varying(255) NOT NULL,
    revocation_code character varying(100),
    revoked_at timestamp(6) with time zone,
    selected_key_id character varying(255) NOT NULL,
    version bigint NOT NULL
);


--
-- Name: weave_agent_runtime_revocations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_agent_runtime_revocations (
    record_id uuid NOT NULL,
    actor_ref_hash character varying(71) NOT NULL,
    audit_correlation_ref character varying(255) NOT NULL,
    cell_ref character varying(255) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    effective_at timestamp(6) with time zone NOT NULL,
    entitlement_ref character varying(255) NOT NULL,
    entitlement_revision character varying(71) NOT NULL,
    organization_ref character varying(255) NOT NULL,
    person_ref character varying(255) NOT NULL,
    profile_hash character varying(71),
    reason_code character varying(100) NOT NULL,
    reason_ref_hash character varying(71) NOT NULL,
    revocation_ref character varying(255) NOT NULL,
    workload_ref_hash character varying(71) NOT NULL
);


--
-- Name: weave_agent_runtime_state_deletions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_agent_runtime_state_deletions (
    idempotency_key character varying(128) NOT NULL,
    organization_ref character varying(255) NOT NULL,
    person_ref character varying(255) NOT NULL,
    audit_ref character varying(255) NOT NULL,
    cell_ref character varying(255) NOT NULL,
    completed_at timestamp(6) with time zone NOT NULL,
    deleted_generation_count bigint NOT NULL,
    runtime_state_store_ref character varying(1000) NOT NULL
);


--
-- Name: weave_agent_runtime_state_generations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_agent_runtime_state_generations (
    generation_ref character varying(81) NOT NULL,
    audit_ref character varying(255) NOT NULL,
    chunk_count integer NOT NULL,
    ciphertext_bytes bigint NOT NULL,
    committed_at timestamp(6) with time zone NOT NULL,
    encryption_algorithm character varying(64) NOT NULL,
    generation bigint NOT NULL,
    idempotency_key character varying(128) NOT NULL,
    nonce bytea NOT NULL,
    plaintext_bytes bigint NOT NULL,
    previous_generation bigint NOT NULL,
    runtime_profile_hash character varying(71) NOT NULL,
    runtime_state_store_ref character varying(1000) NOT NULL,
    wrapped_data_key bytea NOT NULL,
    wrapping_key_ref character varying(128) NOT NULL
);


--
-- Name: weave_agent_runtime_state_heads; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_agent_runtime_state_heads (
    runtime_state_store_ref character varying(1000) NOT NULL,
    audit_ref character varying(255) NOT NULL,
    cell_ref character varying(255) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    current_generation bigint NOT NULL,
    current_generation_ref character varying(81),
    organization_ref character varying(255) NOT NULL,
    person_ref character varying(255) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    version bigint NOT NULL
);


--
-- Name: weave_audit_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_audit_events (
    sequence_id bigint NOT NULL,
    action character varying(120) NOT NULL,
    actor_ref character varying(255) NOT NULL,
    context_id character varying(255),
    idempotency_key character varying(255) NOT NULL,
    occurred_at_utc timestamp(6) with time zone NOT NULL,
    payload_json text NOT NULL,
    redaction_level character varying(80) NOT NULL,
    source_ref character varying(255) NOT NULL,
    tenant_id character varying(160) NOT NULL
);


--
-- Name: weave_audit_events_sequence_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE weave_audit_events ALTER COLUMN sequence_id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME weave_audit_events_sequence_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: weave_calendar_changes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_calendar_changes (
    calendar_id character varying(96) NOT NULL,
    change_sequence bigint NOT NULL,
    scope_key character varying(768) NOT NULL,
    changed_at_utc timestamp(6) with time zone NOT NULL,
    deleted boolean NOT NULL,
    event_id character varying(512) NOT NULL,
    event_version character varying(128) NOT NULL
);


--
-- Name: weave_calendar_collections; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_calendar_collections (
    calendar_id character varying(96) NOT NULL,
    scope_key character varying(768) NOT NULL,
    channel_id character varying(255),
    latest_change_sequence bigint NOT NULL,
    row_version bigint NOT NULL,
    scope_type character varying(32) NOT NULL,
    team_id character varying(255),
    updated_at_utc timestamp(6) with time zone NOT NULL,
    CONSTRAINT weave_calendar_collections_scope_type_check CHECK (((scope_type)::text = ANY ((ARRAY['WORKSPACE'::character varying, 'TEAM'::character varying, 'CHANNEL'::character varying])::text[])))
);


--
-- Name: weave_calendar_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_calendar_events (
    calendar_id character varying(96) NOT NULL,
    event_id character varying(512) NOT NULL,
    scope_key character varying(768) NOT NULL,
    additional_date_count integer NOT NULL,
    all_day boolean NOT NULL,
    attendee_state character varying(65535) NOT NULL,
    change_sequence bigint NOT NULL,
    channel_id character varying(255),
    deleted boolean NOT NULL,
    description character varying(8192),
    event_version character varying(128) NOT NULL,
    local_end timestamp(6) without time zone NOT NULL,
    local_start timestamp(6) without time zone NOT NULL,
    location character varying(2048),
    recurrence_count integer,
    recurrence_date_state character varying(65535) NOT NULL,
    recurrence_frequency character varying(32),
    recurrence_interval integer,
    recurrence_until_local timestamp(6) without time zone,
    recurrence_until_timezone character varying(255),
    row_version bigint NOT NULL,
    scope_type character varying(32) NOT NULL,
    team_id character varying(255),
    timezone_id character varying(255) NOT NULL,
    title character varying(1024) NOT NULL,
    updated_at_utc timestamp(6) with time zone NOT NULL,
    CONSTRAINT weave_calendar_events_recurrence_frequency_check CHECK (((recurrence_frequency)::text = ANY ((ARRAY['DAILY'::character varying, 'WEEKLY'::character varying])::text[]))),
    CONSTRAINT weave_calendar_events_scope_type_check CHECK (((scope_type)::text = ANY ((ARRAY['WORKSPACE'::character varying, 'TEAM'::character varying, 'CHANNEL'::character varying])::text[])))
);


--
-- Name: weave_chat_appservice_transactions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_chat_appservice_transactions (
    provider_key character varying(64) NOT NULL,
    homeserver_transaction_id character varying(255) NOT NULL,
    completed_at_utc timestamp(6) with time zone,
    duplicate_count integer NOT NULL,
    event_count integer NOT NULL,
    semantic_fingerprint_version character varying(64) NOT NULL,
    payload_digest character varying(64) NOT NULL,
    received_at_utc timestamp(6) with time zone NOT NULL,
    semantic_mismatch_count integer NOT NULL,
    semantic_mismatch_hash character varying(64),
    transaction_state character varying(32) NOT NULL,
    version bigint NOT NULL
);


--
-- Name: weave_chat_bridge_ledger; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_chat_bridge_ledger (
    tenant_id character varying(160) NOT NULL,
    ledger_id character varying(96) NOT NULL,
    canonical_object_id character varying(255),
    direction character varying(32) NOT NULL,
    observed_at_utc timestamp(6) with time zone NOT NULL,
    provider_event_ref character varying(768),
    provider_key character varying(64) NOT NULL,
    provider_transaction_id character varying(255),
    source_version character varying(255),
    ledger_state character varying(32) NOT NULL,
    version bigint NOT NULL
);


--
-- Name: weave_chat_changes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_chat_changes (
    sequence_value bigint NOT NULL,
    callback_deduplication_key character varying(96),
    canonical_object_id character varying(255),
    conversation_id character varying(160) NOT NULL,
    change_kind character varying(64) NOT NULL,
    occurred_at_utc timestamp(6) with time zone NOT NULL,
    tenant_id character varying(160) NOT NULL
);


--
-- Name: weave_chat_changes_sequence_value_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE weave_chat_changes ALTER COLUMN sequence_value ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME weave_chat_changes_sequence_value_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: weave_chat_conversations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_chat_conversations (
    tenant_id character varying(160) NOT NULL,
    conversation_id character varying(160) NOT NULL,
    context_id character varying(160) NOT NULL,
    created_at_utc timestamp(6) with time zone NOT NULL,
    encryption_mode character varying(128) NOT NULL,
    conversation_kind character varying(64) NOT NULL,
    lifecycle_state character varying(32) NOT NULL,
    next_event_sequence bigint NOT NULL,
    open_to_workspace boolean NOT NULL,
    title character varying(512) NOT NULL,
    updated_at_utc timestamp(6) with time zone NOT NULL,
    version bigint NOT NULL
);


--
-- Name: weave_chat_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_chat_events (
    tenant_id character varying(160) NOT NULL,
    conversation_id character varying(160) NOT NULL,
    event_id character varying(255) NOT NULL,
    content_json text NOT NULL,
    delivery_state character varying(32) NOT NULL,
    event_kind character varying(32) NOT NULL,
    occurred_at_utc timestamp(6) with time zone NOT NULL,
    redacted boolean NOT NULL,
    sender_issuer character varying(512) NOT NULL,
    sender_ref character varying(255) NOT NULL,
    sequence_value bigint NOT NULL,
    version bigint NOT NULL
);


--
-- Name: weave_chat_memberships; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_chat_memberships (
    tenant_id character varying(160) NOT NULL,
    conversation_id character varying(160) NOT NULL,
    identity_issuer character varying(512) NOT NULL,
    actor_ref character varying(255) NOT NULL,
    invited_at_utc timestamp(6) with time zone,
    joined_at_utc timestamp(6) with time zone,
    member_role character varying(32) NOT NULL,
    membership_state character varying(32) NOT NULL,
    updated_at_utc timestamp(6) with time zone NOT NULL,
    version bigint NOT NULL
);


--
-- Name: weave_chat_operations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_chat_operations (
    tenant_id character varying(160) NOT NULL,
    operation_id character varying(96) NOT NULL,
    actor_ref character varying(255) NOT NULL,
    attempt_count integer NOT NULL,
    canonical_object_id character varying(255) NOT NULL,
    context_id character varying(160) NOT NULL,
    conversation_id character varying(160) NOT NULL,
    created_at_utc timestamp(6) with time zone NOT NULL,
    identity_issuer character varying(512) NOT NULL,
    last_error_code character varying(96),
    northbound_transaction_id character varying(160) NOT NULL,
    operation_type character varying(48) NOT NULL,
    payload_digest character varying(64) NOT NULL,
    provider_alias_intent character varying(255),
    provider_transaction_id character varying(160) NOT NULL,
    operation_state character varying(32) NOT NULL,
    updated_at_utc timestamp(6) with time zone NOT NULL,
    version bigint NOT NULL
);


--
-- Name: weave_chat_outbox; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_chat_outbox (
    tenant_id character varying(160) NOT NULL,
    operation_id character varying(96) NOT NULL,
    attempt_count integer NOT NULL,
    created_at_utc timestamp(6) with time zone NOT NULL,
    last_error_code character varying(96),
    next_attempt_at_utc timestamp(6) with time zone,
    operation_type character varying(48) NOT NULL,
    payload_json text NOT NULL,
    provider_transaction_id character varying(160) NOT NULL,
    outbox_state character varying(32) NOT NULL,
    updated_at_utc timestamp(6) with time zone NOT NULL,
    version bigint NOT NULL
);


--
-- Name: weave_chat_provider_mappings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_chat_provider_mappings (
    tenant_id character varying(160) NOT NULL,
    provider_key character varying(64) NOT NULL,
    object_type character varying(32) NOT NULL,
    canonical_object_id character varying(768) NOT NULL,
    mapping_intent_ref character varying(768),
    provider_ref character varying(768),
    provider_source_version character varying(255),
    mapping_state character varying(32) NOT NULL,
    updated_at_utc timestamp(6) with time zone NOT NULL,
    version bigint NOT NULL
);


--
-- Name: weave_chat_quarantine; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_chat_quarantine (
    tenant_id character varying(160) NOT NULL,
    quarantine_id character varying(96) NOT NULL,
    attempt_count integer NOT NULL,
    category_code character varying(96) NOT NULL,
    classifier_version character varying(64) NOT NULL,
    conversation_id character varying(160),
    correlation_hash character varying(64) NOT NULL,
    last_attempt_at_utc timestamp(6) with time zone,
    last_outcome_code character varying(96),
    lifecycle_state character varying(32) NOT NULL,
    max_attempts integer NOT NULL,
    private_normalized_event_json text,
    observed_at_utc timestamp(6) with time zone NOT NULL,
    private_provider_event_ref character varying(768),
    private_provider_room_ref character varying(768),
    private_homeserver_transaction_id character varying(255),
    provider_key character varying(64) NOT NULL,
    reason_code character varying(96) NOT NULL,
    recoverable boolean NOT NULL,
    resolved_at_utc timestamp(6) with time zone,
    version bigint NOT NULL
);


--
-- Name: weave_chat_read_receipts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_chat_read_receipts (
    tenant_id character varying(160) NOT NULL,
    conversation_id character varying(160) NOT NULL,
    identity_issuer character varying(512) NOT NULL,
    actor_ref character varying(255) NOT NULL,
    event_id character varying(255) NOT NULL,
    read_at_utc timestamp(6) with time zone NOT NULL,
    version bigint NOT NULL
);


--
-- Name: weave_device_credentials; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_device_credentials (
    credential_id character varying(160) NOT NULL,
    capabilities_json text NOT NULL,
    client_type character varying(80) NOT NULL,
    domain character varying(40) NOT NULL,
    expires_at_utc timestamp(6) with time zone NOT NULL,
    issued_at_utc timestamp(6) with time zone NOT NULL,
    label character varying(255) NOT NULL,
    principal_ref character varying(255) NOT NULL,
    revoked_at_utc timestamp(6) with time zone,
    secret_hash text NOT NULL,
    subject_ref character varying(255) NOT NULL,
    tenant_id character varying(160) NOT NULL,
    username character varying(255) NOT NULL,
    version bigint NOT NULL
);


--
-- Name: weave_file_locks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_file_locks (
    canonical_path character varying(2048) NOT NULL,
    organization_ref character varying(255) NOT NULL,
    space_ref character varying(255) NOT NULL,
    created_at_utc timestamp(6) with time zone NOT NULL,
    expires_at_utc timestamp(6) with time zone NOT NULL,
    fence bigint NOT NULL,
    owner_ref character varying(255) NOT NULL,
    released_at_utc timestamp(6) with time zone,
    token_digest character varying(71) NOT NULL,
    version bigint NOT NULL
);


--
-- Name: weave_files_objects; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_files_objects (
    file_id character varying(255) NOT NULL,
    organization_ref character varying(255) NOT NULL,
    space_ref character varying(255) NOT NULL,
    active_path_key character varying(2048),
    byte_size bigint NOT NULL,
    canonical_path character varying(2048) NOT NULL,
    content_digest character varying(71),
    hidden boolean NOT NULL,
    object_kind character varying(32) NOT NULL,
    lifecycle_state character varying(32) NOT NULL,
    media_type character varying(255),
    modified_at_utc timestamp(6) with time zone,
    observed_at_utc timestamp(6) with time zone NOT NULL,
    provider_binding_revision bigint NOT NULL,
    storage_reference character varying(1024),
    version bigint NOT NULL,
    version_token character varying(1024),
    CONSTRAINT weave_files_objects_lifecycle_state_check CHECK (((lifecycle_state)::text = ANY ((ARRAY['ACTIVE'::character varying, 'TOMBSTONED'::character varying])::text[]))),
    CONSTRAINT weave_files_objects_object_kind_check CHECK (((object_kind)::text = ANY ((ARRAY['FILE'::character varying, 'COLLECTION'::character varying])::text[])))
);


--
-- Name: weave_identity_admin_operations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_identity_admin_operations (
    idempotency_key character varying(128) NOT NULL,
    organization_id character varying(255) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    operation_kind character varying(80) NOT NULL,
    request_hash character varying(64) NOT NULL,
    response_json character varying(16384),
    status character varying(20) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL
);


--
-- Name: weave_identity_provisioning_intents; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_identity_provisioning_intents (
    intent_id uuid NOT NULL,
    applied_subject character varying(255),
    audit_correlation character varying(255) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL,
    failure_code character varying(100),
    invited_by_issuer character varying(500) NOT NULL,
    invited_by_subject character varying(255) NOT NULL,
    invited_email character varying(320) NOT NULL,
    invited_email_sha256 character varying(64) NOT NULL,
    organization_id character varying(160) NOT NULL,
    provider_invitation_id character varying(200),
    requested_role character varying(32) NOT NULL,
    status character varying(32) NOT NULL,
    tenant_id character varying(160) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    version bigint NOT NULL
);


--
-- Name: weave_matrix_e2ee_snapshots; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_matrix_e2ee_snapshots (
    tenant_id character varying(160) NOT NULL,
    payload_json text NOT NULL,
    sequence_value bigint NOT NULL,
    updated_at_utc timestamp(6) with time zone NOT NULL,
    version bigint NOT NULL
);


--
-- Name: weave_matrix_identity_projection; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_matrix_identity_projection (
    identity_issuer character varying(512) NOT NULL,
    matrix_user_id character varying(255) NOT NULL,
    tenant_id character varying(160) NOT NULL,
    actor_ref character varying(255) NOT NULL,
    authorization_principal_ref character varying(255) NOT NULL,
    updated_at_utc timestamp(6) with time zone NOT NULL,
    version bigint NOT NULL
);


--
-- Name: weave_matrix_revoked_sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_matrix_revoked_sessions (
    session_hash character varying(64) NOT NULL,
    expires_at_utc timestamp(6) with time zone NOT NULL,
    revoked_at_utc timestamp(6) with time zone NOT NULL
);


--
-- Name: weave_migration_run_evidence; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_migration_run_evidence (
    domain_key character varying(120) NOT NULL,
    run_id character varying(180) NOT NULL,
    admin_approved boolean NOT NULL,
    artifact_refs_json text NOT NULL,
    audit_refs_json text NOT NULL,
    audit_sink_available boolean NOT NULL,
    content_hashes_json text NOT NULL,
    expires_at_utc timestamp(6) with time zone,
    identity_mapping_complete boolean NOT NULL,
    lifecycle character varying(80) NOT NULL,
    object_counts_json text NOT NULL,
    provider_diagnostics_json text NOT NULL,
    recorded_at_utc timestamp(6) with time zone NOT NULL,
    version bigint NOT NULL
);


--
-- Name: weave_operation_intents; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_operation_intents (
    operation_ref character varying(255) NOT NULL,
    action_digest character varying(71) NOT NULL,
    actor_kind character varying(32) NOT NULL,
    audit_ref character varying(255),
    canonical_arguments_digest character varying(71) NOT NULL,
    cell_ref character varying(255),
    client_ref character varying(255),
    created_at_utc timestamp(6) with time zone NOT NULL,
    domain_key character varying(80) NOT NULL,
    entitlement_revision character varying(255) NOT NULL,
    fencing_epoch bigint,
    idempotency_key character varying(128) NOT NULL,
    initial_outbox_ref character varying(255) NOT NULL,
    intent_version character varying(64) NOT NULL,
    object_refs_json text NOT NULL,
    organization_ref character varying(255) NOT NULL,
    person_ref character varying(255) NOT NULL,
    policy_revision character varying(255) NOT NULL,
    profile_revision bigint,
    projection_kind character varying(32) NOT NULL,
    projection_value_1 character varying(255) NOT NULL,
    projection_value_2 character varying(255) NOT NULL,
    projection_value_3 character varying(255),
    provider_binding_revision bigint NOT NULL,
    provider_correlation_hash character varying(71),
    reconciliation_attempts integer NOT NULL,
    reconciliation_last_attempt_at_utc timestamp(6) with time zone,
    reconciliation_lease_until_utc timestamp(6) with time zone,
    reconciliation_max_attempts integer NOT NULL,
    reconciliation_outcome character varying(40),
    reconciliation_result_digest character varying(71),
    result_digest character varying(71),
    intent_state character varying(32) NOT NULL,
    subject_ref character varying(255),
    updated_at_utc timestamp(6) with time zone NOT NULL,
    version bigint NOT NULL
);


--
-- Name: weave_operation_outbox; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_operation_outbox (
    sequence_id bigint NOT NULL,
    attempt_count integer NOT NULL,
    created_at_utc timestamp(6) with time zone NOT NULL,
    delivered_at_utc timestamp(6) with time zone,
    delivery_state character varying(32) NOT NULL,
    event_type character varying(120) NOT NULL,
    next_attempt_at_utc timestamp(6) with time zone,
    operation_ref character varying(255) NOT NULL,
    outbox_ref character varying(255) NOT NULL,
    payload_json text NOT NULL,
    version bigint NOT NULL
);


--
-- Name: weave_operation_outbox_sequence_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE weave_operation_outbox ALTER COLUMN sequence_id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME weave_operation_outbox_sequence_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: weave_organization_bootstrap; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_organization_bootstrap (
    organization_id character varying(255) NOT NULL,
    actor_primary_identity_key character varying(512) NOT NULL,
    bootstrap_mode character varying(64) NOT NULL,
    bootstrapped_at timestamp(6) with time zone NOT NULL,
    retained_admin_primary_identity_keys_json text NOT NULL,
    version bigint NOT NULL
);


--
-- Name: weave_person_bindings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_person_bindings (
    issuer character varying(500) NOT NULL,
    organization_ref character varying(255) NOT NULL,
    subject character varying(255) NOT NULL,
    created_at_utc timestamp(6) with time zone NOT NULL,
    person_ref character varying(255) NOT NULL,
    version bigint NOT NULL
);


--
-- Name: weave_portability_fidelity_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_portability_fidelity_items (
    canonical_object_id character varying(255) NOT NULL,
    plan_ref character varying(255) NOT NULL,
    disposition character varying(32) NOT NULL,
    fidelity_class character varying(2) NOT NULL,
    recorded_at_utc timestamp(6) with time zone NOT NULL,
    CONSTRAINT weave_portability_fidelity_items_disposition_check CHECK (((disposition)::text = ANY ((ARRAY['PRESERVE'::character varying, 'TRANSFORM'::character varying, 'ARCHIVE'::character varying, 'BLOCK'::character varying])::text[]))),
    CONSTRAINT weave_portability_fidelity_items_fidelity_class_check CHECK (((fidelity_class)::text = ANY ((ARRAY['F0'::character varying, 'F1'::character varying, 'F2'::character varying, 'F3'::character varying, 'F4'::character varying])::text[])))
);


--
-- Name: weave_portability_plans; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_portability_plans (
    plan_ref character varying(255) NOT NULL,
    created_at_utc timestamp(6) with time zone NOT NULL,
    domain_key character varying(80) NOT NULL,
    lifecycle_state character varying(32) NOT NULL,
    organization_ref character varying(255) NOT NULL,
    source_binding_revision bigint NOT NULL,
    target_binding_revision bigint NOT NULL,
    updated_at_utc timestamp(6) with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT weave_portability_plans_lifecycle_state_check CHECK (((lifecycle_state)::text = ANY ((ARRAY['DRAFT'::character varying, 'DISCOVERING'::character varying, 'PREFLIGHT'::character varying, 'DRY_RUN'::character varying, 'REVIEW_REQUIRED'::character varying, 'APPROVED'::character varying, 'PREPARING'::character varying, 'COPYING'::character varying, 'DELTA_SYNC'::character varying, 'CUTOVER'::character varying, 'VERIFYING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying, 'ROLLBACK_READY'::character varying, 'ROLLED_BACK'::character varying])::text[])))
);


--
-- Name: weave_product_profile_overrides; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_product_profile_overrides (
    primary_identity_key character varying(528) NOT NULL,
    accessibility_preferences_json text NOT NULL,
    avatar character varying(512),
    display_name character varying(255),
    locale character varying(40),
    profile_visibility character varying(80),
    timezone character varying(80),
    version bigint NOT NULL
);


--
-- Name: weave_provider_bindings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_provider_bindings (
    binding_revision bigint NOT NULL,
    domain_key character varying(80) NOT NULL,
    organization_ref character varying(255) NOT NULL,
    activated_at_utc timestamp(6) with time zone NOT NULL,
    active_slot boolean,
    adapter_key character varying(160) NOT NULL,
    configuration_ref character varying(255) NOT NULL,
    binding_state character varying(32) NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT weave_provider_bindings_binding_state_check CHECK (((binding_state)::text = ANY ((ARRAY['ACTIVE'::character varying, 'RETIRED'::character varying, 'REVOKED'::character varying])::text[])))
);


--
-- Name: weave_provider_object_mappings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_provider_object_mappings (
    binding_revision bigint NOT NULL,
    canonical_object_id character varying(255) NOT NULL,
    domain_key character varying(80) NOT NULL,
    organization_ref character varying(255) NOT NULL,
    first_observed_at_utc timestamp(6) with time zone NOT NULL,
    last_observed_at_utc timestamp(6) with time zone NOT NULL,
    provenance character varying(255) NOT NULL,
    provider_object_ref character varying(1024) NOT NULL,
    version bigint NOT NULL
);


--
-- Name: weave_provider_selection_notes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_provider_selection_notes (
    category character varying(80) NOT NULL,
    note_order integer NOT NULL,
    note_text character varying(1024) NOT NULL
);


--
-- Name: weave_provider_selections; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_provider_selections (
    category character varying(80) NOT NULL,
    applied boolean NOT NULL,
    choice_model character varying(80) NOT NULL,
    migration_dry_run_required boolean NOT NULL,
    provider_key character varying(160) NOT NULL,
    secret_ref character varying(255),
    selected_at_utc timestamp(6) with time zone NOT NULL,
    selected_by character varying(160) NOT NULL,
    support_safe boolean NOT NULL,
    version bigint NOT NULL
);


--
-- Name: weave_schema_authority; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_schema_authority (
    epoch character varying(80) NOT NULL,
    candidate_commit character varying(40) NOT NULL,
    catalog_fingerprint character varying(64) NOT NULL,
    completed_at_utc timestamp(6) with time zone NOT NULL,
    relational_model_id character varying(160) NOT NULL
);


--
-- Name: weave_space_memberships; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_space_memberships (
    organization_ref character varying(255) NOT NULL,
    person_ref character varying(255) NOT NULL,
    space_ref character varying(255) NOT NULL,
    created_at_utc timestamp(6) with time zone NOT NULL,
    lifecycle_state character varying(32) NOT NULL,
    permission_set character varying(255) NOT NULL,
    updated_at_utc timestamp(6) with time zone NOT NULL,
    version bigint NOT NULL
);


--
-- Name: weave_spaces; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_spaces (
    organization_ref character varying(255) NOT NULL,
    space_ref character varying(255) NOT NULL,
    created_at_utc timestamp(6) with time zone NOT NULL,
    lifecycle_state character varying(32) NOT NULL,
    updated_at_utc timestamp(6) with time zone NOT NULL,
    version bigint NOT NULL
);


--
-- Name: weave_wake_envelopes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_wake_envelopes (
    cell_ref character varying(255) NOT NULL,
    organization_ref character varying(255) NOT NULL,
    wake_ref character varying(255) NOT NULL,
    attempt_count integer NOT NULL,
    created_at_utc timestamp(6) with time zone NOT NULL,
    delivered_at_utc timestamp(6) with time zone,
    delivery_state character varying(32) NOT NULL,
    event_digest character varying(71) NOT NULL,
    next_attempt_at_utc timestamp(6) with time zone,
    outbox_ref character varying(255) NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT weave_wake_envelopes_delivery_state_check CHECK (((delivery_state)::text = ANY ((ARRAY['PENDING'::character varying, 'DELIVERING'::character varying, 'DELIVERED'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: weave_workspace_revisions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE weave_workspace_revisions (
    organization_ref character varying(255) NOT NULL,
    person_ref character varying(255) NOT NULL,
    revision bigint NOT NULL,
    activated_at_utc timestamp(6) with time zone,
    active_slot boolean,
    created_at_utc timestamp(6) with time zone NOT NULL,
    lifecycle_state character varying(32) NOT NULL,
    manifest_digest character varying(71) NOT NULL,
    manifest_ref character varying(1000) NOT NULL,
    signature character varying(2048) NOT NULL,
    signature_key_ref character varying(255) NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT weave_workspace_revisions_lifecycle_state_check CHECK (((lifecycle_state)::text = ANY ((ARRAY['DRAFT'::character varying, 'ACTIVE'::character varying, 'SUPERSEDED'::character varying, 'REJECTED'::character varying])::text[])))
);


--
-- Name: weave_chat_events uk_weave_chat_events_conversation_sequence; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_chat_events
    ADD CONSTRAINT uk_weave_chat_events_conversation_sequence UNIQUE (tenant_id, conversation_id, sequence_value);


--
-- Name: weave_files_objects uk_weave_files_active_path; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_files_objects
    ADD CONSTRAINT uk_weave_files_active_path UNIQUE (organization_ref, space_ref, active_path_key);


--
-- Name: weave_identity_provisioning_intents ukqi0q5p045hxv0b6rda2c0x9u3; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_identity_provisioning_intents
    ADD CONSTRAINT ukqi0q5p045hxv0b6rda2c0x9u3 UNIQUE (provider_invitation_id);


--
-- Name: weave_audit_events uq_weave_audit_events_idempotency; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_audit_events
    ADD CONSTRAINT uq_weave_audit_events_idempotency UNIQUE (tenant_id, idempotency_key);


--
-- Name: weave_agent_runtime_audit_correlations weave_agent_runtime_audit_correlations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_agent_runtime_audit_correlations
    ADD CONSTRAINT weave_agent_runtime_audit_correlations_pkey PRIMARY KEY (record_id);


--
-- Name: weave_agent_runtime_cells weave_agent_runtime_cells_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_agent_runtime_cells
    ADD CONSTRAINT weave_agent_runtime_cells_pkey PRIMARY KEY (record_id);


--
-- Name: weave_agent_runtime_commands weave_agent_runtime_commands_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_agent_runtime_commands
    ADD CONSTRAINT weave_agent_runtime_commands_pkey PRIMARY KEY (idempotency_key, organization_ref, person_ref);


--
-- Name: weave_agent_runtime_entitlements weave_agent_runtime_entitlements_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_agent_runtime_entitlements
    ADD CONSTRAINT weave_agent_runtime_entitlements_pkey PRIMARY KEY (record_id);


--
-- Name: weave_agent_runtime_cells weave_agent_runtime_person_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_agent_runtime_cells
    ADD CONSTRAINT weave_agent_runtime_person_unique UNIQUE (organization_ref, person_ref);


--
-- Name: weave_agent_runtime_profile_signatures weave_agent_runtime_profile_signatures_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_agent_runtime_profile_signatures
    ADD CONSTRAINT weave_agent_runtime_profile_signatures_pkey PRIMARY KEY (key_id, profile_hash);


--
-- Name: weave_agent_runtime_profiles weave_agent_runtime_profiles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_agent_runtime_profiles
    ADD CONSTRAINT weave_agent_runtime_profiles_pkey PRIMARY KEY (profile_hash);


--
-- Name: weave_agent_runtime_revocations weave_agent_runtime_revocations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_agent_runtime_revocations
    ADD CONSTRAINT weave_agent_runtime_revocations_pkey PRIMARY KEY (record_id);


--
-- Name: weave_agent_runtime_state_deletions weave_agent_runtime_state_deletions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_agent_runtime_state_deletions
    ADD CONSTRAINT weave_agent_runtime_state_deletions_pkey PRIMARY KEY (idempotency_key, organization_ref, person_ref);


--
-- Name: weave_agent_runtime_state_generations weave_agent_runtime_state_generations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_agent_runtime_state_generations
    ADD CONSTRAINT weave_agent_runtime_state_generations_pkey PRIMARY KEY (generation_ref);


--
-- Name: weave_agent_runtime_state_heads weave_agent_runtime_state_heads_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_agent_runtime_state_heads
    ADD CONSTRAINT weave_agent_runtime_state_heads_pkey PRIMARY KEY (runtime_state_store_ref);


--
-- Name: weave_agent_runtime_cells weave_agent_runtime_workload_client_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_agent_runtime_cells
    ADD CONSTRAINT weave_agent_runtime_workload_client_unique UNIQUE (workload_issuer, workload_client_id);


--
-- Name: weave_agent_runtime_cells weave_agent_runtime_workload_subject_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_agent_runtime_cells
    ADD CONSTRAINT weave_agent_runtime_workload_subject_unique UNIQUE (workload_issuer, workload_subject);


--
-- Name: weave_audit_events weave_audit_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_audit_events
    ADD CONSTRAINT weave_audit_events_pkey PRIMARY KEY (sequence_id);


--
-- Name: weave_calendar_changes weave_calendar_changes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_calendar_changes
    ADD CONSTRAINT weave_calendar_changes_pkey PRIMARY KEY (calendar_id, change_sequence, scope_key);


--
-- Name: weave_calendar_collections weave_calendar_collections_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_calendar_collections
    ADD CONSTRAINT weave_calendar_collections_pkey PRIMARY KEY (calendar_id, scope_key);


--
-- Name: weave_calendar_events weave_calendar_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_calendar_events
    ADD CONSTRAINT weave_calendar_events_pkey PRIMARY KEY (calendar_id, event_id, scope_key);


--
-- Name: weave_chat_appservice_transactions weave_chat_appservice_transactions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_chat_appservice_transactions
    ADD CONSTRAINT weave_chat_appservice_transactions_pkey PRIMARY KEY (provider_key, homeserver_transaction_id);


--
-- Name: weave_chat_bridge_ledger weave_chat_bridge_ledger_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_chat_bridge_ledger
    ADD CONSTRAINT weave_chat_bridge_ledger_pkey PRIMARY KEY (tenant_id, ledger_id);


--
-- Name: weave_chat_changes weave_chat_changes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_chat_changes
    ADD CONSTRAINT weave_chat_changes_pkey PRIMARY KEY (sequence_value);


--
-- Name: weave_chat_conversations weave_chat_conversations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_chat_conversations
    ADD CONSTRAINT weave_chat_conversations_pkey PRIMARY KEY (tenant_id, conversation_id);


--
-- Name: weave_chat_events weave_chat_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_chat_events
    ADD CONSTRAINT weave_chat_events_pkey PRIMARY KEY (tenant_id, conversation_id, event_id);


--
-- Name: weave_chat_memberships weave_chat_memberships_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_chat_memberships
    ADD CONSTRAINT weave_chat_memberships_pkey PRIMARY KEY (tenant_id, conversation_id, identity_issuer, actor_ref);


--
-- Name: weave_chat_operations weave_chat_operations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_chat_operations
    ADD CONSTRAINT weave_chat_operations_pkey PRIMARY KEY (tenant_id, operation_id);


--
-- Name: weave_chat_outbox weave_chat_outbox_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_chat_outbox
    ADD CONSTRAINT weave_chat_outbox_pkey PRIMARY KEY (tenant_id, operation_id);


--
-- Name: weave_chat_provider_mappings weave_chat_provider_mappings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_chat_provider_mappings
    ADD CONSTRAINT weave_chat_provider_mappings_pkey PRIMARY KEY (tenant_id, provider_key, object_type, canonical_object_id);


--
-- Name: weave_chat_quarantine weave_chat_quarantine_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_chat_quarantine
    ADD CONSTRAINT weave_chat_quarantine_pkey PRIMARY KEY (tenant_id, quarantine_id);


--
-- Name: weave_chat_read_receipts weave_chat_read_receipts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_chat_read_receipts
    ADD CONSTRAINT weave_chat_read_receipts_pkey PRIMARY KEY (tenant_id, conversation_id, identity_issuer, actor_ref);


--
-- Name: weave_device_credentials weave_device_credentials_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_device_credentials
    ADD CONSTRAINT weave_device_credentials_pkey PRIMARY KEY (credential_id);


--
-- Name: weave_file_locks weave_file_locks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_file_locks
    ADD CONSTRAINT weave_file_locks_pkey PRIMARY KEY (canonical_path, organization_ref, space_ref);


--
-- Name: weave_files_objects weave_files_objects_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_files_objects
    ADD CONSTRAINT weave_files_objects_pkey PRIMARY KEY (file_id, organization_ref, space_ref);


--
-- Name: weave_identity_admin_operations weave_identity_admin_operations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_identity_admin_operations
    ADD CONSTRAINT weave_identity_admin_operations_pkey PRIMARY KEY (idempotency_key, organization_id);


--
-- Name: weave_identity_provisioning_intents weave_identity_provisioning_intents_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_identity_provisioning_intents
    ADD CONSTRAINT weave_identity_provisioning_intents_pkey PRIMARY KEY (intent_id);


--
-- Name: weave_matrix_e2ee_snapshots weave_matrix_e2ee_snapshots_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_matrix_e2ee_snapshots
    ADD CONSTRAINT weave_matrix_e2ee_snapshots_pkey PRIMARY KEY (tenant_id);


--
-- Name: weave_matrix_identity_projection weave_matrix_identity_projection_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_matrix_identity_projection
    ADD CONSTRAINT weave_matrix_identity_projection_pkey PRIMARY KEY (identity_issuer, matrix_user_id, tenant_id);


--
-- Name: weave_matrix_revoked_sessions weave_matrix_revoked_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_matrix_revoked_sessions
    ADD CONSTRAINT weave_matrix_revoked_sessions_pkey PRIMARY KEY (session_hash);


--
-- Name: weave_migration_run_evidence weave_migration_run_evidence_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_migration_run_evidence
    ADD CONSTRAINT weave_migration_run_evidence_pkey PRIMARY KEY (domain_key, run_id);


--
-- Name: weave_operation_intents weave_operation_intents_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_operation_intents
    ADD CONSTRAINT weave_operation_intents_pkey PRIMARY KEY (operation_ref);


--
-- Name: weave_operation_outbox weave_operation_outbox_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_operation_outbox
    ADD CONSTRAINT weave_operation_outbox_pkey PRIMARY KEY (sequence_id);


--
-- Name: weave_organization_bootstrap weave_organization_bootstrap_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_organization_bootstrap
    ADD CONSTRAINT weave_organization_bootstrap_pkey PRIMARY KEY (organization_id);


--
-- Name: weave_person_bindings weave_person_bindings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_person_bindings
    ADD CONSTRAINT weave_person_bindings_pkey PRIMARY KEY (issuer, organization_ref, subject);


--
-- Name: weave_portability_fidelity_items weave_portability_fidelity_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_portability_fidelity_items
    ADD CONSTRAINT weave_portability_fidelity_items_pkey PRIMARY KEY (canonical_object_id, plan_ref);


--
-- Name: weave_portability_plans weave_portability_plans_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_portability_plans
    ADD CONSTRAINT weave_portability_plans_pkey PRIMARY KEY (plan_ref);


--
-- Name: weave_product_profile_overrides weave_product_profile_overrides_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_product_profile_overrides
    ADD CONSTRAINT weave_product_profile_overrides_pkey PRIMARY KEY (primary_identity_key);


--
-- Name: weave_provider_bindings weave_provider_bindings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_provider_bindings
    ADD CONSTRAINT weave_provider_bindings_pkey PRIMARY KEY (binding_revision, domain_key, organization_ref);


--
-- Name: weave_provider_object_mappings weave_provider_object_mappings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_provider_object_mappings
    ADD CONSTRAINT weave_provider_object_mappings_pkey PRIMARY KEY (binding_revision, canonical_object_id, domain_key, organization_ref);


--
-- Name: weave_provider_selection_notes weave_provider_selection_notes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_provider_selection_notes
    ADD CONSTRAINT weave_provider_selection_notes_pkey PRIMARY KEY (category, note_order);


--
-- Name: weave_provider_selections weave_provider_selections_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_provider_selections
    ADD CONSTRAINT weave_provider_selections_pkey PRIMARY KEY (category);


--
-- Name: weave_schema_authority weave_schema_authority_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_schema_authority
    ADD CONSTRAINT weave_schema_authority_pkey PRIMARY KEY (epoch);


--
-- Name: weave_space_memberships weave_space_memberships_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_space_memberships
    ADD CONSTRAINT weave_space_memberships_pkey PRIMARY KEY (organization_ref, person_ref, space_ref);


--
-- Name: weave_spaces weave_spaces_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_spaces
    ADD CONSTRAINT weave_spaces_pkey PRIMARY KEY (organization_ref, space_ref);


--
-- Name: weave_wake_envelopes weave_wake_envelopes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_wake_envelopes
    ADD CONSTRAINT weave_wake_envelopes_pkey PRIMARY KEY (cell_ref, organization_ref, wake_ref);


--
-- Name: weave_workspace_revisions weave_workspace_revisions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_workspace_revisions
    ADD CONSTRAINT weave_workspace_revisions_pkey PRIMARY KEY (organization_ref, person_ref, revision);


--
-- Name: idx_weave_device_credentials_principal; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_weave_device_credentials_principal ON weave_device_credentials USING btree (domain, principal_ref, issued_at_utc);


--
-- Name: weave_agent_runtime_reconcile; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX weave_agent_runtime_reconcile ON weave_agent_runtime_cells USING btree (desired_state, observed_state, lease_expires_at);


--
-- Name: weave_identity_provisioning_pending_email; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX weave_identity_provisioning_pending_email ON weave_identity_provisioning_intents USING btree (tenant_id, organization_id, invited_email_sha256, status);


--
-- Name: weave_provider_selection_notes fki0v2vb9srqtvrxthxgbl5cxr0; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY weave_provider_selection_notes
    ADD CONSTRAINT fki0v2vb9srqtvrxthxgbl5cxr0 FOREIGN KEY (category) REFERENCES weave_provider_selections(category);


--
-- PostgreSQL database dump complete
--


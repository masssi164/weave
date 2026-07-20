create table weave_agent_runtime_cells (
    record_id uuid primary key,
    organization_ref varchar(255) not null,
    person_ref varchar(255) not null,
    member_issuer varchar(500) not null,
    member_subject varchar(255) not null,
    cell_ref varchar(255) not null unique,
    workload_issuer varchar(500) not null,
    workload_subject varchar(255) not null,
    workload_client_id varchar(255) not null,
    workload_authentication_method varchar(64) not null,
    workload_credential_ref varchar(1000) not null,
    entitlement_state varchar(32) not null,
    entitlement_revision varchar(255) not null,
    desired_state varchar(32) not null,
    observed_state varchar(32) not null,
    runtime_profile_id varchar(255),
    runtime_profile_hash varchar(71),
    workspace_revision varchar(255) not null,
    workspace_manifest_ref varchar(1000) not null,
    runtime_state_store_ref varchar(1000) not null,
    fencing_epoch bigint not null default 0 check (fencing_epoch >= 0),
    lease_id uuid,
    lease_expires_at timestamp with time zone,
    version bigint not null default 0 check (version >= 0),
    audit_ref varchar(255) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint weave_agent_runtime_person_unique unique (organization_ref, person_ref),
    constraint weave_agent_runtime_workload_subject_unique unique (workload_issuer, workload_subject),
    constraint weave_agent_runtime_workload_client_unique unique (workload_issuer, workload_client_id),
    constraint weave_agent_runtime_profile_pair check (
        (runtime_profile_id is null and runtime_profile_hash is null)
        or (runtime_profile_id is not null and runtime_profile_hash is not null)
    ),
    constraint weave_agent_runtime_lease_pair check (
        (lease_id is null and lease_expires_at is null)
        or (lease_id is not null and lease_expires_at is not null)
    )
);

create index weave_agent_runtime_reconcile
    on weave_agent_runtime_cells (desired_state, observed_state, lease_expires_at);

create table weave_agent_runtime_commands (
    organization_ref varchar(255) not null,
    person_ref varchar(255) not null,
    idempotency_key varchar(128) not null,
    command varchar(64) not null,
    status varchar(32) not null,
    cell_ref varchar(255) not null,
    runtime_version bigint,
    audit_ref varchar(255) not null,
    failure_code varchar(100),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    primary key (organization_ref, person_ref, idempotency_key),
    constraint weave_agent_runtime_command_key_length check (char_length(idempotency_key) between 16 and 128)
);

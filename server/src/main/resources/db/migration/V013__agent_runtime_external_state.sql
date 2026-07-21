create table weave_agent_runtime_state_heads (
    runtime_state_store_ref varchar(1000) primary key,
    organization_ref varchar(255) not null,
    person_ref varchar(255) not null,
    cell_ref varchar(255) not null unique,
    current_generation bigint not null default 0 check (current_generation >= 0),
    current_generation_ref varchar(81),
    version bigint not null default 0 check (version >= 0),
    audit_ref varchar(255) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint weave_agent_runtime_state_head_generation_pair check (
        (current_generation = 0 and current_generation_ref is null)
        or (current_generation > 0 and current_generation_ref is not null)
    )
);

create table weave_agent_runtime_state_generations (
    generation_ref varchar(81) primary key,
    runtime_state_store_ref varchar(1000) not null,
    generation bigint not null check (generation > 0),
    previous_generation bigint not null check (previous_generation >= 0),
    runtime_profile_hash varchar(71) not null,
    idempotency_key varchar(128) not null,
    encryption_algorithm varchar(64) not null,
    wrapping_key_ref varchar(128) not null,
    wrapped_data_key bytea not null,
    nonce bytea not null,
    plaintext_bytes bigint not null check (plaintext_bytes >= 0),
    ciphertext_bytes bigint not null check (ciphertext_bytes >= 16),
    chunk_count integer not null check (chunk_count > 0),
    audit_ref varchar(255) not null,
    committed_at timestamp with time zone not null,
    constraint weave_agent_runtime_state_generation_store_fk foreign key (runtime_state_store_ref)
        references weave_agent_runtime_state_heads (runtime_state_store_ref),
    constraint weave_agent_runtime_state_generation_number_unique unique (
        runtime_state_store_ref, generation
    ),
    constraint weave_agent_runtime_state_generation_idempotency_unique unique (
        runtime_state_store_ref, idempotency_key
    ),
    constraint weave_agent_runtime_state_generation_hash check (
        char_length(runtime_profile_hash) = 71 and runtime_profile_hash like 'sha256:%'
    ),
    constraint weave_agent_runtime_state_generation_key_length check (
        char_length(idempotency_key) between 16 and 128
    )
);

alter table weave_agent_runtime_state_heads
    add constraint weave_agent_runtime_state_head_current_fk foreign key (current_generation_ref)
        references weave_agent_runtime_state_generations (generation_ref);

create table weave_agent_runtime_state_chunks (
    generation_ref varchar(81) not null,
    chunk_ordinal integer not null check (chunk_ordinal >= 0),
    ciphertext bytea not null,
    primary key (generation_ref, chunk_ordinal),
    constraint weave_agent_runtime_state_chunk_generation_fk foreign key (generation_ref)
        references weave_agent_runtime_state_generations (generation_ref)
);

create table weave_agent_runtime_state_deletions (
    organization_ref varchar(255) not null,
    person_ref varchar(255) not null,
    cell_ref varchar(255) not null,
    runtime_state_store_ref varchar(1000) not null,
    idempotency_key varchar(128) not null,
    deleted_generation_count bigint not null check (deleted_generation_count >= 0),
    audit_ref varchar(255) not null,
    completed_at timestamp with time zone not null,
    primary key (organization_ref, person_ref, idempotency_key),
    constraint weave_agent_runtime_state_deletion_key_length check (
        char_length(idempotency_key) between 16 and 128
    )
);

create index weave_agent_runtime_state_generation_committed
    on weave_agent_runtime_state_generations (runtime_state_store_ref, committed_at desc);

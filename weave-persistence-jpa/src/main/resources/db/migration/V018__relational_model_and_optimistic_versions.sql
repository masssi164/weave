alter table weave_provider_selections
    add column version bigint not null default 0 check (version >= 0);
alter table weave_product_profile_overrides
    add column version bigint not null default 0 check (version >= 0);
alter table weave_device_credentials
    add column version bigint not null default 0 check (version >= 0);
alter table weave_identity_provisioning_intents
    add column version bigint not null default 0 check (version >= 0);
alter table weave_migration_run_evidence
    add column version bigint not null default 0 check (version >= 0);
alter table weave_matrix_e2ee_snapshots
    add column version bigint not null default 0 check (version >= 0);
alter table weave_chat_conversations
    add column version bigint not null default 0 check (version >= 0);
alter table weave_chat_memberships
    add column version bigint not null default 0 check (version >= 0);
alter table weave_chat_events
    add column version bigint not null default 0 check (version >= 0);
alter table weave_chat_operations
    add column version bigint not null default 0 check (version >= 0);
alter table weave_chat_outbox
    add column version bigint not null default 0 check (version >= 0);
alter table weave_chat_provider_mappings
    add column version bigint not null default 0 check (version >= 0);
alter table weave_chat_bridge_ledger
    add column version bigint not null default 0 check (version >= 0);
alter table weave_chat_appservice_transactions
    add column version bigint not null default 0 check (version >= 0);
alter table weave_chat_quarantine
    add column version bigint not null default 0 check (version >= 0);
alter table weave_chat_read_receipts
    add column version bigint not null default 0 check (version >= 0);
alter table weave_matrix_identity_projection
    add column version bigint not null default 0 check (version >= 0);
alter table weave_agent_runtime_commands
    add column version bigint not null default 0 check (version >= 0);
alter table weave_agent_runtime_profiles
    add column version bigint not null default 0 check (version >= 0);
alter table weave_agent_runtime_entitlements
    add column version bigint not null default 0 check (version >= 0);
alter table weave_provider_bindings
    add column version bigint not null default 0 check (version >= 0);
alter table weave_provider_object_mappings
    add column version bigint not null default 0 check (version >= 0);
alter table weave_files_objects
    add column version bigint not null default 0 check (version >= 0);
alter table weave_file_locks
    add column version bigint not null default 0 check (version >= 0);
alter table weave_operation_intents
    add column version bigint not null default 0 check (version >= 0);
alter table weave_operation_outbox
    add column version bigint not null default 0 check (version >= 0);
alter table weave_organization_bootstrap
    add column version bigint not null default 0 check (version >= 0);

create table weave_person_bindings (
    organization_ref varchar(255) not null,
    issuer varchar(500) not null,
    subject varchar(255) not null,
    person_ref varchar(255) not null,
    created_at_utc timestamp with time zone not null,
    version bigint not null default 0 check (version >= 0),
    primary key (organization_ref, issuer, subject),
    constraint uq_weave_person_binding_person
        unique (organization_ref, person_ref)
);

create index weave_person_binding_lookup_idx
    on weave_person_bindings (organization_ref, person_ref);

create table weave_spaces (
    organization_ref varchar(255) not null,
    space_ref varchar(255) not null,
    lifecycle_state varchar(32) not null,
    version bigint not null default 0 check (version >= 0),
    created_at_utc timestamp with time zone not null,
    updated_at_utc timestamp with time zone not null,
    primary key (organization_ref, space_ref),
    constraint chk_weave_space_lifecycle
        check (lifecycle_state in ('ACTIVE', 'SUSPENDED', 'DELETED'))
);

create table weave_space_memberships (
    organization_ref varchar(255) not null,
    space_ref varchar(255) not null,
    person_ref varchar(255) not null,
    permission_set varchar(255) not null,
    lifecycle_state varchar(32) not null,
    version bigint not null default 0 check (version >= 0),
    created_at_utc timestamp with time zone not null,
    updated_at_utc timestamp with time zone not null,
    primary key (organization_ref, space_ref, person_ref),
    constraint fk_weave_space_membership_space
        foreign key (organization_ref, space_ref)
        references weave_spaces (organization_ref, space_ref),
    constraint fk_weave_space_membership_person_binding
        foreign key (organization_ref, person_ref)
        references weave_person_bindings (organization_ref, person_ref),
    constraint chk_weave_space_membership_lifecycle
        check (lifecycle_state in ('ACTIVE', 'SUSPENDED', 'REVOKED'))
);

create index weave_space_membership_person_idx
    on weave_space_memberships (organization_ref, person_ref, lifecycle_state);

create table weave_portability_plans (
    plan_ref varchar(255) primary key,
    organization_ref varchar(255) not null,
    domain_key varchar(80) not null,
    source_binding_revision bigint not null check (source_binding_revision > 0),
    target_binding_revision bigint not null check (target_binding_revision > 0),
    lifecycle_state varchar(32) not null,
    version bigint not null default 0 check (version >= 0),
    created_at_utc timestamp with time zone not null,
    updated_at_utc timestamp with time zone not null,
    constraint chk_weave_portability_distinct_bindings
        check (source_binding_revision <> target_binding_revision),
    constraint chk_weave_portability_lifecycle
        check (lifecycle_state in (
            'DRAFT', 'DISCOVERING', 'PREFLIGHT', 'DRY_RUN', 'REVIEW_REQUIRED',
            'APPROVED', 'PREPARING', 'COPYING', 'DELTA_SYNC', 'CUTOVER',
            'VERIFYING', 'COMPLETED', 'FAILED', 'ROLLBACK_READY', 'ROLLED_BACK'
        )),
    constraint fk_weave_portability_source_binding
        foreign key (organization_ref, domain_key, source_binding_revision)
        references weave_provider_bindings (organization_ref, domain_key, binding_revision),
    constraint fk_weave_portability_target_binding
        foreign key (organization_ref, domain_key, target_binding_revision)
        references weave_provider_bindings (organization_ref, domain_key, binding_revision)
);

create index weave_portability_reconciliation_idx
    on weave_portability_plans (lifecycle_state, updated_at_utc, plan_ref);

create table weave_portability_fidelity_items (
    plan_ref varchar(255) not null,
    canonical_object_id varchar(255) not null,
    fidelity_class varchar(2) not null,
    disposition varchar(32) not null,
    recorded_at_utc timestamp with time zone not null,
    primary key (plan_ref, canonical_object_id),
    constraint fk_weave_portability_fidelity_plan
        foreign key (plan_ref)
        references weave_portability_plans (plan_ref),
    constraint chk_weave_portability_fidelity_class
        check (fidelity_class in ('F0', 'F1', 'F2', 'F3', 'F4')),
    constraint chk_weave_portability_disposition
        check (disposition in ('PRESERVE', 'TRANSFORM', 'ARCHIVE', 'BLOCK'))
);

create table weave_workspace_revisions (
    organization_ref varchar(255) not null,
    person_ref varchar(255) not null,
    revision bigint not null check (revision > 0),
    manifest_ref varchar(1000) not null,
    manifest_digest varchar(71) not null,
    signature_key_ref varchar(255) not null,
    signature varchar(2048) not null,
    lifecycle_state varchar(32) not null,
    active_slot boolean,
    version bigint not null default 0 check (version >= 0),
    created_at_utc timestamp with time zone not null,
    activated_at_utc timestamp with time zone,
    primary key (organization_ref, person_ref, revision),
    constraint chk_weave_workspace_manifest_digest
        check (char_length(manifest_digest) = 71 and manifest_digest like 'sha256:%'),
    constraint chk_weave_workspace_revision_lifecycle
        check (lifecycle_state in ('DRAFT', 'ACTIVE', 'SUPERSEDED', 'REJECTED')),
    constraint chk_weave_workspace_revision_active_slot
        check (
            (lifecycle_state = 'ACTIVE' and active_slot = true and activated_at_utc is not null)
            or (lifecycle_state <> 'ACTIVE' and active_slot is null)
        ),
    constraint uq_weave_workspace_revision_active
        unique (organization_ref, person_ref, active_slot)
);

create index weave_workspace_revision_history_idx
    on weave_workspace_revisions (organization_ref, person_ref, revision);

create table weave_wake_envelopes (
    organization_ref varchar(255) not null,
    cell_ref varchar(255) not null,
    wake_ref varchar(255) not null,
    event_digest varchar(71) not null,
    outbox_ref varchar(255) not null unique,
    delivery_state varchar(32) not null,
    attempt_count integer not null default 0 check (attempt_count >= 0),
    next_attempt_at_utc timestamp with time zone,
    version bigint not null default 0 check (version >= 0),
    created_at_utc timestamp with time zone not null,
    delivered_at_utc timestamp with time zone,
    primary key (organization_ref, cell_ref, wake_ref),
    constraint fk_weave_wake_cell
        foreign key (cell_ref)
        references weave_agent_runtime_cells (cell_ref),
    constraint uq_weave_wake_event
        unique (organization_ref, cell_ref, event_digest),
    constraint chk_weave_wake_event_digest
        check (char_length(event_digest) = 71 and event_digest like 'sha256:%'),
    constraint chk_weave_wake_delivery
        check (delivery_state in ('PENDING', 'DELIVERING', 'DELIVERED', 'FAILED')),
    constraint chk_weave_wake_terminal
        check (delivery_state <> 'DELIVERED' or delivered_at_utc is not null)
);

create index weave_wake_delivery_idx
    on weave_wake_envelopes (delivery_state, next_attempt_at_utc, created_at_utc);

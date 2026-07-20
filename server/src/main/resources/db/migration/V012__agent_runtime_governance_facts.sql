create table weave_agent_runtime_entitlements (
    record_id uuid primary key,
    entitlement_ref varchar(255) not null unique,
    entitlement_revision varchar(71) not null unique,
    organization_ref varchar(255) not null,
    person_ref varchar(255) not null,
    member_issuer varchar(500) not null,
    member_subject varchar(255) not null,
    source_provider varchar(64) not null,
    source_group_ref varchar(71) not null,
    capability_revision varchar(71) not null,
    entitlement_state varchar(32) not null,
    effective_at timestamp with time zone not null,
    last_observed_at timestamp with time zone not null,
    expires_at timestamp with time zone not null,
    revocation_ref varchar(255),
    revoked_at timestamp with time zone,
    audit_ref varchar(255) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint weave_agent_runtime_entitlement_hashes check (
        char_length(entitlement_ref) = 76 and entitlement_ref like 'entitlement:%'
        and char_length(entitlement_revision) = 71 and entitlement_revision like 'sha256:%'
        and char_length(source_group_ref) = 71 and source_group_ref like 'sha256:%'
        and char_length(capability_revision) = 71 and capability_revision like 'sha256:%'
    ),
    constraint weave_agent_runtime_entitlement_times check (
        last_observed_at >= effective_at and expires_at > last_observed_at
    ),
    constraint weave_agent_runtime_entitlement_revocation_pair check (
        (entitlement_state = 'ENTITLED' and revocation_ref is null and revoked_at is null)
        or (entitlement_state = 'REVOKED' and revocation_ref is not null and revoked_at is not null)
    )
);

create index weave_agent_runtime_entitlement_current
    on weave_agent_runtime_entitlements (organization_ref, person_ref, last_observed_at desc);

create table weave_agent_runtime_audit_correlations (
    record_id uuid primary key,
    correlation_ref varchar(255) not null unique,
    organization_ref_hash varchar(71) not null,
    person_ref_hash varchar(71) not null,
    keycloak_ref_hash varchar(71),
    orchestrator_ref_hash varchar(71),
    openclaw_ref_hash varchar(71),
    matrix_ref_hash varchar(71),
    mcp_ref_hash varchar(71),
    domain_audit_ref_hash varchar(71),
    occurred_at timestamp with time zone not null,
    created_at timestamp with time zone not null,
    constraint weave_agent_runtime_correlation_link check (
        keycloak_ref_hash is not null or orchestrator_ref_hash is not null
        or openclaw_ref_hash is not null or matrix_ref_hash is not null
        or mcp_ref_hash is not null or domain_audit_ref_hash is not null
    ),
    constraint weave_agent_runtime_correlation_format check (
        char_length(correlation_ref) = 76 and correlation_ref like 'correlation:%'
        and char_length(organization_ref_hash) = 71 and organization_ref_hash like 'sha256:%'
        and char_length(person_ref_hash) = 71 and person_ref_hash like 'sha256:%'
        and (keycloak_ref_hash is null or (char_length(keycloak_ref_hash) = 71 and keycloak_ref_hash like 'sha256:%'))
        and (orchestrator_ref_hash is null or (char_length(orchestrator_ref_hash) = 71 and orchestrator_ref_hash like 'sha256:%'))
        and (openclaw_ref_hash is null or (char_length(openclaw_ref_hash) = 71 and openclaw_ref_hash like 'sha256:%'))
        and (matrix_ref_hash is null or (char_length(matrix_ref_hash) = 71 and matrix_ref_hash like 'sha256:%'))
        and (mcp_ref_hash is null or (char_length(mcp_ref_hash) = 71 and mcp_ref_hash like 'sha256:%'))
        and (domain_audit_ref_hash is null or (char_length(domain_audit_ref_hash) = 71 and domain_audit_ref_hash like 'sha256:%'))
    )
);

create table weave_agent_runtime_revocations (
    record_id uuid primary key,
    revocation_ref varchar(255) not null unique,
    organization_ref varchar(255) not null,
    person_ref varchar(255) not null,
    reason_code varchar(100) not null,
    actor_ref_hash varchar(71) not null,
    effective_at timestamp with time zone not null,
    entitlement_ref varchar(255) not null,
    entitlement_revision varchar(71) not null,
    cell_ref varchar(255) not null,
    profile_hash varchar(71),
    workload_ref_hash varchar(71) not null,
    audit_correlation_ref varchar(255) not null,
    created_at timestamp with time zone not null,
    constraint weave_agent_runtime_revocation_formats check (
        char_length(revocation_ref) = 75 and revocation_ref like 'revocation:%'
        and char_length(actor_ref_hash) = 71 and actor_ref_hash like 'sha256:%'
        and char_length(entitlement_revision) = 71 and entitlement_revision like 'sha256:%'
        and char_length(workload_ref_hash) = 71 and workload_ref_hash like 'sha256:%'
        and char_length(audit_correlation_ref) = 76 and audit_correlation_ref like 'correlation:%'
        and (profile_hash is null or (char_length(profile_hash) = 71 and profile_hash like 'sha256:%'))
    ),
    constraint weave_agent_runtime_revocation_entitlement_fk foreign key (entitlement_ref)
        references weave_agent_runtime_entitlements (entitlement_ref),
    constraint weave_agent_runtime_revocation_cell_fk foreign key (cell_ref)
        references weave_agent_runtime_cells (cell_ref),
    constraint weave_agent_runtime_revocation_correlation_fk foreign key (audit_correlation_ref)
        references weave_agent_runtime_audit_correlations (correlation_ref)
);

create index weave_agent_runtime_revocation_person
    on weave_agent_runtime_revocations (organization_ref, person_ref, effective_at desc);

alter table weave_agent_runtime_cells
    add constraint weave_agent_runtime_cell_entitlement_fk foreign key (entitlement_revision)
        references weave_agent_runtime_entitlements (entitlement_revision);

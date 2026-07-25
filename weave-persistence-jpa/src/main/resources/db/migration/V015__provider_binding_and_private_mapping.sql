create table weave_provider_bindings (
    organization_ref varchar(255) not null,
    domain_key varchar(80) not null,
    binding_revision bigint not null,
    adapter_key varchar(160) not null,
    configuration_ref varchar(255) not null,
    binding_state varchar(32) not null,
    active_slot boolean,
    activated_at_utc timestamp with time zone not null,
    primary key (organization_ref, domain_key, binding_revision),
    constraint chk_weave_provider_binding_revision check (binding_revision > 0),
    constraint chk_weave_provider_binding_state check (binding_state in ('ACTIVE', 'RETIRED', 'REVOKED')),
    constraint chk_weave_provider_binding_active_slot check (
        (binding_state = 'ACTIVE' and active_slot = true)
        or (binding_state <> 'ACTIVE' and active_slot is null)),
    constraint uq_weave_provider_binding_active unique (organization_ref, domain_key, active_slot)
);

create table weave_provider_object_mappings (
    organization_ref varchar(255) not null,
    domain_key varchar(80) not null,
    binding_revision bigint not null,
    canonical_object_id varchar(255) not null,
    provider_object_ref varchar(1024) not null,
    provenance varchar(255) not null,
    first_observed_at_utc timestamp with time zone not null,
    last_observed_at_utc timestamp with time zone not null,
    primary key (organization_ref, domain_key, binding_revision, canonical_object_id),
    constraint fk_weave_provider_mapping_binding foreign key
        (organization_ref, domain_key, binding_revision)
        references weave_provider_bindings (organization_ref, domain_key, binding_revision),
    constraint uq_weave_provider_mapping_private_ref unique
        (organization_ref, domain_key, binding_revision, provider_object_ref)
);

create index weave_provider_mapping_observed_idx
    on weave_provider_object_mappings (organization_ref, domain_key, binding_revision, last_observed_at_utc);

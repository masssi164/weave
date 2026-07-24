create table weave_organization_bootstrap (
    organization_id varchar(255) primary key,
    bootstrap_mode varchar(64) not null,
    actor_primary_identity_key varchar(512) not null,
    retained_admin_primary_identity_keys_json text not null,
    bootstrapped_at timestamp with time zone not null
);

create table weave_identity_realm_dry_run_evidence (
    dry_run_id varchar(255) primary key,
    audit_ref varchar(255) not null,
    provider_key varchar(128) not null,
    realm_id varchar(255) not null,
    report_json text not null,
    created_at timestamp with time zone not null
);

create index idx_identity_realm_dry_run_evidence_created
    on weave_identity_realm_dry_run_evidence (created_at);

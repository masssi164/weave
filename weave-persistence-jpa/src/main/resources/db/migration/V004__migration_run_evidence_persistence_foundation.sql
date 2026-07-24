create table weave_migration_run_evidence (
    run_id varchar(180) not null,
    domain_key varchar(120) not null,
    lifecycle varchar(80) not null,
    object_counts_json text not null,
    content_hashes_json text not null,
    audit_refs_json text not null,
    artifact_refs_json text not null,
    provider_diagnostics_json text not null,
    identity_mapping_complete boolean not null,
    audit_sink_available boolean not null,
    admin_approved boolean not null,
    recorded_at_utc timestamp with time zone not null,
    expires_at_utc timestamp with time zone,
    primary key (run_id, domain_key)
);

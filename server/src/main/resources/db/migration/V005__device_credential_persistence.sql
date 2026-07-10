create table weave_device_credentials (
    credential_id varchar(160) primary key,
    domain varchar(40) not null,
    tenant_id varchar(160) not null,
    principal_ref varchar(255) not null,
    subject_ref varchar(255) not null,
    username varchar(255) not null,
    client_type varchar(80) not null,
    label varchar(255) not null,
    capabilities_json text not null,
    secret_hash text not null,
    issued_at_utc timestamp with time zone not null,
    expires_at_utc timestamp with time zone not null,
    revoked_at_utc timestamp with time zone,
    constraint ck_weave_device_credential_domain check (domain in ('files', 'calendar'))
);

create index idx_weave_device_credentials_principal
    on weave_device_credentials (domain, principal_ref, issued_at_utc);

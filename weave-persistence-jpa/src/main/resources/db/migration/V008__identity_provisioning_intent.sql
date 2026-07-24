drop table if exists weave_member_invitations;

create table weave_identity_provisioning_intents (
    intent_id uuid primary key,
    tenant_id varchar(160) not null,
    organization_id varchar(160) not null,
    invited_email varchar(320) not null,
    invited_email_sha256 varchar(64) not null,
    requested_role varchar(32) not null,
    organization_groups varchar(8192) not null default '[]',
    provider_invitation_id varchar(200) unique,
    invited_by_issuer varchar(500) not null,
    invited_by_subject varchar(255) not null,
    audit_correlation varchar(255) not null,
    status varchar(32) not null,
    applied_subject varchar(255),
    failure_code varchar(100),
    expires_at timestamp with time zone not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index weave_identity_provisioning_pending_email
    on weave_identity_provisioning_intents (tenant_id, organization_id, invited_email_sha256, status);

create table weave_keycloak_event_receipts (
    event_id varchar(200) primary key,
    occurred_at timestamp with time zone not null,
    received_at timestamp with time zone not null
);

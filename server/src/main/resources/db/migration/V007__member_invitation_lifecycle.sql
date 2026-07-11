create table if not exists weave_member_invitations (
    invitation_id uuid primary key,
    tenant_id varchar(160) not null,
    organization_id varchar(160) not null,
    invited_email varchar(320) not null,
    display_name varchar(200),
    requested_role varchar(32) not null,
    workspace_ids varchar(8192) not null default '[]',
    status varchar(32) not null,
    provider_invitation_id varchar(200),
    invited_by_subject varchar(255) not null,
    expires_at timestamp with time zone not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index if not exists weave_member_invitations_email_status
    on weave_member_invitations (tenant_id, organization_id, invited_email, status);

create index if not exists weave_member_invitations_org_created
    on weave_member_invitations (tenant_id, organization_id, created_at desc);

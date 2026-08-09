-- Complete native-provider relational authority before the first accepted persistent-dogfood baseline.
-- This migration series may still be corrected while the closure PR is unaccepted; after the
-- first persistent dogfood schema is accepted, applied Flyway migrations are immutable.

create table if not exists weave_matrix_sync_heads (
    tenant_id varchar(255) primary key,
    revision bigint not null default 0,
    row_version bigint not null default 0,
    updated_at_utc timestamp with time zone not null default now(),
    constraint ck_weave_matrix_sync_head_revision check (revision >= 0)
);

create table if not exists weave_matrix_shared_users (
    tenant_id varchar(255) not null,
    user_id varchar(255) not null,
    device_id varchar(255) not null,
    shared_user_id varchar(255) not null,
    shared boolean not null,
    changed_revision bigint not null,
    primary key (tenant_id, user_id, device_id, shared_user_id),
    constraint ck_weave_matrix_shared_user_revision check (changed_revision >= 0)
);
create index if not exists ix_weave_matrix_shared_users_changes
    on weave_matrix_shared_users (tenant_id, user_id, device_id, changed_revision);

alter table weave_chat_idempotency
    add column if not exists provider_binding_revision bigint not null default 1;

alter table weave_chat_idempotency
    add constraint ck_weave_chat_idempotency_provider_binding_revision
    check (provider_binding_revision > 0);

create index if not exists ix_weave_chat_idempotency_provider_binding
    on weave_chat_idempotency (tenant_id, user_id, device_id, endpoint_identity, provider_binding_revision, transaction_id);

-- Per-device Matrix to-device ordering uses the explicit Matrix sync revision.
-- sequence_id remains only the stable row identity introduced by V2; revision_id is
-- the protocol ordering/high-water value and is assigned from weave_matrix_sync_heads.
alter table weave_matrix_to_device_messages
    add column if not exists revision_id bigint;
update weave_matrix_to_device_messages
    set revision_id = sequence_id
    where revision_id is null;
alter table weave_matrix_to_device_messages
    alter column revision_id set not null;
create index if not exists ix_weave_matrix_to_device_revision
    on weave_matrix_to_device_messages (tenant_id, target_user_id, target_device_id, revision_id);

-- Keep device sync progress separate from queue row identity.
alter table weave_matrix_device_sync_progress
    add column if not exists last_issued_revision bigint not null default 0;

-- The normalized V2/V3 tables are authoritative. The tenant-wide snapshot is
-- deliberately removed rather than retained as a compatibility path.
drop table if exists weave_matrix_e2ee_snapshots;
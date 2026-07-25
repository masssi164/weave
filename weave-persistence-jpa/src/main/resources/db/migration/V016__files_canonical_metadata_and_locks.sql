create table weave_files_objects (
    organization_ref varchar(255) not null,
    space_ref varchar(255) not null,
    file_id varchar(255) not null,
    canonical_path varchar(2048) not null,
    object_kind varchar(32) not null,
    byte_size bigint not null,
    media_type varchar(255),
    modified_at_utc timestamp with time zone,
    hidden boolean not null,
    version_token varchar(1024),
    content_digest varchar(71),
    provider_binding_revision bigint not null,
    lifecycle_state varchar(32) not null,
    observed_at_utc timestamp with time zone not null,
    primary key (organization_ref, space_ref, file_id),
    constraint uq_weave_files_canonical_path unique (organization_ref, space_ref, canonical_path),
    constraint chk_weave_files_object_kind check (object_kind in ('FILE', 'COLLECTION')),
    constraint chk_weave_files_byte_size check (byte_size >= 0),
    constraint chk_weave_files_binding_revision check (provider_binding_revision > 0),
    constraint chk_weave_files_lifecycle check (lifecycle_state in ('ACTIVE', 'TOMBSTONED'))
);

create index weave_files_binding_inventory_idx
    on weave_files_objects (organization_ref, provider_binding_revision, lifecycle_state, file_id);

create table weave_file_locks (
    organization_ref varchar(255) not null,
    space_ref varchar(255) not null,
    canonical_path varchar(2048) not null,
    token_digest varchar(71) not null,
    owner_ref varchar(255) not null,
    fence bigint not null,
    expires_at_utc timestamp with time zone not null,
    created_at_utc timestamp with time zone not null,
    released_at_utc timestamp with time zone,
    primary key (organization_ref, space_ref, canonical_path),
    constraint chk_weave_file_lock_fence check (fence > 0),
    constraint chk_weave_file_lock_expiry check (expires_at_utc > created_at_utc)
);

create index weave_file_lock_expiry_idx
    on weave_file_locks (expires_at_utc, released_at_utc);

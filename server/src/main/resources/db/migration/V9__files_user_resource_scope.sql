-- A member-visible Files identity must be explicitly attached to a Weave Space.
-- Provider-visible objects without one of these records stay outside the User API.
CREATE TABLE weave_files_user_resources (
    organization_ref varchar(255) NOT NULL,
    file_id varchar(255) NOT NULL,
    binding_revision bigint NOT NULL,
    space_ref varchar(255) NOT NULL,
    parent_file_id varchar(255) NOT NULL,
    canonical_path varchar(2048) NOT NULL,
    active_path_key varchar(2048),
    object_kind varchar(32) NOT NULL,
    owner_principal_ref varchar(255) NOT NULL,
    lifecycle_state varchar(32) NOT NULL,
    created_at_utc timestamp(6) with time zone NOT NULL,
    modified_at_utc timestamp(6) with time zone NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT weave_files_user_resources_pkey PRIMARY KEY (organization_ref, file_id),
    CONSTRAINT ck_weave_files_user_resources_kind
        CHECK (object_kind IN ('FILE', 'COLLECTION')),
    CONSTRAINT ck_weave_files_user_resources_lifecycle
        CHECK (lifecycle_state IN ('ACTIVE', 'TOMBSTONED')),
    CONSTRAINT ck_weave_files_user_resources_active_path
        CHECK ((lifecycle_state = 'ACTIVE' AND active_path_key = canonical_path)
            OR (lifecycle_state = 'TOMBSTONED' AND active_path_key IS NULL)),
    CONSTRAINT uq_weave_files_user_resources_active_path
        UNIQUE (organization_ref, binding_revision, active_path_key)
);

CREATE INDEX ix_weave_files_user_resources_parent
    ON weave_files_user_resources (organization_ref, parent_file_id, lifecycle_state);

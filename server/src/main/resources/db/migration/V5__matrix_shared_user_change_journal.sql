-- Preserve Matrix shared-user device-list transitions as an append-only logical stream.
-- The current-state table weave_matrix_shared_users remains the latest projection;
-- this journal is the replay/sync authority for changed/left transitions.

CREATE TABLE public.weave_matrix_shared_user_changes (
    tenant_id varchar(255) NOT NULL,
    user_id varchar(512) NOT NULL,
    device_id varchar(128) NOT NULL,
    shared_user_id varchar(512) NOT NULL,
    shared boolean NOT NULL,
    changed_revision bigint NOT NULL,
    PRIMARY KEY (tenant_id, user_id, device_id, changed_revision, shared_user_id),
    CHECK (changed_revision >= 0)
);

CREATE INDEX weave_matrix_shared_user_changes_lookup_idx
    ON public.weave_matrix_shared_user_changes (
        tenant_id,
        user_id,
        device_id,
        changed_revision,
        shared_user_id
    );

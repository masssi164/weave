-- Existing Files grants remain private; sharing is an explicit creation decision.
ALTER TABLE weave_files_user_resources
    ADD COLUMN visibility_scope varchar(32) NOT NULL DEFAULT 'PRIVATE';
ALTER TABLE weave_files_user_resources
    ADD CONSTRAINT ck_weave_files_user_resources_visibility
        CHECK (visibility_scope IN ('PRIVATE', 'SPACE'));

-- Add presentation metadata without changing Space or resource identity or grants.
ALTER TABLE weave_spaces ADD COLUMN display_name character varying(160);
UPDATE weave_spaces SET display_name = 'Organization workspace'
WHERE space_ref = 'workspace-default' AND display_name IS NULL;
WITH unnamed AS (
    SELECT organization_ref, space_ref,
           row_number() OVER (PARTITION BY organization_ref ORDER BY space_ref) AS ordinal
    FROM weave_spaces WHERE display_name IS NULL
)
UPDATE weave_spaces AS target
SET display_name = 'Space ' || unnamed.ordinal
FROM unnamed
WHERE target.organization_ref = unnamed.organization_ref AND target.space_ref = unnamed.space_ref;
ALTER TABLE weave_spaces ALTER COLUMN display_name SET NOT NULL;

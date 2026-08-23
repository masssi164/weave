-- RFC 6578 initial synchronization resolves the latest committed journal snapshot per
-- canonical FileId at one captured high-water. Keep that lookup bounded and provider-neutral.
CREATE INDEX idx_weave_files_changes_latest_file_revision
    ON weave_files_changes (
        organization_ref,
        space_ref,
        file_ref,
        revision DESC);

-- Incremental synchronization advances only through complete mutation ranges. This index
-- supports ordered range accounting without introducing a second change authority.
CREATE INDEX idx_weave_files_changes_mutation_range
    ON weave_files_changes (
        organization_ref,
        space_ref,
        range_start,
        range_end,
        revision);

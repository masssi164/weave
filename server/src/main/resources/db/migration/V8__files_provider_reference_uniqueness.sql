-- A Files object on one active provider binding has exactly one Weave identity.
-- Existing duplicate rows must be investigated rather than silently rewritten.
CREATE UNIQUE INDEX uq_weave_files_provider_object_ref
    ON weave_provider_object_mappings (
        organization_ref, domain_key, binding_revision, provider_object_ref)
    WHERE domain_key = 'files';

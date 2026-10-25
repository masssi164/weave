-- A binding has one live routing slot per organization and module. Retired and
-- revoked revisions retain their history without occupying that slot.
ALTER TABLE weave_provider_bindings
    ADD CONSTRAINT ck_weave_provider_bindings_active_slot
    CHECK (
        (binding_state = 'ACTIVE' AND active_slot IS TRUE)
        OR (binding_state IN ('RETIRED', 'REVOKED') AND active_slot IS NULL)
    );

ALTER TABLE weave_provider_bindings
    ADD CONSTRAINT uq_weave_provider_bindings_active_slot
    UNIQUE (organization_ref, domain_key, active_slot);

CREATE TABLE weave_runner_capability_catalogs (
    organization_ref VARCHAR(256) PRIMARY KEY,
    catalog_revision BIGINT NOT NULL,
    updated_at_utc TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT ck_weave_runner_capability_catalog_revision
        CHECK (catalog_revision >= 0)
);

CREATE TABLE weave_runner_capability_definitions (
    capability_definition_id UUID PRIMARY KEY,
    organization_ref VARCHAR(256) NOT NULL,
    capability_id VARCHAR(128) NOT NULL,
    capability_version VARCHAR(96) NOT NULL,
    contract_digest VARCHAR(71) NOT NULL,
    title VARCHAR(160) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    capability_effect VARCHAR(32) NOT NULL,
    input_schema_json TEXT NOT NULL,
    input_schema_digest VARCHAR(71) NOT NULL,
    output_schema_json TEXT NOT NULL,
    output_schema_digest VARCHAR(71) NOT NULL,
    timeout_seconds INTEGER NOT NULL,
    maximum_output_bytes BIGINT NOT NULL,
    introduced_revision BIGINT NOT NULL,
    created_at_utc TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT fk_weave_runner_capability_definition_catalog
        FOREIGN KEY (organization_ref)
        REFERENCES weave_runner_capability_catalogs (organization_ref),
    CONSTRAINT uk_weave_runner_capability_coordinate
        UNIQUE (organization_ref, capability_id, capability_version),
    CONSTRAINT ck_weave_runner_capability_definition_id
        CHECK (capability_id ~ '^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*$'),
    CONSTRAINT ck_weave_runner_capability_definition_version
        CHECK (capability_version ~ '^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?$'),
    CONSTRAINT ck_weave_runner_capability_definition_digests
        CHECK (
            contract_digest ~ '^sha256:[a-f0-9]{64}$'
            AND input_schema_digest ~ '^sha256:[a-f0-9]{64}$'
            AND output_schema_digest ~ '^sha256:[a-f0-9]{64}$'
        ),
    CONSTRAINT ck_weave_runner_capability_definition_effect
        CHECK (capability_effect IN ('READ_ONLY', 'IDEMPOTENT_WRITE', 'NON_IDEMPOTENT_WRITE')),
    CONSTRAINT ck_weave_runner_capability_definition_limits
        CHECK (
            timeout_seconds BETWEEN 1 AND 3600
            AND maximum_output_bytes BETWEEN 1024 AND 16777216
            AND introduced_revision > 0
        )
);

CREATE INDEX ix_weave_runner_capability_catalog
    ON weave_runner_capability_definitions (
        organization_ref,
        introduced_revision,
        capability_id,
        capability_version
    );

CREATE TABLE weave_runner_capability_artifact_types (
    capability_definition_id UUID NOT NULL,
    artifact_type VARCHAR(160) NOT NULL,
    PRIMARY KEY (capability_definition_id, artifact_type),
    CONSTRAINT fk_weave_runner_capability_artifact_definition
        FOREIGN KEY (capability_definition_id)
        REFERENCES weave_runner_capability_definitions (capability_definition_id)
);

CREATE TABLE weave_runner_capability_offerings (
    offering_id UUID PRIMARY KEY,
    organization_ref VARCHAR(256) NOT NULL,
    runner_id VARCHAR(135) NOT NULL,
    capability_definition_id UUID NOT NULL,
    capability_id VARCHAR(128) NOT NULL,
    capability_version VARCHAR(96) NOT NULL,
    contract_digest VARCHAR(71) NOT NULL,
    public_bundle_digest VARCHAR(71) NOT NULL,
    bundle_id VARCHAR(128) NOT NULL,
    bundle_version VARCHAR(96) NOT NULL,
    runner_state VARCHAR(16) NOT NULL,
    capacity INTEGER NOT NULL,
    available_slots INTEGER NOT NULL,
    observed_at_utc TIMESTAMPTZ NOT NULL,
    active BOOLEAN NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT fk_weave_runner_capability_offering_catalog
        FOREIGN KEY (organization_ref)
        REFERENCES weave_runner_capability_catalogs (organization_ref),
    CONSTRAINT fk_weave_runner_capability_offering_definition
        FOREIGN KEY (capability_definition_id)
        REFERENCES weave_runner_capability_definitions (capability_definition_id),
    CONSTRAINT uk_weave_runner_capability_offering
        UNIQUE (organization_ref, runner_id, capability_definition_id),
    CONSTRAINT ck_weave_runner_capability_offering_identity
        CHECK (
            runner_id ~ '^runner_[A-Za-z0-9_-]{8,128}$'
            AND capability_id ~ '^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*$'
            AND bundle_id ~ '^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*$'
        ),
    CONSTRAINT ck_weave_runner_capability_offering_versions
        CHECK (
            capability_version ~ '^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?$'
            AND bundle_version ~ '^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?$'
        ),
    CONSTRAINT ck_weave_runner_capability_offering_digests
        CHECK (
            contract_digest ~ '^sha256:[a-f0-9]{64}$'
            AND public_bundle_digest ~ '^sha256:[a-f0-9]{64}$'
        ),
    CONSTRAINT ck_weave_runner_capability_offering_state
        CHECK (runner_state IN ('ENROLLING', 'ONLINE', 'DEGRADED', 'OFFLINE', 'REVOKED')),
    CONSTRAINT ck_weave_runner_capability_offering_capacity
        CHECK (
            capacity BETWEEN 1 AND 1024
            AND available_slots BETWEEN 0 AND capacity
        )
);

CREATE INDEX ix_weave_runner_capability_available
    ON weave_runner_capability_offerings (
        organization_ref,
        capability_definition_id,
        active,
        runner_state,
        available_slots,
        observed_at_utc
    );

CREATE INDEX ix_weave_runner_capability_runner
    ON weave_runner_capability_offerings (
        organization_ref,
        runner_id,
        active
    );

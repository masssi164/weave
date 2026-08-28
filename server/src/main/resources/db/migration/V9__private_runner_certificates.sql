CREATE TABLE weave_runner_certificates (
    certificate_id UUID PRIMARY KEY,
    runner_id VARCHAR(135) NOT NULL,
    organization_ref VARCHAR(256) NOT NULL,
    certificate_fingerprint VARCHAR(71) NOT NULL,
    subject_dn VARCHAR(1024) NOT NULL,
    serial_number VARCHAR(128) NOT NULL,
    valid_from_utc TIMESTAMPTZ NOT NULL,
    expires_at_utc TIMESTAMPTZ NOT NULL,
    registered_at_utc TIMESTAMPTZ NOT NULL,
    certificate_status VARCHAR(16) NOT NULL,
    revoked_at_utc TIMESTAMPTZ,
    revocation_reason VARCHAR(64),
    version BIGINT NOT NULL,
    CONSTRAINT uk_weave_runner_certificate_fingerprint
        UNIQUE (certificate_fingerprint),
    CONSTRAINT ck_weave_runner_certificate_fingerprint
        CHECK (certificate_fingerprint ~ '^sha256:[a-f0-9]{64}$'),
    CONSTRAINT ck_weave_runner_certificate_serial
        CHECK (serial_number ~ '^[0-9a-f]{1,128}$'),
    CONSTRAINT ck_weave_runner_certificate_validity
        CHECK (expires_at_utc > valid_from_utc AND registered_at_utc <= expires_at_utc),
    CONSTRAINT ck_weave_runner_certificate_status
        CHECK (certificate_status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT ck_weave_runner_certificate_revocation
        CHECK (
            (certificate_status = 'ACTIVE'
                AND revoked_at_utc IS NULL
                AND revocation_reason IS NULL)
            OR
            (certificate_status = 'REVOKED'
                AND revoked_at_utc IS NOT NULL
                AND revocation_reason ~ '^[A-Z][A-Z0-9_]{1,63}$')
        )
);

CREATE INDEX ix_weave_runner_certificate_identity
    ON weave_runner_certificates (
        runner_id,
        organization_ref,
        certificate_status,
        expires_at_utc
    );

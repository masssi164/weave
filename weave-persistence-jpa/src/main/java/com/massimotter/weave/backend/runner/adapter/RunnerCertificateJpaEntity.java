package com.massimotter.weave.backend.runner.adapter;

import com.massimotter.weave.backend.runner.application.RunnerWorkloadIdentity;
import com.massimotter.weave.backend.runner.application.RunnerWorkloadIdentityDirectory;
import com.massimotter.weave.backend.runner.domain.RunnerControl.RunnerId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "weave_runner_certificates",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_weave_runner_certificate_fingerprint",
                columnNames = "certificate_fingerprint"),
        indexes = @Index(
                name = "ix_weave_runner_certificate_identity",
                columnList = "runner_id,organization_ref,certificate_status,expires_at_utc"))
class RunnerCertificateJpaEntity {

    @Id
    @Column(name = "certificate_id", nullable = false, updatable = false)
    private UUID certificateId;

    @Column(name = "runner_id", nullable = false, length = 135, updatable = false)
    private String runnerId;

    @Column(name = "organization_ref", nullable = false, length = 256, updatable = false)
    private String organizationRef;

    @Column(name = "certificate_fingerprint", nullable = false, length = 71, updatable = false)
    private String certificateFingerprint;

    @Column(name = "subject_dn", nullable = false, length = 1024, updatable = false)
    private String subjectDn;

    @Column(name = "serial_number", nullable = false, length = 128, updatable = false)
    private String serialNumber;

    @Column(name = "valid_from_utc", nullable = false, updatable = false)
    private OffsetDateTime validFrom;

    @Column(name = "expires_at_utc", nullable = false, updatable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "registered_at_utc", nullable = false, updatable = false)
    private OffsetDateTime registeredAt;

    @Column(name = "certificate_status", nullable = false, length = 16)
    private String status;

    @Column(name = "revoked_at_utc")
    private OffsetDateTime revokedAt;

    @Column(name = "revocation_reason", length = 64)
    private String revocationReason;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected RunnerCertificateJpaEntity() {}

    static RunnerCertificateJpaEntity create(
            RunnerWorkloadIdentityDirectory.CertificateRegistration registration) {
        RunnerCertificateJpaEntity entity = new RunnerCertificateJpaEntity();
        entity.certificateId = registration.certificateId();
        entity.runnerId = registration.runnerId().value();
        entity.organizationRef = registration.organizationRef();
        entity.certificateFingerprint = registration.certificateFingerprint();
        entity.subjectDn = registration.subjectDn();
        entity.serialNumber = registration.serialNumber();
        entity.validFrom = RunnerPersistenceTime.utc(registration.validFrom());
        entity.expiresAt = RunnerPersistenceTime.utc(registration.expiresAt());
        entity.registeredAt = RunnerPersistenceTime.utc(registration.registeredAt());
        entity.status = "ACTIVE";
        return entity;
    }

    boolean matches(RunnerWorkloadIdentityDirectory.CertificateRegistration registration) {
        return certificateId.equals(registration.certificateId())
                && runnerId.equals(registration.runnerId().value())
                && organizationRef.equals(registration.organizationRef())
                && certificateFingerprint.equals(registration.certificateFingerprint())
                && subjectDn.equals(registration.subjectDn())
                && serialNumber.equals(registration.serialNumber())
                && validFrom.toInstant().equals(RunnerPersistenceTime.instant(registration.validFrom()))
                && expiresAt.toInstant().equals(RunnerPersistenceTime.instant(registration.expiresAt()))
                && registeredAt.toInstant().equals(RunnerPersistenceTime.instant(registration.registeredAt()));
    }

    boolean activeAt(Instant instant) {
        return "ACTIVE".equals(status)
                && !instant.isBefore(validFrom.toInstant())
                && instant.isBefore(expiresAt.toInstant());
    }

    RunnerWorkloadIdentity identity() {
        return new RunnerWorkloadIdentity(
                new RunnerId(runnerId),
                organizationRef,
                certificateFingerprint,
                validFrom.toInstant(),
                expiresAt.toInstant());
    }

    RunnerWorkloadIdentityDirectory.RevocationDisposition revoke(
            RunnerWorkloadIdentityDirectory.CertificateRevocation revocation) {
        if ("REVOKED".equals(status)) {
            if (Objects.equals(revocationReason, revocation.reasonCode())) {
                return RunnerWorkloadIdentityDirectory.RevocationDisposition.IDEMPOTENT_REPLAY;
            }
            throw new IllegalStateException(
                    "certificate was already revoked with a different reason");
        }
        if (revocation.revokedAt().isBefore(registeredAt.toInstant())) {
            throw new IllegalArgumentException("revokedAt must not precede registration");
        }
        status = "REVOKED";
        revokedAt = RunnerPersistenceTime.utc(revocation.revokedAt());
        revocationReason = revocation.reasonCode();
        return RunnerWorkloadIdentityDirectory.RevocationDisposition.APPLIED;
    }
}

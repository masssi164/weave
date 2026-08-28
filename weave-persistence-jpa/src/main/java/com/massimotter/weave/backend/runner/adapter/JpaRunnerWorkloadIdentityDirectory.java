package com.massimotter.weave.backend.runner.adapter;

import com.massimotter.weave.backend.runner.application.RunnerWorkloadIdentity;
import com.massimotter.weave.backend.runner.application.RunnerWorkloadIdentityDirectory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL-backed Runner certificate directory. */
public class JpaRunnerWorkloadIdentityDirectory
        implements RunnerWorkloadIdentityDirectory {

    private final EntityManager entityManager;

    public JpaRunnerWorkloadIdentityDirectory(EntityManager entityManager) {
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager");
    }

    @Override
    @Transactional
    public RegistrationDisposition register(CertificateRegistration registration) {
        Objects.requireNonNull(registration, "registration");
        RunnerCertificateJpaEntity byId =
                entityManager.find(RunnerCertificateJpaEntity.class, registration.certificateId());
        if (byId != null) {
            if (byId.matches(registration)) {
                return RegistrationDisposition.IDEMPOTENT_REPLAY;
            }
            throw new IllegalStateException(
                    "certificateId already exists with a different Runner identity");
        }

        List<RunnerCertificateJpaEntity> byFingerprint = entityManager.createQuery(
                        """
                        select certificate
                        from RunnerCertificateJpaEntity certificate
                        where certificate.certificateFingerprint = :fingerprint
                        """,
                        RunnerCertificateJpaEntity.class)
                .setParameter("fingerprint", registration.certificateFingerprint())
                .setMaxResults(1)
                .getResultList();
        if (!byFingerprint.isEmpty()) {
            if (byFingerprint.getFirst().matches(registration)) {
                return RegistrationDisposition.IDEMPOTENT_REPLAY;
            }
            throw new IllegalStateException(
                    "certificate fingerprint already maps to a different Runner identity");
        }

        entityManager.persist(RunnerCertificateJpaEntity.create(registration));
        entityManager.flush();
        return RegistrationDisposition.CREATED;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RunnerWorkloadIdentity> resolveActive(
            String certificateFingerprint,
            Instant at) {
        String fingerprint = validateFingerprint(certificateFingerprint);
        Instant instant = Objects.requireNonNull(at, "at");
        List<RunnerCertificateJpaEntity> matches = entityManager.createQuery(
                        """
                        select certificate
                        from RunnerCertificateJpaEntity certificate
                        where certificate.certificateFingerprint = :fingerprint
                        """,
                        RunnerCertificateJpaEntity.class)
                .setParameter("fingerprint", fingerprint)
                .setMaxResults(1)
                .getResultList();
        if (matches.isEmpty() || !matches.getFirst().activeAt(instant)) {
            return Optional.empty();
        }
        return Optional.of(matches.getFirst().identity());
    }

    @Override
    @Transactional
    public RevocationDisposition revoke(CertificateRevocation revocation) {
        Objects.requireNonNull(revocation, "revocation");
        List<RunnerCertificateJpaEntity> matches = entityManager.createQuery(
                        """
                        select certificate
                        from RunnerCertificateJpaEntity certificate
                        where certificate.certificateFingerprint = :fingerprint
                        """,
                        RunnerCertificateJpaEntity.class)
                .setParameter("fingerprint", revocation.certificateFingerprint())
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .setMaxResults(1)
                .getResultList();
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("certificate does not exist");
        }
        RevocationDisposition disposition = matches.getFirst().revoke(revocation);
        entityManager.flush();
        return disposition;
    }

    private static String validateFingerprint(String value) {
        if (value == null || !SHA256.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "certificateFingerprint must be a sha256 digest");
        }
        return value;
    }
}

package com.massimotter.weave.backend.runner.adapter;

import com.massimotter.weave.backend.runner.application.RunnerWorkloadIdentity;
import com.massimotter.weave.backend.runner.application.RunnerWorkloadIdentityDirectory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL-backed Runner certificate directory. */
public final class JpaRunnerWorkloadIdentityDirectory
        implements RunnerWorkloadIdentityDirectory {

    private final EntityManager entityManager;

    public JpaRunnerWorkloadIdentityDirectory(EntityManager entityManager) {
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager");
    }

    @Override
    @Transactional
    public RegistrationDisposition register(CertificateRegistration registration) {
        throw notImplemented();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RunnerWorkloadIdentity> resolveActive(
            String certificateFingerprint,
            Instant at) {
        throw notImplemented();
    }

    @Override
    @Transactional
    public RevocationDisposition revoke(CertificateRevocation revocation) {
        throw notImplemented();
    }

    private UnsupportedOperationException notImplemented() {
        return new UnsupportedOperationException(
                "Runner certificate persistence is the current red TDD boundary for "
                        + entityManager);
    }
}

package com.massimotter.weave.backend.runner.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.runner.application.RunnerWorkloadIdentityDirectory;
import com.massimotter.weave.backend.runner.application.RunnerWorkloadIdentityDirectory.CertificateRegistration;
import com.massimotter.weave.backend.runner.application.RunnerWorkloadIdentityDirectory.CertificateRevocation;
import com.massimotter.weave.backend.runner.application.RunnerWorkloadIdentityDirectory.RegistrationDisposition;
import com.massimotter.weave.backend.runner.application.RunnerWorkloadIdentityDirectory.RevocationDisposition;
import com.massimotter.weave.backend.runner.domain.RunnerControl.RunnerId;
import com.massimotter.weave.backend.testing.JpaTestDatabase;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("postgres")
class JpaRunnerWorkloadIdentityDirectoryPostgresTest {

    private static final String FINGERPRINT =
            "sha256:1111111111111111111111111111111111111111111111111111111111111111";
    private static final RunnerId RUNNER = new RunnerId("runner_certificate_01");
    private static final Instant VALID_FROM = Instant.parse("2026-08-28T10:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-08-29T10:00:00Z");

    @Test
    void registeredFingerprintResolvesToExactlyOneRunnerAndOrganization() {
        DataSource dataSource = JpaTestDatabase.entityFirstDataSource("runner-cert-resolve");
        RunnerWorkloadIdentityDirectory directory = directory(dataSource);

        assertThat(directory.register(registration()))
                .isEqualTo(RegistrationDisposition.CREATED);

        var identity = directory.resolveActive(FINGERPRINT, VALID_FROM.plusSeconds(1)).orElseThrow();
        assertThat(identity.runnerId()).isEqualTo(RUNNER);
        assertThat(identity.organizationRef()).isEqualTo("org:trusted");
        assertThat(identity.certificateFingerprint()).isEqualTo(FINGERPRINT);
        assertThat(identity.certificateValidFrom()).isEqualTo(VALID_FROM);
        assertThat(identity.certificateExpiresAt()).isEqualTo(EXPIRES_AT);
    }

    @Test
    void inactiveOrRevokedCertificateNeverResolves() {
        DataSource dataSource = JpaTestDatabase.entityFirstDataSource("runner-cert-lifecycle");
        RunnerWorkloadIdentityDirectory directory = directory(dataSource);
        directory.register(registration());

        assertThat(directory.resolveActive(FINGERPRINT, VALID_FROM.minusNanos(1))).isEmpty();
        assertThat(directory.resolveActive(FINGERPRINT, EXPIRES_AT)).isEmpty();
        assertThat(directory.resolveActive(
                        "sha256:2222222222222222222222222222222222222222222222222222222222222222",
                        VALID_FROM.plusSeconds(1)))
                .isEmpty();

        CertificateRevocation revocation =
                new CertificateRevocation(FINGERPRINT, "OPERATOR_REVOKED", VALID_FROM.plusSeconds(2));
        assertThat(directory.revoke(revocation)).isEqualTo(RevocationDisposition.APPLIED);
        assertThat(directory.revoke(revocation)).isEqualTo(RevocationDisposition.IDEMPOTENT_REPLAY);

        RunnerWorkloadIdentityDirectory restarted = directory(dataSource);
        assertThat(restarted.resolveActive(FINGERPRINT, VALID_FROM.plusSeconds(3))).isEmpty();
    }

    @Test
    void exactRegistrationReplayIsIdempotentButConflictingFingerprintMappingFailsClosed() {
        DataSource dataSource = JpaTestDatabase.entityFirstDataSource("runner-cert-conflict");
        RunnerWorkloadIdentityDirectory directory = directory(dataSource);

        assertThat(directory.register(registration()))
                .isEqualTo(RegistrationDisposition.CREATED);
        assertThat(directory.register(registration()))
                .isEqualTo(RegistrationDisposition.IDEMPOTENT_REPLAY);

        CertificateRegistration conflicting = new CertificateRegistration(
                UUID.fromString("00000000-0000-0000-0000-000000009999"),
                new RunnerId("runner_certificate_02"),
                "org:other",
                FINGERPRINT,
                "CN=runner_certificate_02,OU=org:other",
                "beef",
                VALID_FROM,
                EXPIRES_AT,
                VALID_FROM);

        assertThatThrownBy(() -> directory.register(conflicting))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("different Runner identity");
    }

    private RunnerWorkloadIdentityDirectory directory(DataSource dataSource) {
        return JpaTestDatabase.transactional(
                dataSource,
                new JpaRunnerWorkloadIdentityDirectory(
                        JpaTestDatabase.entityManager(dataSource)));
    }

    private CertificateRegistration registration() {
        return new CertificateRegistration(
                UUID.fromString("00000000-0000-0000-0000-000000009001"),
                RUNNER,
                "org:trusted",
                FINGERPRINT,
                "CN=runner_certificate_01,OU=org:trusted",
                "abcd1234",
                VALID_FROM,
                EXPIRES_AT,
                VALID_FROM);
    }
}

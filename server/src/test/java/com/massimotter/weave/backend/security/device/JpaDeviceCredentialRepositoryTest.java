package com.massimotter.weave.backend.security.device;

import com.massimotter.weave.backend.persistence.jpa.security.DeviceCredentialJpaRepository;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

class JpaDeviceCredentialRepositoryTest {

    @Test
    void credentialAndRevocationSurviveAdapterRestartWithoutCrossingAuthorityBoundary() {
        DriverManagerDataSource dataSource = migratedDataSource();
        JpaDeviceCredentialRepository repository = repository(dataSource);
        DeviceCredential credential = new DeviceCredential(
                "files-device-" + UUID.randomUUID(),
                "files",
                "tenant-1",
                "user:subject-1",
                "subject-1",
                "massimo",
                "webdav",
                "Finder",
                Set.of("files.read", "files.upload"),
                "not-a-plaintext-secret",
                Instant.parse("2026-07-09T10:00:00Z"),
                Instant.parse("2026-10-07T10:00:00Z"),
                null);

        repository.save(credential);
        JpaDeviceCredentialRepository restarted = repository(dataSource);

        assertThat(restarted.findById(credential.credentialId())).contains(credential);
        assertThat(restarted.findByDomainAndPrincipal("files", "user:subject-1"))
                .containsExactly(credential);
        assertThat(restarted.findByDomainAndPrincipal("calendar", "user:subject-1")).isEmpty();

        Instant revokedAt = Instant.parse("2026-07-09T12:00:00Z");
        restarted.save(credential.revoke(revokedAt));
        assertThat(repository(dataSource).findById(credential.credentialId()))
                .get().extracting(DeviceCredential::revokedAt).isEqualTo(revokedAt);
    }

    private JpaDeviceCredentialRepository repository(DriverManagerDataSource dataSource) {
        DeviceCredentialJpaRepository springData =
                com.massimotter.weave.backend.testing.JpaTestDatabase.repository(
                        dataSource, DeviceCredentialJpaRepository.class);
        return com.massimotter.weave.backend.testing.JpaTestDatabase.transactional(
                dataSource,
                new JpaDeviceCredentialRepository(
                        springData, tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build()));
    }

    private DriverManagerDataSource migratedDataSource() {
        return com.massimotter.weave.backend.testing.JpaTestDatabase
                .entityFirstDataSource("device-credential");
    }
}

package com.massimotter.weave.backend.security.device;

import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

class JpaDeviceCredentialRepositoryTest {

    @Test
    void credentialAndRevocationSurviveRepositoryRestart() {
        DriverManagerDataSource dataSource = migratedDataSource();
        var repository = repository(dataSource);
        DeviceCredential credential = credential();

        repository.save(credential);

        var restartedRepository = repository(dataSource);
        assertThat(restartedRepository.findById(credential.credentialId()))
                .contains(credential);
        assertThat(restartedRepository.findByDomainAndPrincipal("files", "user:subject-1"))
                .containsExactly(credential);

        Instant revokedAt = Instant.parse("2026-07-09T12:00:00Z");
        restartedRepository.save(credential.revoke(revokedAt));

        assertThat(repository(dataSource).findById(credential.credentialId()))
                .get()
                .extracting(DeviceCredential::revokedAt)
                .isEqualTo(revokedAt);
    }

    @Test
    void lookupCannotCrossDomainOrPrincipalBoundary() {
        DriverManagerDataSource dataSource = migratedDataSource();
        var repository = repository(dataSource);
        repository.save(credential());

        assertThat(repository.findByDomainAndPrincipal("calendar", "user:subject-1"))
                .isEmpty();
        assertThat(repository.findByDomainAndPrincipal("files", "user:subject-2"))
                .isEmpty();
    }

    private JpaDeviceCredentialRepository repository(DriverManagerDataSource dataSource) {
        DeviceCredentialJpaRepository springData =
                com.massimotter.weave.backend.testing.JpaTestDatabase.repository(
                        dataSource,
                        DeviceCredentialJpaRepository.class);
        return com.massimotter.weave.backend.testing.JpaTestDatabase.transactional(
                dataSource,
                new JpaDeviceCredentialRepository(
                        springData,
                        tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build()));
    }

    private DeviceCredential credential() {
        return new DeviceCredential(
                "files_device_" + UUID.randomUUID(),
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
    }

    private DriverManagerDataSource migratedDataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_UPPER=true;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        return dataSource;
    }
}

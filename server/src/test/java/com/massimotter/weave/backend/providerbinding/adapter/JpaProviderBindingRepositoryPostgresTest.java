package com.massimotter.weave.backend.providerbinding.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.providerbinding.domain.ProviderBinding.State;
import com.massimotter.weave.backend.providerbinding.domain.ProviderObjectMapping;
import java.time.Instant;
import com.massimotter.weave.backend.testing.JpaTestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class JpaProviderBindingRepositoryPostgresTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void activationIsMonotonicAndMappingsRemainRevisionPrivate() {
        DriverManagerDataSource dataSource = dataSource();
        JpaTestDatabase.initializeSchema(dataSource);
        var repository = ProviderBindingJpaTestFactory.create(dataSource);
        Instant now = Instant.parse("2026-07-21T13:00:00Z");

        var nextcloud = repository.activate(
                "org:example", "files", 0, "nextcloud-webdav", "secretref:files:nextcloud", now);
        var mapping = repository.saveMapping(new ProviderObjectMapping(
                "org:example", "files", nextcloud.revision(), "file:stable-1", "nextcloud-fileid:42",
                "nextcloud-oc-fileid", now, now));
        var minio = repository.activate(
                "org:example", "files", nextcloud.revision(), "weave-s3-minio", "secretref:files:minio",
                now.plusSeconds(1));

        assertThat(nextcloud.revision()).isEqualTo(1);
        assertThat(repository.revision("org:example", "files", 1)).get()
                .extracting(binding -> binding.state()).isEqualTo(State.RETIRED);
        assertThat(minio.revision()).isEqualTo(2);
        assertThat(repository.current("org:example", "files")).contains(minio);
        assertThat(repository.mappingByProviderRef("org:example", "files", 1, "nextcloud-fileid:42"))
                .contains(mapping);
        assertThat(repository.mappingByProviderRef("org:example", "files", 2, "nextcloud-fileid:42"))
                .isEmpty();

        assertThatThrownBy(() -> repository.activate(
                "org:example", "files", 1, "nextcloud-webdav", "secretref:stale", now.plusSeconds(2)))
                .isInstanceOf(JpaProviderBindingRepository.StaleProviderBindingException.class);
    }

    private DriverManagerDataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(POSTGRES.getDriverClassName());
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }
}

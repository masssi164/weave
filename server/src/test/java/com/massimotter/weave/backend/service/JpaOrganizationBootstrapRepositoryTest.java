package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.persistence.jpa.OrganizationBootstrapJpaRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

class JpaOrganizationBootstrapRepositoryTest {

    @Test
    void repeatedBootstrapReplacesTheManagedVersionedEntity() {
        DriverManagerDataSource dataSource =
                com.massimotter.weave.backend.testing.JpaTestDatabase
                        .entityFirstDataSource("organization-bootstrap");
        JpaOrganizationBootstrapRepository repository = repository(dataSource);
        OrganizationBootstrapRecord initial = new OrganizationBootstrapRecord(
                "ORG-WEAVE",
                "fresh-start",
                "issuer#owner",
                List.of("issuer#owner"),
                Instant.parse("2026-08-01T20:00:00Z"));
        OrganizationBootstrapRecord updated = new OrganizationBootstrapRecord(
                "org-weave",
                "fresh-start-verified",
                "issuer#owner",
                List.of("issuer#owner", "issuer#admin"),
                Instant.parse("2026-08-01T20:01:00Z"));

        repository.save(initial);
        repository.save(updated);

        assertThat(repository(dataSource).findByOrganizationId("ORG-WEAVE"))
                .contains(updated);
    }

    private JpaOrganizationBootstrapRepository repository(DriverManagerDataSource dataSource) {
        OrganizationBootstrapJpaRepository springData =
                com.massimotter.weave.backend.testing.JpaTestDatabase.repository(
                        dataSource, OrganizationBootstrapJpaRepository.class);
        return com.massimotter.weave.backend.testing.JpaTestDatabase.transactional(
                dataSource,
                new JpaOrganizationBootstrapRepository(
                        springData,
                        tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build()));
    }
}

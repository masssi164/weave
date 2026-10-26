package com.massimotter.weave.backend.identity.organization.persistence;


import com.massimotter.weave.backend.identity.organization.domain.OrganizationBootstrapRecord;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.massimotter.weave.backend.testing.JpaTestDatabase;
import org.springframework.transaction.support.TransactionTemplate;

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

    @Test
    void organizationUpdatesParticipateInTheCallerTransactionAndStayTenantScoped() {
        var dataSource = JpaTestDatabase.entityFirstDataSource("organization-bootstrap-rollback");
        var repository = repository(dataSource);
        var now = Instant.parse("2026-08-01T20:00:00Z");
        var first = new OrganizationBootstrapRecord("org-first", "existing_org", "issuer#owner",
                List.of("issuer#owner"), now);
        var second = new OrganizationBootstrapRecord("org-second", "existing_org", "issuer#other",
                List.of("issuer#other"), now);
        repository.save(first);
        repository.save(second);
        var transaction = new TransactionTemplate(JpaTestDatabase.transactionManager(dataSource));
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            repository.save(new OrganizationBootstrapRecord("org-first", "new_org", "issuer#changed",
                    List.of("issuer#changed"), now.plusSeconds(60)));
            throw new IllegalStateException("caller rollback");
        })).hasMessage("caller rollback");
        assertThat(repository.findByOrganizationId("ORG-FIRST")).contains(first);
        assertThat(repository.findByOrganizationId("ORG-SECOND")).contains(second);
    }

    @Test
    void staleBootstrapAuthorityCannotOverwriteTheWinningVersion() {
        var dataSource = JpaTestDatabase.entityFirstDataSource("organization-bootstrap-version");
        var repository = repository(dataSource);
        var now = Instant.parse("2026-08-01T20:00:00Z");
        repository.save(new OrganizationBootstrapRecord("org-version", "existing_org", "issuer#owner",
                List.of("issuer#owner"), now));
        var entities = JpaTestDatabase.repository(dataSource, OrganizationBootstrapJpaRepository.class);
        var winner = entities.findById("org-version").orElseThrow();
        var stale = entities.findById("org-version").orElseThrow();
        winner.replaceBootstrap("new_org", "issuer#winner", "[\"issuer#winner\"]", now.atOffset(java.time.ZoneOffset.UTC));
        stale.replaceBootstrap("new_org", "issuer#stale", "[\"issuer#stale\"]", now.atOffset(java.time.ZoneOffset.UTC));
        entities.saveAndFlush(winner);
        assertThatThrownBy(() -> entities.saveAndFlush(stale))
                .isInstanceOf(jakarta.persistence.OptimisticLockException.class);
        assertThat(repository.findByOrganizationId("org-version")).get()
                .extracting(OrganizationBootstrapRecord::actorPrimaryIdentityKey).isEqualTo("issuer#winner");
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

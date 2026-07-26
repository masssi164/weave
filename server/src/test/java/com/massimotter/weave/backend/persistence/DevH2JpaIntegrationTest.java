package com.massimotter.weave.backend.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.massimotter.weave.backend.persistence.jpa.OrganizationBootstrapEntity;
import com.massimotter.weave.backend.persistence.jpa.OrganizationBootstrapJpaRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Tag("dev-h2-integration")
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DevH2JpaIntegrationTest {

    @Autowired
    private Environment environment;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private OrganizationBootstrapJpaRepository repository;

    @Test
    void devProfileUsesPortableEntityGeneratedH2AndPersistsThroughJpa() {
        assertThat(environment.getActiveProfiles()).containsExactly("dev");
        assertThat(environment.getRequiredProperty("spring.datasource.url"))
                .startsWith("jdbc:h2:mem:weave-dev")
                .contains("MODE=PostgreSQL");
        assertThat(environment.getRequiredProperty("spring.datasource.driver-class-name"))
                .isEqualTo("org.h2.Driver");
        assertThat(environment.getRequiredProperty("spring.jpa.hibernate.ddl-auto"))
                .isEqualTo("create-drop");
        assertThat(environment.getRequiredProperty("spring.jpa.open-in-view"))
                .isEqualTo("false");
        assertThat(applicationContext.getBeanNamesForType(org.flywaydb.core.Flyway.class))
                .isEmpty();

        OffsetDateTime bootstrappedAt = OffsetDateTime.of(
                2026, 7, 22, 12, 30, 0, 0, ZoneOffset.UTC);
        repository.saveAndFlush(new OrganizationBootstrapEntity(
                "org-dev-h2-proof",
                "invite-first",
                "issuer+subject:dev-proof",
                "[\"issuer+subject:dev-proof\"]",
                bootstrappedAt));

        OrganizationBootstrapEntity persisted = repository.findById("org-dev-h2-proof").orElseThrow();
        assertThat(persisted.bootstrapMode()).isEqualTo("invite-first");
        assertThat(persisted.actorPrimaryIdentityKey()).isEqualTo("issuer+subject:dev-proof");
        assertThat(persisted.retainedAdminPrimaryIdentityKeysJson())
                .isEqualTo("[\"issuer+subject:dev-proof\"]");
        assertThat(persisted.bootstrappedAt().withOffsetSameInstant(ZoneOffset.UTC))
                .isEqualTo(bootstrappedAt);
        assertThat(repository.findAll())
                .extracting(OrganizationBootstrapEntity::organizationId)
                .containsAll(List.of("org-dev-h2-proof"));
    }
}

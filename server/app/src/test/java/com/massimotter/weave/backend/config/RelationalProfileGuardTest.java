package com.massimotter.weave.backend.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class RelationalProfileGuardTest {

    @Test
    void devAcceptsEmbeddedH2ForIsolatedDeveloperFeedback() {
        MockEnvironment environment = environment(
                "dev",
                "jdbc:h2:mem:weave-dev;MODE=PostgreSQL",
                "org.h2.Driver");

        assertThatCode(() -> new RelationalProfileGuard(environment).validate())
                .doesNotThrowAnyException();
    }

    @Test
    void servingRejectsFlywayAutoMigrationEvenOutsidePostgresqlProfiles() {
        MockEnvironment environment = environment(
                        "dev",
                        "jdbc:h2:mem:weave-dev;MODE=PostgreSQL",
                        "org.h2.Driver")
                .withProperty("spring.flyway.enabled", "true");

        assertThatThrownBy(() -> new RelationalProfileGuard(environment).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Flyway auto-migration");
    }

    @Test
    void servingRejectsMissingFlywayAutoMigrationFence() {
        MockEnvironment environment = new MockEnvironment();

        assertThatThrownBy(() -> new RelationalProfileGuard(environment).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Flyway auto-migration");
    }

    @Test
    void testRejectsEmbeddedDatabaseEvenWhenOnlyDeploymentProfileIsSet() {
        MockEnvironment environment = environment(
                "test",
                "jdbc:h2:mem:weave-test",
                "org.h2.Driver");

        assertThatThrownBy(() -> new RelationalProfileGuard(environment).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PostgreSQL");
    }

    @Test
    void dogfoodRejectsEmbeddedDatabaseEvenWhenOnlyDeploymentProfileIsSet() {
        MockEnvironment environment = environment(
                "dogfood",
                "jdbc:h2:mem:weave-dogfood",
                "org.h2.Driver");

        assertThatThrownBy(() -> new RelationalProfileGuard(environment).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PostgreSQL");
    }

    @Test
    void e2eAcceptsExplicitPostgresqlConfiguration() {
        MockEnvironment environment = environment(
                "e2e",
                "jdbc:postgresql://postgres:5432/weave_e2e",
                "org.postgresql.Driver");

        assertThatCode(() -> new RelationalProfileGuard(environment).validate())
                .doesNotThrowAnyException();
    }

    @Test
    void prodRequiresThePostgresqlDriverAsWellAsItsUrl() {
        MockEnvironment environment = environment(
                "prod",
                "jdbc:postgresql://postgres:5432/weave",
                "org.h2.Driver");

        assertThatThrownBy(() -> new RelationalProfileGuard(environment).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PostgreSQL driver");
    }

    @Test
    void prodAcceptsExplicitPostgresqlConfiguration() {
        MockEnvironment environment = environment(
                "prod",
                "jdbc:postgresql://postgres:5432/weave",
                "org.postgresql.Driver");

        assertThatCode(() -> new RelationalProfileGuard(environment).validate())
                .doesNotThrowAnyException();
    }

    @Test
    void prodRejectsServingSchemaMutation() {
        MockEnvironment environment = environment(
                        "prod",
                        "jdbc:postgresql://postgres:5432/weave",
                        "org.postgresql.Driver")
                .withProperty("spring.jpa.hibernate.ddl-auto", "update");

        assertThatThrownBy(() -> new RelationalProfileGuard(environment).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Hibernate validate");
    }

    private static MockEnvironment environment(String deploymentProfile, String url, String driver) {
        return new MockEnvironment()
                .withProperty("weave.deployment.profile", deploymentProfile)
                .withProperty("spring.datasource.url", url)
                .withProperty("spring.datasource.driver-class-name", driver)
                .withProperty("spring.flyway.enabled", "false")
                .withProperty("spring.jpa.hibernate.ddl-auto", "validate");
    }
}

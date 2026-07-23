package com.massimotter.weave.backend.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class RelationalProfileGuardTest {

    @Test
    void devAcceptsEmbeddedH2ForIsolatedServerAndE2eTesting() {
        MockEnvironment environment = environment(
                "dev",
                "jdbc:h2:mem:weave-dev;MODE=PostgreSQL",
                "org.h2.Driver");

        assertThatCode(() -> new RelationalProfileGuard(environment).afterSingletonsInstantiated())
                .doesNotThrowAnyException();
    }

    @Test
    void dogfoodRejectsEmbeddedDatabaseEvenWhenOnlyDeploymentProfileIsSet() {
        MockEnvironment environment = environment(
                "dogfood",
                "jdbc:h2:mem:weave-dogfood",
                "org.h2.Driver");

        assertThatThrownBy(() -> new RelationalProfileGuard(environment).afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PostgreSQL");
    }

    @Test
    void mainRequiresThePostgresqlDriverAsWellAsItsUrl() {
        MockEnvironment environment = environment(
                "main",
                "jdbc:postgresql://postgres:5432/weave",
                "org.h2.Driver");

        assertThatThrownBy(() -> new RelationalProfileGuard(environment).afterSingletonsInstantiated())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PostgreSQL driver");
    }

    @Test
    void releaseProfileAcceptsExplicitPostgresqlConfiguration() {
        MockEnvironment environment = environment(
                "main",
                "jdbc:postgresql://postgres:5432/weave",
                "org.postgresql.Driver");

        assertThatCode(() -> new RelationalProfileGuard(environment).afterSingletonsInstantiated())
                .doesNotThrowAnyException();
    }

    private static MockEnvironment environment(String deploymentProfile, String url, String driver) {
        return new MockEnvironment()
                .withProperty("weave.deployment.profile", deploymentProfile)
                .withProperty("spring.datasource.url", url)
                .withProperty("spring.datasource.driver-class-name", driver);
    }
}

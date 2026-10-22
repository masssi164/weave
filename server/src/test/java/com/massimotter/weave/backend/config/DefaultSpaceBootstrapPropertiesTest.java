package com.massimotter.weave.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import static org.assertj.core.api.Assertions.*;

class DefaultSpaceBootstrapPropertiesTest {
    @Test void defaultsToDisabledProduction() {
        var properties = new DefaultSpaceBootstrapProperties(false, null, null);
        assertThat(properties.enabled()).isFalse();
        assertThat(properties.environment()).isEqualTo("prod");
    }
    @Test void enabledProductionUnknownAndMissingEnvironmentFailClosed() {
        for (String environment : new String[] {null, "prod", "production", "dev", "", "dogfood-prod"}) {
            assertThatIllegalArgumentException().isThrownBy(() ->
                    new DefaultSpaceBootstrapProperties(true, environment, "/private/token"));
        }
    }
    @Test void productionEnablingCannotStartAnExceptionalServer() {
        new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class)
                .withPropertyValues("weave.spaces.bootstrap.enabled=true", "weave.spaces.bootstrap.environment=prod")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(DefaultSpaceBootstrapProperties.class)
    static class PropertiesConfiguration {}

    @Test void onlyExplicitDogfoodAndIsolatedE2eCanEnable() {
        for (String environment : new String[] {"dogfood", "e2e"}) {
            assertThat(new DefaultSpaceBootstrapProperties(true, environment, "/private/token").enabled()).isTrue();
        }
    }
}

package com.massimotter.weave.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

/** Proves the normal Server graph cannot construct or auto-run Flyway. */
@SpringBootTest
class ServingFlywayAuthorityTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Environment environment;

    @Test
    void sharedServingConfigurationDisablesFlywayAndCreatesNoFlywayBean() {
        assertThat(environment.getProperty("spring.flyway.enabled", Boolean.class))
                .isFalse();
        assertThat(applicationContext.getBeansOfType(Flyway.class))
                .isEmpty();
    }
}

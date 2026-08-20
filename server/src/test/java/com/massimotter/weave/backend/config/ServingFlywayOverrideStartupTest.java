package com.massimotter.weave.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class ServingFlywayOverrideStartupTest {

    @Test
    void reenabledFlywayFailsBeforeServingSingletonInitialization() {
        AtomicBoolean singletonCreated = new AtomicBoolean();

        new ApplicationContextRunner()
                .withUserConfiguration(GuardConfiguration.class)
                .withBean("servingSingleton", Object.class, () -> {
                    singletonCreated.set(true);
                    return new Object();
                })
                .withPropertyValues("spring.flyway.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessage(
                                    "Serving processes require Spring Boot Flyway auto-migration to be disabled");
                    assertThat(singletonCreated).isFalse();
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import(RelationalProfileGuard.class)
    static class GuardConfiguration {}
}

package com.massimotter.weave.backend.config;

import java.util.Arrays;
import java.util.Set;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Fails before singleton initialization when a serving persistence profile is unsafe. */
@Component
public final class RelationalProfileGuard implements BeanFactoryPostProcessor, EnvironmentAware {
    private static final Set<String> POSTGRES_REQUIRED_PROFILES =
            Set.of("test", "dogfood", "prod", "e2e");

    private Environment environment;

    public RelationalProfileGuard() {}

    public RelationalProfileGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory)
            throws BeansException {
        validate();
    }

    void validate() {
        if (environment == null) {
            throw new IllegalStateException("Serving persistence environment is unavailable");
        }
        Boolean flywayEnabled = environment.getProperty("spring.flyway.enabled", Boolean.class);
        if (!Boolean.FALSE.equals(flywayEnabled)) {
            throw new IllegalStateException(
                    "Serving processes require Spring Boot Flyway auto-migration to be disabled");
        }
        boolean postgresRequired = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(POSTGRES_REQUIRED_PROFILES::contains)
                || POSTGRES_REQUIRED_PROFILES.contains(environment.getProperty(
                        "weave.deployment.profile", ""));
        if (!postgresRequired) {
            return;
        }
        String url = environment.getProperty("spring.datasource.url");
        if (url == null || !url.startsWith("jdbc:postgresql://")) {
            throw new IllegalStateException(
                    "Relational server profiles require a standard PostgreSQL datasource URL");
        }
        String driver = environment.getProperty("spring.datasource.driver-class-name");
        if (!"org.postgresql.Driver".equals(driver)) {
            throw new IllegalStateException(
                    "Relational server profiles require the PostgreSQL driver");
        }
        String ddlMode = environment.getProperty("spring.jpa.hibernate.ddl-auto", "");
        if (!"validate".equals(ddlMode)) {
            throw new IllegalStateException(
                    "Relational serving profiles require Hibernate validate");
        }
    }
}

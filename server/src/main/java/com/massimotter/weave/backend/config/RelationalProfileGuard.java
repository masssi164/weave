package com.massimotter.weave.backend.config;

import java.util.Arrays;
import java.util.Set;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Fails closed when a relational runtime profile uses a non-PostgreSQL database. */
@Component
public final class RelationalProfileGuard implements SmartInitializingSingleton {
    private static final Set<String> POSTGRES_REQUIRED_PROFILES =
            Set.of("test", "dogfood", "prod", "e2e");

    private final Environment environment;
    public RelationalProfileGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterSingletonsInstantiated() {
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

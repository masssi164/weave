package com.massimotter.weave.shared.persistence;

import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods = false)
public class SharedSchemaReadinessConfiguration {

    @Bean("weaveSchemaVersion")
    HealthIndicator weaveSchemaVersionHealthIndicator(ObjectProvider<DataSource> dataSources) {
        DataSource dataSource = dataSources.getIfAvailable();
        if (dataSource == null) {
            return () -> Health.down()
                    .withDetail("expectedSchemaVersion", SharedPersistenceModel.VERSION)
                    .withDetail("observedSchemaVersion", "unavailable")
                    .build();
        }
        return new SharedSchemaReadinessHealthIndicator(new JdbcTemplate(dataSource));
    }
}

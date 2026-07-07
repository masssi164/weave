package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.provider.JdbcProviderSelectionRepository;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WeavePersistenceProperties.class)
@ConditionalOnProperty(name = "weave.provider.selections.storage.mode", havingValue = "jdbc")
public class WeavePersistenceConfiguration {

    @Bean
    DataSource weaveDataSource(WeavePersistenceProperties properties) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(properties.requiredUrl());
        dataSource.setUsername(properties.normalizedUsername());
        dataSource.setPassword(properties.normalizedPassword());
        if (!properties.normalizedDriverClassName().isBlank()) {
            dataSource.setDriverClassName(properties.normalizedDriverClassName());
        }
        return dataSource;
    }

    @Bean(initMethod = "migrate")
    Flyway weaveFlyway(DataSource weaveDataSource) {
        return Flyway.configure()
                .dataSource(weaveDataSource)
                .locations("classpath:db/migration")
                .load();
    }

    @Bean
    JdbcTemplate weaveJdbcTemplate(DataSource weaveDataSource, Flyway weaveFlyway) {
        return new JdbcTemplate(weaveDataSource);
    }

    @Bean
    JdbcProviderSelectionRepository jdbcProviderSelectionRepository(JdbcTemplate weaveJdbcTemplate) {
        return new JdbcProviderSelectionRepository(weaveJdbcTemplate);
    }
}

package com.massimotter.weave.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.audit.JdbcAuditEventPublisher;
import com.massimotter.weave.backend.provider.JdbcProviderSelectionRepository;
import com.massimotter.weave.backend.service.JdbcProductProfileOverrideRepository;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WeavePersistenceProperties.class)
public class WeavePersistenceConfiguration {

    @Bean
    @ConditionalOnExpression("'${weave.provider.selections.storage.mode:file}' == 'jdbc' "
            + "|| '${weave.profile.storage.mode:file}' == 'jdbc' "
            + "|| '${weave.audit.events.storage.mode:file}' == 'jdbc' "
            + "|| '${weave.migration.evidence.storage.mode:file}' == 'jdbc'")
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
    @ConditionalOnExpression("'${weave.provider.selections.storage.mode:file}' == 'jdbc' "
            + "|| '${weave.profile.storage.mode:file}' == 'jdbc' "
            + "|| '${weave.audit.events.storage.mode:file}' == 'jdbc' "
            + "|| '${weave.migration.evidence.storage.mode:file}' == 'jdbc'")
    Flyway weaveFlyway(DataSource weaveDataSource) {
        return Flyway.configure()
                .dataSource(weaveDataSource)
                .locations("classpath:db/migration")
                .load();
    }

    @Bean
    @ConditionalOnExpression("'${weave.provider.selections.storage.mode:file}' == 'jdbc' "
            + "|| '${weave.profile.storage.mode:file}' == 'jdbc' "
            + "|| '${weave.audit.events.storage.mode:file}' == 'jdbc' "
            + "|| '${weave.migration.evidence.storage.mode:file}' == 'jdbc'")
    JdbcTemplate weaveJdbcTemplate(DataSource weaveDataSource, Flyway weaveFlyway) {
        return new JdbcTemplate(weaveDataSource);
    }

    @Bean
    @ConditionalOnProperty(name = "weave.provider.selections.storage.mode", havingValue = "jdbc")
    JdbcProviderSelectionRepository jdbcProviderSelectionRepository(JdbcTemplate weaveJdbcTemplate) {
        return new JdbcProviderSelectionRepository(weaveJdbcTemplate);
    }

    @Bean
    @ConditionalOnProperty(name = "weave.profile.storage.mode", havingValue = "jdbc")
    JdbcProductProfileOverrideRepository jdbcProductProfileOverrideRepository(JdbcTemplate weaveJdbcTemplate) {
        return new JdbcProductProfileOverrideRepository(weaveJdbcTemplate);
    }

    @Bean
    @ConditionalOnProperty(name = "weave.audit.events.storage.mode", havingValue = "jdbc")
    JdbcAuditEventPublisher jdbcAuditEventPublisher(JdbcTemplate weaveJdbcTemplate, ObjectMapper objectMapper) {
        return new JdbcAuditEventPublisher(weaveJdbcTemplate, objectMapper);
    }
}

package com.massimotter.weave.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.agentruntime.adapter.JdbcRuntimeCellRepository;
import com.massimotter.weave.backend.agentruntime.adapter.JdbcRuntimeCommandRepository;
import com.massimotter.weave.backend.agentruntime.adapter.JdbcRuntimeGovernanceRepository;
import com.massimotter.weave.backend.agentruntime.adapter.JdbcRuntimeProfileRepository;
import com.massimotter.weave.backend.audit.JdbcAuditEventPublisher;
import com.massimotter.weave.backend.files.adapter.JdbcFilesAuthorityRepository;
import com.massimotter.weave.backend.files.application.FilesLockService;
import com.massimotter.weave.backend.files.application.FilesMutationIntentService;
import com.massimotter.weave.backend.operation.adapter.JdbcOperationIntentRepository;
import com.massimotter.weave.backend.operation.application.OperationIntentService;
import com.massimotter.weave.backend.provider.JdbcProviderSelectionRepository;
import com.massimotter.weave.backend.providerbinding.adapter.JdbcProviderBindingRepository;
import com.massimotter.weave.backend.providerbinding.application.FilesProviderBindingBootstrap;
import com.massimotter.weave.backend.providerbinding.application.ProviderBindingBootstrapProperties;
import com.massimotter.weave.backend.service.JdbcProductProfileOverrideRepository;
import com.massimotter.weave.backend.security.device.JdbcDeviceCredentialRepository;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({WeavePersistenceProperties.class, ProviderBindingBootstrapProperties.class})
public class WeavePersistenceConfiguration {

    @Bean
    @ConditionalOnExpression("'${weave.provider.selections.storage.mode:file}' == 'jdbc' "
            + "|| '${weave.profile.storage.mode:file}' == 'jdbc' "
            + "|| '${weave.audit.events.storage.mode:file}' == 'jdbc' "
            + "|| '${weave.security.device-credentials.storage.mode:memory}' == 'jdbc' "
            + "|| '${weave.migration.evidence.storage.mode:file}' == 'jdbc' "
            + "|| '${weave.matrix.e2ee.storage.mode:memory}' == 'jdbc' "
            + "|| '${weave.chat.storage.mode:memory}' == 'jdbc' "
            + "|| '${weave.identity.invitations.storage-mode:memory}' == 'jdbc' "
            + "|| '${weave.operation-intents.storage.mode:disabled}' == 'jdbc' "
            + "|| '${weave.agent-runtime.storage.mode:disabled}' == 'jdbc'")
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
            + "|| '${weave.security.device-credentials.storage.mode:memory}' == 'jdbc' "
            + "|| '${weave.migration.evidence.storage.mode:file}' == 'jdbc' "
            + "|| '${weave.matrix.e2ee.storage.mode:memory}' == 'jdbc' "
            + "|| '${weave.chat.storage.mode:memory}' == 'jdbc' "
            + "|| '${weave.identity.invitations.storage-mode:memory}' == 'jdbc' "
            + "|| '${weave.operation-intents.storage.mode:disabled}' == 'jdbc' "
            + "|| '${weave.agent-runtime.storage.mode:disabled}' == 'jdbc'")
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
            + "|| '${weave.security.device-credentials.storage.mode:memory}' == 'jdbc' "
            + "|| '${weave.migration.evidence.storage.mode:file}' == 'jdbc' "
            + "|| '${weave.matrix.e2ee.storage.mode:memory}' == 'jdbc' "
            + "|| '${weave.chat.storage.mode:memory}' == 'jdbc' "
            + "|| '${weave.identity.invitations.storage-mode:memory}' == 'jdbc' "
            + "|| '${weave.operation-intents.storage.mode:disabled}' == 'jdbc' "
            + "|| '${weave.agent-runtime.storage.mode:disabled}' == 'jdbc'")
    JdbcTemplate weaveJdbcTemplate(DataSource weaveDataSource, Flyway weaveFlyway) {
        return new JdbcTemplate(weaveDataSource);
    }

    @Bean
    @ConditionalOnExpression("'${weave.provider.selections.storage.mode:file}' == 'jdbc' "
            + "|| '${weave.profile.storage.mode:file}' == 'jdbc' "
            + "|| '${weave.audit.events.storage.mode:file}' == 'jdbc' "
            + "|| '${weave.security.device-credentials.storage.mode:memory}' == 'jdbc' "
            + "|| '${weave.migration.evidence.storage.mode:file}' == 'jdbc' "
            + "|| '${weave.matrix.e2ee.storage.mode:memory}' == 'jdbc' "
            + "|| '${weave.chat.storage.mode:memory}' == 'jdbc' "
            + "|| '${weave.identity.invitations.storage-mode:memory}' == 'jdbc' "
            + "|| '${weave.operation-intents.storage.mode:disabled}' == 'jdbc' "
            + "|| '${weave.agent-runtime.storage.mode:disabled}' == 'jdbc'")
    PlatformTransactionManager weaveTransactionManager(DataSource weaveDataSource) {
        return new DataSourceTransactionManager(weaveDataSource);
    }

    @Bean
    @ConditionalOnProperty(name = "weave.operation-intents.storage.mode", havingValue = "jdbc")
    JdbcOperationIntentRepository jdbcOperationIntentRepository(
            JdbcTemplate weaveJdbcTemplate,
            ObjectMapper objectMapper,
            PlatformTransactionManager weaveTransactionManager) {
        return new JdbcOperationIntentRepository(weaveJdbcTemplate, objectMapper, weaveTransactionManager);
    }

    @Bean
    @ConditionalOnProperty(name = "weave.operation-intents.storage.mode", havingValue = "jdbc")
    OperationIntentService operationIntentService(JdbcOperationIntentRepository repository) {
        return new OperationIntentService(repository, java.time.Clock.systemUTC());
    }

    @Bean
    @ConditionalOnProperty(name = "weave.operation-intents.storage.mode", havingValue = "jdbc")
    JdbcProviderBindingRepository jdbcProviderBindingRepository(
            JdbcTemplate weaveJdbcTemplate,
            PlatformTransactionManager weaveTransactionManager) {
        return new JdbcProviderBindingRepository(weaveJdbcTemplate, weaveTransactionManager);
    }

    @Bean
    @ConditionalOnProperty(name = "weave.operation-intents.storage.mode", havingValue = "jdbc")
    JdbcFilesAuthorityRepository jdbcFilesAuthorityRepository(
            JdbcTemplate weaveJdbcTemplate,
            PlatformTransactionManager weaveTransactionManager) {
        return new JdbcFilesAuthorityRepository(weaveJdbcTemplate, weaveTransactionManager);
    }

    @Bean
    @ConditionalOnProperty(name = "weave.operation-intents.storage.mode", havingValue = "jdbc")
    FilesLockService filesLockService(JdbcFilesAuthorityRepository repository) {
        return new FilesLockService(repository, java.time.Clock.systemUTC());
    }

    @Bean
    @ConditionalOnProperty(name = "weave.operation-intents.storage.mode", havingValue = "jdbc")
    FilesMutationIntentService filesMutationIntentService(
            OperationIntentService operationIntentService,
            JdbcProviderBindingRepository providerBindingRepository) {
        return new FilesMutationIntentService(operationIntentService, providerBindingRepository);
    }

    @Bean
    @ConditionalOnProperty(name = "weave.provider-bindings.bootstrap.files.enabled", havingValue = "true")
    FilesProviderBindingBootstrap filesProviderBindingBootstrap(
            JdbcProviderBindingRepository providerBindingRepository,
            ProviderBindingBootstrapProperties properties) {
        return new FilesProviderBindingBootstrap(
                providerBindingRepository, properties, java.time.Clock.systemUTC());
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

    @Bean
    @ConditionalOnProperty(name = "weave.security.device-credentials.storage.mode", havingValue = "jdbc")
    JdbcDeviceCredentialRepository jdbcDeviceCredentialRepository(
            JdbcTemplate weaveJdbcTemplate,
            ObjectMapper objectMapper) {
        return new JdbcDeviceCredentialRepository(weaveJdbcTemplate, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(name = "weave.agent-runtime.storage.mode", havingValue = "jdbc")
    JdbcRuntimeCellRepository jdbcRuntimeCellRepository(JdbcTemplate weaveJdbcTemplate) {
        return new JdbcRuntimeCellRepository(weaveJdbcTemplate);
    }

    @Bean
    @ConditionalOnProperty(name = "weave.agent-runtime.storage.mode", havingValue = "jdbc")
    JdbcRuntimeCommandRepository jdbcRuntimeCommandRepository(JdbcTemplate weaveJdbcTemplate) {
        return new JdbcRuntimeCommandRepository(weaveJdbcTemplate);
    }

    @Bean
    @ConditionalOnProperty(name = "weave.agent-runtime.storage.mode", havingValue = "jdbc")
    JdbcRuntimeProfileRepository jdbcRuntimeProfileRepository(JdbcTemplate weaveJdbcTemplate) {
        return new JdbcRuntimeProfileRepository(weaveJdbcTemplate);
    }

    @Bean
    @ConditionalOnProperty(name = "weave.agent-runtime.storage.mode", havingValue = "jdbc")
    JdbcRuntimeGovernanceRepository jdbcRuntimeGovernanceRepository(JdbcTemplate weaveJdbcTemplate) {
        return new JdbcRuntimeGovernanceRepository(weaveJdbcTemplate);
    }
}

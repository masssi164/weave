package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.files.adapter.JpaFilesAuthorityRepository;
import com.massimotter.weave.backend.files.application.FilesLockService;
import com.massimotter.weave.backend.files.application.FilesMutationIntentService;
import com.massimotter.weave.backend.operation.adapter.JpaOperationIntentRepository;
import com.massimotter.weave.backend.operation.application.OperationIntentService;
import com.massimotter.weave.backend.providerbinding.adapter.JpaProviderBindingRepository;
import com.massimotter.weave.backend.providerbinding.application.FilesProviderBindingBootstrap;
import com.massimotter.weave.backend.providerbinding.application.ProviderBindingBootstrapProperties;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Single production persistence composition root.
 *
 * <p>The deployment profile selects only the database implementation (H2 for host dev,
 * PostgreSQL for test/prod); it never selects an alternate repository authority.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ProviderBindingBootstrapProperties.class)
public class WeavePersistenceConfiguration {

    @Bean
    OperationIntentService operationIntentService(JpaOperationIntentRepository repository) {
        return new OperationIntentService(repository, Clock.systemUTC());
    }

    @Bean
    FilesLockService filesLockService(JpaFilesAuthorityRepository repository) {
        return new FilesLockService(repository, Clock.systemUTC());
    }

    @Bean
    FilesMutationIntentService filesMutationIntentService(
            OperationIntentService operationIntentService,
            JpaProviderBindingRepository providerBindingRepository) {
        return new FilesMutationIntentService(operationIntentService, providerBindingRepository);
    }

    @Bean
    @ConditionalOnProperty(name = "weave.provider-bindings.bootstrap.files.enabled", havingValue = "true")
    FilesProviderBindingBootstrap filesProviderBindingBootstrap(
            JpaProviderBindingRepository providerBindingRepository,
            ProviderBindingBootstrapProperties properties) {
        return new FilesProviderBindingBootstrap(providerBindingRepository, properties, Clock.systemUTC());
    }

}

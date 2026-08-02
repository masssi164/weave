package com.massimotter.weave.backend.config;

import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.audit.JpaAuditEventPublisher;
import com.massimotter.weave.backend.files.adapter.JpaFilesAuthorityRepository;
import com.massimotter.weave.backend.files.application.FilesLockService;
import com.massimotter.weave.backend.files.application.FilesMutationIntentService;
import com.massimotter.weave.backend.identity.invitation.JpaProvisioningIntentRepository;
import com.massimotter.weave.backend.operation.adapter.JpaOperationIntentRepository;
import com.massimotter.weave.backend.operation.application.OperationIntentService;
import com.massimotter.weave.backend.persistence.jpa.audit.AuditEventJpaRepository;
import com.massimotter.weave.backend.persistence.jpa.identity.ProvisioningIntentJpaRepository;
import com.massimotter.weave.backend.persistence.jpa.migration.MigrationRunEvidenceJpaRepository;
import com.massimotter.weave.backend.persistence.jpa.profile.ProductProfileOverrideJpaRepository;
import com.massimotter.weave.backend.persistence.jpa.provider.ProviderSelectionJpaRepository;
import com.massimotter.weave.backend.persistence.jpa.readiness.JpaPersistenceReadinessProbe;
import com.massimotter.weave.backend.persistence.jpa.security.DeviceCredentialJpaRepository;
import com.massimotter.weave.backend.persistence.jpa.schema.SchemaAuthorityJpaRepository;
import com.massimotter.weave.backend.provider.JpaProviderSelectionRepository;
import com.massimotter.weave.backend.providerbinding.adapter.JpaProviderBindingRepository;
import com.massimotter.weave.backend.providerbinding.application.FilesProviderBindingBootstrap;
import com.massimotter.weave.backend.providerbinding.application.ProviderBindingBootstrapProperties;
import com.massimotter.weave.backend.security.device.JpaDeviceCredentialRepository;
import com.massimotter.weave.backend.service.JpaProductProfileOverrideRepository;
import com.massimotter.weave.backend.service.migration.JpaMigrationRunEvidenceRepository;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import java.util.Arrays;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Explicit composition of domain persistence ports.
 *
 * <p>Spring Boot owns the DataSource, Hibernate and transaction manager. No persistence
 * adapter is allowed to create infrastructure beans.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ProviderBindingBootstrapProperties.class)
public class WeavePersistenceConfiguration {

  @Bean
  @ConditionalOnBean(EntityManagerFactory.class)
  JpaPersistenceReadinessProbe jpaPersistenceReadinessProbe(
      EntityManagerFactory entityManagerFactory,
      SchemaAuthorityJpaRepository schemaAuthority,
      Environment environment) {
    Set<String> markerProfiles = Set.of("test", "prod");
    boolean markerRequired =
        markerProfiles.contains(environment.getProperty("weave.deployment.profile", ""))
            || Arrays.stream(environment.getActiveProfiles()).anyMatch(markerProfiles::contains);
    String candidate = environment.getProperty("weave.candidate.commit", "");
    return new JpaPersistenceReadinessProbe(
        entityManagerFactory, schemaAuthority, markerRequired, candidate);
  }

  @Bean
  JpaProviderSelectionRepository jpaProviderSelectionRepository(
      ProviderSelectionJpaRepository repository) {
    return new JpaProviderSelectionRepository(repository);
  }

  @Bean
  JpaProductProfileOverrideRepository jpaProductProfileOverrideRepository(
      ProductProfileOverrideJpaRepository repository, ObjectMapper objectMapper) {
    return new JpaProductProfileOverrideRepository(repository, objectMapper);
  }

  @Bean
  JpaAuditEventPublisher jpaAuditEventPublisher(
      AuditEventJpaRepository repository, ObjectMapper objectMapper) {
    return new JpaAuditEventPublisher(repository, objectMapper);
  }

  @Bean
  JpaDeviceCredentialRepository jpaDeviceCredentialRepository(
      DeviceCredentialJpaRepository repository, ObjectMapper objectMapper) {
    return new JpaDeviceCredentialRepository(repository, objectMapper);
  }

  @Bean
  JpaMigrationRunEvidenceRepository jpaMigrationRunEvidenceRepository(
      MigrationRunEvidenceJpaRepository repository, ObjectMapper objectMapper) {
    return new JpaMigrationRunEvidenceRepository(repository, objectMapper);
  }

  @Bean
  JpaProvisioningIntentRepository jpaProvisioningIntentRepository(
      ProvisioningIntentJpaRepository intents) {
    return new JpaProvisioningIntentRepository(intents);
  }

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
  @ConditionalOnProperty(
      name = "weave.provider-bindings.bootstrap.files.enabled",
      havingValue = "true")
  FilesProviderBindingBootstrap filesProviderBindingBootstrap(
      JpaProviderBindingRepository providerBindingRepository,
      ProviderBindingBootstrapProperties properties) {
    return new FilesProviderBindingBootstrap(
        providerBindingRepository, properties, Clock.systemUTC());
  }

}

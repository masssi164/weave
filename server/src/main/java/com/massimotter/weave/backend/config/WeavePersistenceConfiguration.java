package com.massimotter.weave.backend.config;

import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.agentruntime.adapter.JpaRuntimeCellRepository;
import com.massimotter.weave.backend.agentruntime.adapter.JpaRuntimeCommandRepository;
import com.massimotter.weave.backend.agentruntime.adapter.JpaRuntimeGovernanceRepository;
import com.massimotter.weave.backend.agentruntime.adapter.JpaRuntimeProfileRepository;
import com.massimotter.weave.backend.audit.JpaAuditEventPublisher;
import com.massimotter.weave.backend.identity.invitation.JpaProvisioningIntentRepository;
import com.massimotter.weave.backend.persistence.jpa.agentruntime.RuntimeAuditCorrelationJpaRepository;
import com.massimotter.weave.backend.persistence.jpa.agentruntime.RuntimeCellJpaRepository;
import com.massimotter.weave.backend.persistence.jpa.agentruntime.RuntimeCellPersistenceMapper;
import com.massimotter.weave.backend.persistence.jpa.agentruntime.RuntimeCommandJpaRepository;
import com.massimotter.weave.backend.persistence.jpa.agentruntime.RuntimeEntitlementJpaRepository;
import com.massimotter.weave.backend.persistence.jpa.agentruntime.RuntimeProfileJpaRepository;
import com.massimotter.weave.backend.persistence.jpa.agentruntime.RuntimeProfileSignatureJpaRepository;
import com.massimotter.weave.backend.persistence.jpa.agentruntime.RuntimeRevocationJpaRepository;
import com.massimotter.weave.backend.persistence.jpa.audit.AuditEventJpaRepository;
import com.massimotter.weave.backend.persistence.jpa.identity.KeycloakEventReceiptJpaRepository;
import com.massimotter.weave.backend.persistence.jpa.identity.ProvisioningIntentJpaRepository;
import com.massimotter.weave.backend.persistence.jpa.migration.MigrationRunEvidenceJpaRepository;
import com.massimotter.weave.backend.persistence.jpa.profile.ProductProfileOverrideJpaRepository;
import com.massimotter.weave.backend.persistence.jpa.provider.ProviderSelectionJpaRepository;
import com.massimotter.weave.backend.persistence.jpa.readiness.JpaPersistenceReadinessProbe;
import com.massimotter.weave.backend.persistence.jpa.security.DeviceCredentialJpaRepository;
import com.massimotter.weave.backend.provider.JpaProviderSelectionRepository;
import com.massimotter.weave.backend.security.device.JpaDeviceCredentialRepository;
import com.massimotter.weave.backend.service.JpaProductProfileOverrideRepository;
import com.massimotter.weave.backend.service.migration.JpaMigrationRunEvidenceRepository;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.mapstruct.factory.Mappers;

/**
 * Explicit composition of domain persistence ports.
 *
 * <p>Spring Boot owns the DataSource, Hibernate, Flyway and transaction manager. No persistence
 * adapter is allowed to create infrastructure beans.
 */
@Configuration(proxyBeanMethods = false)
public class WeavePersistenceConfiguration {

  @Bean
  RuntimeCellPersistenceMapper runtimeCellPersistenceMapper() {
    return Mappers.getMapper(RuntimeCellPersistenceMapper.class);
  }

  @Bean
  @ConditionalOnBean(EntityManagerFactory.class)
  JpaPersistenceReadinessProbe jpaPersistenceReadinessProbe(
      EntityManagerFactory entityManagerFactory) {
    return new JpaPersistenceReadinessProbe(entityManagerFactory);
  }

  @Bean
  @ConditionalOnProperty(name = "weave.provider.selections.storage.mode", havingValue = "jpa")
  JpaProviderSelectionRepository jpaProviderSelectionRepository(
      ProviderSelectionJpaRepository repository) {
    return new JpaProviderSelectionRepository(repository);
  }

  @Bean
  @ConditionalOnProperty(name = "weave.profile.storage.mode", havingValue = "jpa")
  JpaProductProfileOverrideRepository jpaProductProfileOverrideRepository(
      ProductProfileOverrideJpaRepository repository, ObjectMapper objectMapper) {
    return new JpaProductProfileOverrideRepository(repository, objectMapper);
  }

  @Bean
  @ConditionalOnProperty(name = "weave.audit.events.storage.mode", havingValue = "jpa")
  JpaAuditEventPublisher jpaAuditEventPublisher(
      AuditEventJpaRepository repository, ObjectMapper objectMapper) {
    return new JpaAuditEventPublisher(repository, objectMapper);
  }

  @Bean
  @ConditionalOnProperty(
      name = "weave.security.device-credentials.storage.mode",
      havingValue = "jpa")
  JpaDeviceCredentialRepository jpaDeviceCredentialRepository(
      DeviceCredentialJpaRepository repository, ObjectMapper objectMapper) {
    return new JpaDeviceCredentialRepository(repository, objectMapper);
  }

  @Bean
  @ConditionalOnProperty(name = "weave.migration.evidence.storage.mode", havingValue = "jpa")
  JpaMigrationRunEvidenceRepository jpaMigrationRunEvidenceRepository(
      MigrationRunEvidenceJpaRepository repository, ObjectMapper objectMapper) {
    return new JpaMigrationRunEvidenceRepository(repository, objectMapper);
  }

  @Bean
  @ConditionalOnProperty(name = "weave.identity.invitations.storage-mode", havingValue = "jpa")
  JpaProvisioningIntentRepository jpaProvisioningIntentRepository(
      ProvisioningIntentJpaRepository intents,
      KeycloakEventReceiptJpaRepository eventReceipts,
      ObjectMapper objectMapper) {
    return new JpaProvisioningIntentRepository(
        intents, eventReceipts, objectMapper, Clock.systemUTC());
  }

  @Bean
  @ConditionalOnProperty(name = "weave.agent-runtime.storage.mode", havingValue = "jpa")
  JpaRuntimeCellRepository jpaRuntimeCellRepository(
      RuntimeCellJpaRepository repository, RuntimeCellPersistenceMapper mapper) {
    return new JpaRuntimeCellRepository(repository, mapper);
  }

  @Bean
  @ConditionalOnProperty(name = "weave.agent-runtime.storage.mode", havingValue = "jpa")
  JpaRuntimeCommandRepository jpaRuntimeCommandRepository(RuntimeCommandJpaRepository repository) {
    return new JpaRuntimeCommandRepository(repository);
  }

  @Bean
  @ConditionalOnProperty(name = "weave.agent-runtime.storage.mode", havingValue = "jpa")
  JpaRuntimeProfileRepository jpaRuntimeProfileRepository(
      RuntimeProfileJpaRepository profiles,
      RuntimeProfileSignatureJpaRepository signatures,
      RuntimeCellJpaRepository cells) {
    return new JpaRuntimeProfileRepository(profiles, signatures, cells);
  }

  @Bean
  @ConditionalOnProperty(name = "weave.agent-runtime.storage.mode", havingValue = "jpa")
  JpaRuntimeGovernanceRepository jpaRuntimeGovernanceRepository(
      RuntimeEntitlementJpaRepository entitlements,
      RuntimeRevocationJpaRepository revocations,
      RuntimeAuditCorrelationJpaRepository correlations) {
    return new JpaRuntimeGovernanceRepository(entitlements, revocations, correlations);
  }
}

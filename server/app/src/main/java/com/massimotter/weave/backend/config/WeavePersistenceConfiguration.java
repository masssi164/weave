package com.massimotter.weave.backend.config;

import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.identity.invitation.JpaProvisioningIntentRepository;
import com.massimotter.weave.backend.operation.application.OperationIntentService;
import com.massimotter.weave.backend.identity.persistence.ProvisioningIntentJpaRepository;
import com.massimotter.weave.backend.portability.persistence.MigrationRunEvidenceJpaRepository;
import com.massimotter.weave.backend.readiness.persistence.JpaPersistenceReadinessProbe;
import com.massimotter.weave.backend.schema.persistence.SchemaAuthorityJpaRepository;
import com.massimotter.weave.backend.service.migration.JpaMigrationRunEvidenceRepository;
import jakarta.persistence.EntityManagerFactory;
import java.util.Arrays;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
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
public class WeavePersistenceConfiguration {

  @Bean
  @ConditionalOnBean(EntityManagerFactory.class)
  JpaPersistenceReadinessProbe jpaPersistenceReadinessProbe(
      EntityManagerFactory entityManagerFactory,
      SchemaAuthorityJpaRepository schemaAuthority,
      Environment environment) {
    Set<String> markerProfiles = Set.of("test", "dogfood", "prod", "e2e");
    boolean markerRequired =
        markerProfiles.contains(environment.getProperty("weave.deployment.profile", ""))
            || Arrays.stream(environment.getActiveProfiles()).anyMatch(markerProfiles::contains);
    String candidate = environment.getProperty("weave.candidate.commit", "");
    return new JpaPersistenceReadinessProbe(
        entityManagerFactory, schemaAuthority, markerRequired, candidate);
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

}

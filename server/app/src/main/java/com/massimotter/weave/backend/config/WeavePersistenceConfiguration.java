package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.readiness.persistence.JpaPersistenceReadinessProbe;
import com.massimotter.weave.backend.schema.persistence.SchemaAuthorityJpaRepository;
import jakarta.persistence.EntityManagerFactory;
import java.util.Arrays;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Composition of the application-wide persistence readiness probe.
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

}

package com.massimotter.weave.backend.portability.autoconfigure;

import com.massimotter.weave.backend.portability.persistence.JpaMigrationRunEvidenceRepository;
import com.massimotter.weave.backend.portability.persistence.JpaTransferRunRepository;
import com.massimotter.weave.backend.portability.persistence.MigrationRunEvidenceJpaRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.ObjectMapper;

/** Reusable owning persistence adapters, independent of servlet and provider construction. */
@AutoConfiguration(afterName = {"com.massimotter.weave.portability.autoconfigure.PortabilityPersistenceAutoConfiguration",
        "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration"})
@Import(JpaTransferRunRepository.class)
public class PortabilityApplicationPersistenceAutoConfiguration {
    @Bean
    JpaMigrationRunEvidenceRepository jpaMigrationRunEvidenceRepository(
            MigrationRunEvidenceJpaRepository repository, ObjectMapper objectMapper) {
        return new JpaMigrationRunEvidenceRepository(repository, objectMapper);
    }
}

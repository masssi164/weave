package com.massimotter.weave.core.autoconfigure;

import com.massimotter.weave.backend.audit.persistence.AuditEventJpaRepository;
import com.massimotter.weave.backend.audit.persistence.JpaAuditEventPublisher;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;

/** Durable shared audit sink for serving; operator entry points compose their explicit sink. */
@AutoConfiguration(after = CorePersistenceAutoConfiguration.class,
        afterName = "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CoreAuditAutoConfiguration {
    @Bean
    JpaAuditEventPublisher jpaAuditEventPublisher(AuditEventJpaRepository repository, ObjectMapper mapper) {
        return new JpaAuditEventPublisher(repository, mapper);
    }
}

package com.massimotter.weave.core.autoconfigure;

import com.massimotter.weave.backend.providerbinding.adapter.JpaProviderBindingRepository;
import com.massimotter.weave.backend.providerbinding.persistence.JpaProviderSelectionRepository;
import com.massimotter.weave.backend.providerbinding.persistence.ProviderSelectionJpaRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/** Shared runtime provider authority; adapter selection and bootstrap belong to domains. */
@AutoConfiguration(after = CorePersistenceAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import(JpaProviderBindingRepository.class)
public class CoreProviderBindingAutoConfiguration {
    @Bean
    JpaProviderSelectionRepository jpaProviderSelectionRepository(ProviderSelectionJpaRepository repository) {
        return new JpaProviderSelectionRepository(repository);
    }
}

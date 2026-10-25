package com.massimotter.weave.core.autoconfigure;

import com.massimotter.weave.backend.identity.OrganizationIdentityContextResolver;
import com.massimotter.weave.backend.identity.profile.application.ProductProfileService;
import com.massimotter.weave.backend.identity.profile.persistence.JpaProductProfileOverrideRepository;
import com.massimotter.weave.backend.identity.profile.persistence.ProductProfileOverrideJpaRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Import;
import com.massimotter.weave.backend.identity.profile.api.IdentityController;
import com.massimotter.weave.backend.identity.profile.api.ProfileController;

/** Product-owned profile values and persistence, independently of optional provider domains. */
@AutoConfiguration(after = {CorePersistenceAutoConfiguration.class, CoreSecurityAutoConfiguration.class},
        afterName = "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import({IdentityController.class, ProfileController.class})
public class CoreProfileAutoConfiguration {
    @Bean
    JpaProductProfileOverrideRepository jpaProductProfileOverrideRepository(
            ProductProfileOverrideJpaRepository repository, ObjectMapper mapper) {
        return new JpaProductProfileOverrideRepository(repository, mapper);
    }

    @Bean
    ProductProfileService productProfileService(JpaProductProfileOverrideRepository repository,
            OrganizationIdentityContextResolver identities) {
        return new ProductProfileService(repository, identities);
    }
}

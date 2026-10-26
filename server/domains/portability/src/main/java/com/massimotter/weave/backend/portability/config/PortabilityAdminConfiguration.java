package com.massimotter.weave.backend.portability.config;

import com.massimotter.weave.backend.admin.domain.AdminApiLink;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Owns the existing protected provider-replacement endpoint's Admin discovery link. */
@Configuration(proxyBeanMethods = false)
public class PortabilityAdminConfiguration {
    @Bean
    AdminApiLink providerReplacementAdminApiLink() {
        return new AdminApiLink("providerReplacementDryRun", "/api/admin/providers/replacements/dry-run");
    }
}

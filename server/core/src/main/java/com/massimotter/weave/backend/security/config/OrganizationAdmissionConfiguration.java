package com.massimotter.weave.backend.security.config;

import com.massimotter.weave.backend.security.DeploymentOrganizationAdmission;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Reusable admission wiring for serving filters and isolated HTTP transport tests. */
@EnableConfigurationProperties({ContextAuthorizationProperties.class, DeploymentOrganizationProperties.class})
public class OrganizationAdmissionConfiguration {
    @Bean
    DeploymentOrganizationAdmission deploymentOrganizationAdmission(
            DeploymentOrganizationProperties primary, ContextAuthorizationProperties context) {
        return new DeploymentOrganizationAdmission(primary, context);
    }
}

package com.massimotter.weave.core.autoconfigure;

import com.massimotter.weave.backend.capability.WorkspaceCapabilityService;
import com.massimotter.weave.backend.capability.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.capability.port.WorkspaceCapabilityContribution;
import com.massimotter.weave.backend.capability.readiness.WorkspaceReleaseReadinessService;
import com.massimotter.weave.backend.identity.OrganizationIdentityContextResolver;
import com.massimotter.weave.backend.security.config.WeaveSecurityProperties;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;

/** Shared serving policy consumes only the declarations supplied by installed domains. */
@AutoConfiguration(after = CoreSecurityAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties({
        WorkspaceCapabilityProperties.class,
        WeaveSecurityProperties.class,
        OAuth2ResourceServerProperties.class
})
public class CoreWorkspaceCapabilityAutoConfiguration {
    @Bean
    WorkspaceCapabilityService workspaceCapabilityService(
            OAuth2ResourceServerProperties resource,
            WeaveSecurityProperties security,
            WorkspaceCapabilityProperties workspace,
            OrganizationIdentityContextResolver identities,
            List<WorkspaceCapabilityContribution> contributions) {
        return new WorkspaceCapabilityService(resource, security, workspace, identities, contributions);
    }

    @Bean
    WorkspaceReleaseReadinessService workspaceReleaseReadinessService(
            OAuth2ResourceServerProperties resource,
            WeaveSecurityProperties security,
            WorkspaceCapabilityService capabilities) {
        return new WorkspaceReleaseReadinessService(resource, security, capabilities);
    }
}

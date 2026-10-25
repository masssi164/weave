package com.massimotter.weave.backend.service.spaces;

import com.massimotter.weave.backend.spaces.application.SpaceRelationshipUserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.security.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.security.config.DeploymentOrganizationProperties;
import com.massimotter.weave.backend.identity.invitation.KeycloakIdentityAdminClient;
import com.massimotter.weave.backend.security.DeploymentOrganizationAdmission;
import com.massimotter.weave.backend.identity.OrganizationIdentityContextResolver;
import com.massimotter.weave.backend.capability.WorkspaceCapabilityService;
import com.massimotter.weave.backend.spaces.port.DefaultSpacePreparationPort;
import com.massimotter.weave.backend.spaces.port.SpaceAccessPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SpaceContributionCompositionTest {
    @Test
    void sharedSpaceServicesComposeWithoutAnyOptionalDomainClassesOrContributions() {
        new ApplicationContextRunner()
                .withClassLoader(new FilteredClassLoader("com.massimotter.weave.backend.calendar",
                        "com.massimotter.weave.backend.files", "com.massimotter.weave.backend.chat",
                        "com.massimotter.weave.backend.matrix"))
                .withUserConfiguration(SpaceRelationshipUserService.class, DefaultSpaceBootstrapService.class)
                .withBean(OrganizationIdentityContextResolver.class, () -> mock(OrganizationIdentityContextResolver.class))
                .withBean(DeploymentOrganizationAdmission.class, () -> mock(DeploymentOrganizationAdmission.class))
                .withBean(ContextAuthorizationProperties.class, () -> new ContextAuthorizationProperties(null, null, null, null, null, null, null, null))
                .withBean(SpaceAccessPort.class, () -> mock(SpaceAccessPort.class))
                .withBean(DefaultSpacePreparationPort.class, () -> mock(DefaultSpacePreparationPort.class))
                .withBean(KeycloakIdentityAdminClient.class, () -> mock(KeycloakIdentityAdminClient.class))
                .withBean(WorkspaceCapabilityService.class, () -> mock(WorkspaceCapabilityService.class))
                .withBean(AuditEventPublisher.class, () -> mock(AuditEventPublisher.class))
                .withBean(OAuth2ResourceServerProperties.class, OAuth2ResourceServerProperties::new)
                .withBean(DeploymentOrganizationProperties.class, () -> new DeploymentOrganizationProperties("org", "weave"))
                .run(context -> assertThat(context).hasSingleBean(SpaceRelationshipUserService.class)
                        .hasSingleBean(DefaultSpaceBootstrapService.class));
    }
}

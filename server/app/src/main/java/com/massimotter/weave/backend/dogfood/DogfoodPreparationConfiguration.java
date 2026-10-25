package com.massimotter.weave.backend.dogfood;

import com.massimotter.weave.backend.capability.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.capability.WorkspaceCapabilityService;
import com.massimotter.weave.backend.security.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.security.workload.config.WorkloadEntitlementProperties;

import com.massimotter.weave.backend.security.config.DeploymentOrganizationProperties;
import com.massimotter.weave.backend.security.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.identity.OrganizationIdentityContextResolver;
import com.massimotter.weave.backend.audit.persistence.JpaAuditEventPublisher;
import com.massimotter.weave.backend.config.*;
import com.massimotter.weave.backend.identity.IdentityOpaqueReferenceCodec;
import com.massimotter.weave.backend.identity.invitation.*;
import com.massimotter.weave.backend.audit.persistence.AuditEventJpaRepository;
import com.massimotter.weave.backend.identity.persistence.ProvisioningIntentJpaRepository;
import com.massimotter.weave.backend.schema.ServingSchemaAuthorityGuard;
import com.massimotter.weave.backend.service.*;
import com.massimotter.weave.backend.spaces.adapter.JpaDogfoodSpacePreparationAdapter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnNotWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import tools.jackson.databind.ObjectMapper;

/** Explicit primary source only: ordinary component scanning must not activate this graph. */
@Configuration(proxyBeanMethods = false)
@Profile("dogfood-operator-cli")
@ConditionalOnNotWebApplication
@EnableAutoConfiguration
@EnableConfigurationProperties({IdentityInvitationProperties.class, ContextAuthorizationProperties.class,
        DeploymentOrganizationProperties.class, OAuth2ResourceServerProperties.class, WeaveSecurityProperties.class,
        WorkspaceCapabilityProperties.class, WorkloadEntitlementProperties.class})
@Import({KeycloakAdminClientConfiguration.class, KeycloakIdentityAdminClient.class, IdentityOpaqueReferenceCodec.class,
        OrganizationIdentityContextResolver.class, WorkspaceCapabilityService.class, MemberInvitationService.class,
        JpaDogfoodSpacePreparationAdapter.class, DogfoodOperatorPlanBinding.class, DogfoodPreparationService.class,
        ServingSchemaAuthorityGuard.class})
class DogfoodPreparationConfiguration {
    @Bean
    JpaAuditEventPublisher auditPublisher(AuditEventJpaRepository repository, ObjectMapper mapper) {
        return new JpaAuditEventPublisher(repository, mapper);
    }
    @Bean
    JpaProvisioningIntentRepository provisioningIntents(ProvisioningIntentJpaRepository repository) {
        return new JpaProvisioningIntentRepository(repository);
    }
}

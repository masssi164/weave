package com.massimotter.weave.backend.dogfood;

import com.massimotter.weave.backend.audit.JpaAuditEventPublisher;
import com.massimotter.weave.backend.config.*;
import com.massimotter.weave.backend.config.keycloak.KeycloakAuthConfig;
import com.massimotter.weave.backend.identity.IdentityOpaqueReferenceCodec;
import com.massimotter.weave.backend.identity.invitation.*;
import com.massimotter.weave.backend.persistence.jpa.audit.AuditEventJpaRepository;
import com.massimotter.weave.backend.persistence.jpa.identity.ProvisioningIntentJpaRepository;
import com.massimotter.weave.backend.schema.ServingSchemaAuthorityGuard;
import com.massimotter.weave.backend.service.*;
import com.massimotter.weave.backend.spaces.adapter.JpaDogfoodSpacePreparationAdapter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnNotWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import tools.jackson.databind.ObjectMapper;

/** Explicit primary source only: ordinary component scanning must not activate this graph. */
@Configuration(proxyBeanMethods = false)
@Profile("dogfood-operator-cli")
@ConditionalOnNotWebApplication
@EnableAutoConfiguration
@EntityScan("com.massimotter.weave.backend")
@EnableJpaRepositories(basePackages = {
        "com.massimotter.weave.backend.persistence.jpa.audit",
        "com.massimotter.weave.backend.persistence.jpa.identity",
        "com.massimotter.weave.backend.persistence.jpa.schema",
        "com.massimotter.weave.backend.spaces.adapter"})
@EnableConfigurationProperties({IdentityInvitationProperties.class, ContextAuthorizationProperties.class,
        DeploymentOrganizationProperties.class, OAuth2ResourceServerProperties.class, WeaveSecurityProperties.class,
        WorkspaceCapabilityProperties.class, AgentRuntimeEntitlementProperties.class})
@Import({KeycloakAuthConfig.class, KeycloakIdentityAdminClient.class, IdentityOpaqueReferenceCodec.class,
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

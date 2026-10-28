package com.massimotter.weave.core.autoconfigure;

import com.massimotter.weave.backend.identity.invitation.application.MemberInvitationService;

import com.massimotter.weave.backend.identity.invitation.api.MemberInvitationController;

import com.massimotter.weave.backend.identity.invitation.api.IdentitySessionController;

import com.massimotter.weave.backend.identity.invitation.api.BootstrapOwnerInvitationController;

import com.massimotter.weave.backend.identity.bootstrap.BootstrapOwnerCredential;

import com.massimotter.weave.backend.identity.application.MemberDisplayNameQuery;

import com.massimotter.weave.backend.identity.IdentityOpaqueReferenceCodec;

import com.massimotter.weave.backend.identity.invitation.KeycloakIdentityAdminClient;
import com.massimotter.weave.backend.identity.keycloak.IdentityInvitationProperties;
import com.massimotter.weave.backend.identity.keycloak.KeycloakAuthConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/** Serving identity administration; operator tooling imports its protected transport explicitly. */
@AutoConfiguration(afterName = {
        "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration",
        "org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration"
})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(IdentityInvitationProperties.class)
@Import({com.massimotter.weave.backend.identity.organization.application.OrganizationBootstrapService.class,
        com.massimotter.weave.backend.identity.organization.api.OrganizationBootstrapController.class,com.massimotter.weave.backend.identity.persistence.IdentityAdminOperationStore.class,
        com.massimotter.weave.backend.identity.member.application.OrganizationMemberAdministrationService.class,
        com.massimotter.weave.backend.identity.member.api.OrganizationMemberAdministrationController.class,
        KeycloakAuthConfig.class, KeycloakIdentityAdminClient.class,
        IdentityOpaqueReferenceCodec.class,
        MemberDisplayNameQuery.class,
        MemberInvitationService.class,
        MemberInvitationController.class,
        IdentitySessionController.class,
        BootstrapOwnerInvitationController.class,
        BootstrapOwnerCredential.class})
public class CoreIdentityAdminAutoConfiguration {
    @org.springframework.context.annotation.Bean
    com.massimotter.weave.backend.identity.bootstrap.IdentityReferenceReadiness identityReferenceReadiness(IdentityOpaqueReferenceCodec references) {
        return new com.massimotter.weave.backend.identity.bootstrap.IdentityReferenceReadiness(references);
    }
}

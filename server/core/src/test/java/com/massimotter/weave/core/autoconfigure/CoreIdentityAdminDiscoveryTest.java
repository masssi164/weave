package com.massimotter.weave.core.autoconfigure;

import com.massimotter.weave.backend.identity.invitation.KeycloakIdentityAdminClient;
import com.massimotter.weave.backend.identity.keycloak.KeycloakAdminClient;
import com.massimotter.weave.backend.security.keycloak.port.KeycloakAdminAccessTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import static org.assertj.core.api.Assertions.assertThat;

class CoreIdentityAdminDiscoveryTest {
    @Test
    void servingImportsDiscoverOneProtectedTransportWithoutReadingCredentials() {
        new WebApplicationContextRunner().withUserConfiguration(Discovery.class)
                .withPropertyValues("spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false")
                .run(context -> assertThat(context).hasNotFailed()
                        .hasSingleBean(com.massimotter.weave.backend.identity.application.MemberDisplayNameQuery.class)
                        .hasSingleBean(com.massimotter.weave.backend.identity.invitation.application.MemberInvitationService.class)
                        .hasSingleBean(com.massimotter.weave.backend.identity.invitation.persistence.JpaProvisioningIntentRepository.class)
                        .hasSingleBean(com.massimotter.weave.backend.identity.invitation.api.MemberInvitationController.class)
                        .hasSingleBean(com.massimotter.weave.backend.identity.invitation.api.IdentitySessionController.class)
                        .hasSingleBean(com.massimotter.weave.backend.identity.profile.api.IdentityController.class)
                        .hasSingleBean(com.massimotter.weave.backend.identity.profile.api.ProfileController.class)
                        .doesNotHaveBean(com.massimotter.weave.backend.identity.bootstrap.BootstrapOwnerCredential.class)
                        .hasSingleBean(KeycloakIdentityAdminClient.class)
                        .hasSingleBean(KeycloakAdminClient.class)
                        .hasSingleBean(KeycloakAdminAccessTokenProvider.class));
    }

    @Test
    void servingReadinessRejectsAnUnavailableReferenceSecret() {
        new WebApplicationContextRunner().withUserConfiguration(Discovery.class)
                .withPropertyValues("spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var readiness = context.getBean("identityReferenceReadiness", org.springframework.boot.ApplicationRunner.class);
                    org.assertj.core.api.Assertions.assertThatThrownBy(() -> readiness.run(null))
                            .isInstanceOf(IllegalStateException.class).hasMessageContaining("not configured");
                });
    }

    @Test
    void nonWebToolingDoesNotConstructServingTransport() {
        new ApplicationContextRunner().withUserConfiguration(Discovery.class)
                .withPropertyValues("spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false")
                .run(context -> assertThat(context).hasNotFailed()
                        .doesNotHaveBean(com.massimotter.weave.backend.identity.application.MemberDisplayNameQuery.class)
                        .doesNotHaveBean(com.massimotter.weave.backend.identity.invitation.application.MemberInvitationService.class)
                        .doesNotHaveBean(com.massimotter.weave.backend.identity.profile.api.ProfileController.class)
                        .doesNotHaveBean(KeycloakIdentityAdminClient.class)
                        .doesNotHaveBean(KeycloakAdminClient.class)
                        .doesNotHaveBean(KeycloakAdminAccessTokenProvider.class));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class Discovery {}
}

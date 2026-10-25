package com.massimotter.weave.core.autoconfigure;

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
@Import({KeycloakAuthConfig.class, KeycloakIdentityAdminClient.class})
public class CoreIdentityAdminAutoConfiguration {}

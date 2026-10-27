package com.massimotter.weave.core.autoconfigure;

import com.massimotter.weave.backend.identity.OrganizationIdentityContextResolver;
import com.massimotter.weave.backend.security.MemberSessionCutoffService;
import com.massimotter.weave.backend.security.MemberSessionRevocationService;
import com.massimotter.weave.backend.security.config.ContextAuthorizationConfiguration;
import com.massimotter.weave.backend.security.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.security.config.OrganizationAdmissionConfiguration;
import com.massimotter.weave.backend.security.port.MemberSessionStateStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/** Shared serving identity, authorization policy and durable member-session revocation. */
@AutoConfiguration(after = CorePersistenceAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(ContextAuthorizationProperties.class)
@Import({ContextAuthorizationConfiguration.class, OrganizationAdmissionConfiguration.class})
public class CoreSecurityAutoConfiguration {
    @Bean
    OrganizationIdentityContextResolver organizationIdentityContextResolver(ContextAuthorizationProperties properties) {
        return OrganizationIdentityContextResolver.configured(properties);
    }

    @Bean
    MemberSessionCutoffService memberSessionCutoffService(
            @Qualifier("memberSessionStateStore") MemberSessionStateStore state,
            OrganizationIdentityContextResolver identities) {
        return new MemberSessionCutoffService(state, identities);
    }

    @Bean
    MemberSessionRevocationService memberSessionRevocationService(
            @Qualifier("memberSessionStateStore") MemberSessionStateStore state,
            OrganizationIdentityContextResolver identities) {
        return new MemberSessionRevocationService(state, identities);
    }
}

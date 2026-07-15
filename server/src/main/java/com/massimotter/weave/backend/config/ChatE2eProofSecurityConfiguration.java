package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.chat.e2e.ChatE2eProofAuthenticationFilter;
import com.massimotter.weave.backend.chat.e2e.ChatE2eProofSecrets;
import com.massimotter.weave.backend.chat.provider.synapse.MatrixApplicationServiceSecrets;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ChatE2eProofProperties.class)
@ConditionalOnProperty(name = "weave.chat.e2e-proof.enabled", havingValue = "true")
public class ChatE2eProofSecurityConfiguration {

    public static final String PATH = "/api/internal/e2e/chat/provider-proof";

    @Bean
    ChatE2eProofSecrets chatE2eProofSecrets(
            ChatE2eProofProperties properties,
            MatrixApplicationServiceSecrets applicationServiceSecrets) {
        properties.requiredRunId();
        ChatE2eProofSecrets secrets = new ChatE2eProofSecrets(properties);
        if (secrets.conflictsWith(applicationServiceSecrets)) {
            throw new IllegalStateException("The Chat E2E proof token must be distinct from Application Service tokens.");
        }
        return secrets;
    }

    @Bean
    ChatE2eProofAuthenticationFilter chatE2eProofAuthenticationFilter(ChatE2eProofSecrets secrets) {
        return new ChatE2eProofAuthenticationFilter(secrets);
    }

    @Bean
    @Order(1)
    SecurityFilterChain chatE2eProofSecurityFilterChain(
            HttpSecurity http,
            ChatE2eProofAuthenticationFilter authenticationFilter) throws Exception {
        return http
                .securityMatcher(PATH, PATH + "/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(authenticationFilter, AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().hasAuthority(ChatE2eProofAuthenticationFilter.AUTHORITY))
                .build();
    }
}

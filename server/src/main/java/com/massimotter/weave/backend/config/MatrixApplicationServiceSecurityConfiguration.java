package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.chat.provider.synapse.MatrixApplicationServiceAuthenticationFilter;
import com.massimotter.weave.backend.chat.provider.synapse.MatrixApplicationServiceSecrets;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "weave.chat.provider", havingValue = ChatRuntimeProperties.MATRIX_SYNAPSE_PROVIDER)
public class MatrixApplicationServiceSecurityConfiguration {

    @Bean
    MatrixApplicationServiceAuthenticationFilter matrixApplicationServiceAuthenticationFilter(
            MatrixApplicationServiceSecrets secrets) {
        return new MatrixApplicationServiceAuthenticationFilter(secrets);
    }

    @Bean
    @Order(2)
    SecurityFilterChain matrixApplicationServiceSecurityFilterChain(
            HttpSecurity http,
            MatrixApplicationServiceAuthenticationFilter authenticationFilter) throws Exception {
        return http
                .securityMatcher("/api/internal/chat/matrix/appservice/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(authenticationFilter, AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().hasAuthority(MatrixApplicationServiceAuthenticationFilter.AUTHORITY))
                .build();
    }
}

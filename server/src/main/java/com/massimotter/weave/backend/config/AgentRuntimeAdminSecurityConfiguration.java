package com.massimotter.weave.backend.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/** Exact interactive-admin OIDC boundary; this chain never grants MCP workload access. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "weave.agent-runtime.storage.mode", havingValue = "jdbc")
public class AgentRuntimeAdminSecurityConfiguration {
    public static final String ADMIN_PATH = "/api/admin/agent-runtimes/**";
    public static final String ADMIN_SCOPE = "agent-runtime.admin";
    public static final String ADMIN_AUTHORITY = "SCOPE_" + ADMIN_SCOPE;

    @Bean
    @Order(-1)
    SecurityFilterChain agentRuntimeAdminSecurityFilterChain(
            HttpSecurity http,
            JwtDecoder jwtDecoder,
            AgentRuntimeErrorResponseWriter errors) throws Exception {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new JwtGrantedAuthoritiesConverter());

        return http
                .securityMatcher(ADMIN_PATH)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> errors.write(
                                request, response, HttpStatus.UNAUTHORIZED,
                                "agent-runtime-admin-unauthorized", "unavailable", false,
                                "A valid Agent Runtime administrator bearer token is required."))
                        .accessDeniedHandler((request, response, exception) -> errors.write(
                                request, response, HttpStatus.FORBIDDEN,
                                "agent-runtime-admin-forbidden", "disabled_by_policy", false,
                                "The authenticated caller is not authorized to administer Agent Runtimes.")))
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().hasAuthority(ADMIN_AUTHORITY))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint((request, response, exception) -> errors.write(
                                request, response, HttpStatus.UNAUTHORIZED,
                                "agent-runtime-admin-unauthorized", "unavailable", false,
                                "A valid Agent Runtime administrator bearer token is required."))
                        .accessDeniedHandler((request, response, exception) -> errors.write(
                                request, response, HttpStatus.FORBIDDEN,
                                "agent-runtime-admin-forbidden", "disabled_by_policy", false,
                                "The authenticated caller is not authorized to administer Agent Runtimes."))
                        .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(converter)))
                .build();
    }
}

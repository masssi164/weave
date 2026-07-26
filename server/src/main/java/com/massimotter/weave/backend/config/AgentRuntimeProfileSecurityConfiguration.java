package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.agentruntime.adapter.AgentRuntimeWorkloadTokenPolicy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class AgentRuntimeProfileSecurityConfiguration {
    public static final String PROFILE_PATH = "/api/v1/agent-runtime/runtime-profiles/**";
    public static final String TRUST_PATH = "/api/v1/agent-runtime/trust/jwks.json";

    @Bean
    @Order(0)
    SecurityFilterChain agentRuntimeProfileSecurityFilterChain(
            HttpSecurity http,
            @Qualifier("agentRuntimeProfileJwtDecoder") JwtDecoder decoder,
            ApiErrorResponseWriter errors) throws Exception {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new JwtGrantedAuthoritiesConverter());

        return http
                .securityMatcher(PROFILE_PATH, TRUST_PATH)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> errors.write(
                                request,
                                response,
                                HttpStatus.UNAUTHORIZED,
                                "agent-runtime-workload-unauthorized",
                                "A valid Agent Runtime cell workload bearer token is required."))
                        .accessDeniedHandler((request, response, exception) -> errors.write(
                                request,
                                response,
                                HttpStatus.FORBIDDEN,
                                "agent-runtime-workload-forbidden",
                                "The workload token does not satisfy the Agent Runtime profile-read contract.")))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, TRUST_PATH).permitAll()
                        .requestMatchers(HttpMethod.GET, PROFILE_PATH)
                        .hasAuthority(AgentRuntimeWorkloadTokenPolicy.PROFILE_READ_AUTHORITY)
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint((request, response, exception) -> errors.write(
                                request,
                                response,
                                HttpStatus.UNAUTHORIZED,
                                "agent-runtime-workload-unauthorized",
                                "A valid Agent Runtime cell workload bearer token is required."))
                        .accessDeniedHandler((request, response, exception) -> errors.write(
                                request,
                                response,
                                HttpStatus.FORBIDDEN,
                                "agent-runtime-workload-forbidden",
                                "The workload token does not satisfy the Agent Runtime profile-read contract."))
                        .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(converter)))
                .build();
    }
}

package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.security.AdminApiAuthorizationManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/** Dedicated Admin API admission; the interactive Admin Console is a distinct OIDC client. */
@Configuration(proxyBeanMethods = false)
public class AdminApiSecurityConfiguration {

    @Bean
    @Order(0)
    SecurityFilterChain adminApiSecurityFilterChain(
            HttpSecurity http,
            @Qualifier("adminApiJwtDecoder") JwtDecoder adminJwtDecoder,
            @Qualifier("jwtAuthenticationConverter") Converter<Jwt, ? extends AbstractAuthenticationToken> converter,
            ApiAuthenticationEntryPoint authenticationEntryPoint,
            ApiAccessDeniedHandler accessDeniedHandler) throws Exception {
        return http
                .securityMatcher("/api/admin/**", "/api/migration/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().access(new AdminApiAuthorizationManager()))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt.decoder(adminJwtDecoder).jwtAuthenticationConverter(converter)))
                .build();
    }
}

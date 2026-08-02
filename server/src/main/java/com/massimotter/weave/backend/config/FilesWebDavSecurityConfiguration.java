package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.security.device.DeviceCredentialAuthenticationFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.expression.WebExpressionAuthorizationManager;

/**
 * Closed Files protocol boundary for direct members and exchanged Weaver workloads.
 *
 * <p>Each token is fully decoded and validated against one exact profile. The general API decoder
 * remains member-only and never accepts an MCP edge token.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "weave.agent-runtime.workload-identity.enabled", havingValue = "true")
public class FilesWebDavSecurityConfiguration {
    private static final WebExpressionAuthorizationManager FILES_ACCESS =
            new WebExpressionAuthorizationManager(
                    "hasAuthority('SCOPE_weave:workspace') or hasAuthority('SCOPE_files.read')");

    @Bean("filesWebDavJwtDecoder")
    JwtDecoder filesWebDavJwtDecoder(
            @Qualifier("jwtDecoder") JwtDecoder memberDecoder,
            @Qualifier("filesMcpWorkloadJwtDecoder") JwtDecoder workloadDecoder) {
        return token -> {
            try {
                return memberDecoder.decode(token);
            } catch (JwtException memberRejected) {
                try {
                    return workloadDecoder.decode(token);
                } catch (JwtException workloadRejected) {
                    workloadRejected.addSuppressed(memberRejected);
                    throw workloadRejected;
                }
            }
        };
    }

    @Bean
    @Order(2)
    SecurityFilterChain filesWebDavSecurityFilterChain(
            HttpSecurity http,
            @Qualifier("filesWebDavJwtDecoder") JwtDecoder decoder,
            Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter,
            ApiAuthenticationEntryPoint authenticationEntryPoint,
            ApiAccessDeniedHandler accessDeniedHandler,
            ObjectProvider<DeviceCredentialAuthenticationFilter> deviceCredentials) throws Exception {
        http
                .securityMatcher("/dav/files", "/dav/files/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().access(FILES_ACCESS))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt
                                .decoder(decoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter)));
        DeviceCredentialAuthenticationFilter deviceFilter = deviceCredentials.getIfAvailable();
        if (deviceFilter != null) {
            http.addFilterBefore(deviceFilter, BearerTokenAuthenticationFilter.class);
        }
        return http.build();
    }
}

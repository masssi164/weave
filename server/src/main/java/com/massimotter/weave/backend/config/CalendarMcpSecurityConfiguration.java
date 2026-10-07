package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.security.DeploymentOrganizationAdmission;
import com.massimotter.weave.backend.security.device.DeviceCredentialAuthenticationFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.expression.WebExpressionAuthorizationManager;

/** Closed Calendar GET boundary for members and exchanged Weaver workloads. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "weave.agent-runtime.workload-identity.enabled", havingValue = "true")
public class CalendarMcpSecurityConfiguration {
    private static final WebExpressionAuthorizationManager CALENDAR_READ =
            new WebExpressionAuthorizationManager(
                    "hasAuthority('SCOPE_weave:workspace') or hasAuthority('SCOPE_calendar.read')");

    @Bean("calendarMcpJwtDecoder")
    JwtDecoder calendarMcpJwtDecoder(
            @Qualifier("jwtDecoder") JwtDecoder memberDecoder,
            @Qualifier("calendarMcpWorkloadJwtDecoder") JwtDecoder workloadDecoder,
            DeploymentOrganizationAdmission organizationAdmission) {
        return token -> {
            Jwt member;
            try {
                member = memberDecoder.decode(token);
            } catch (JwtException memberRejected) {
                try {
                    return workloadDecoder.decode(token);
                } catch (JwtException workloadRejected) {
                    workloadRejected.addSuppressed(memberRejected);
                    throw workloadRejected;
                }
            }
            if (!organizationAdmission.allows(member)) {
                throw new BadJwtException("The member token does not belong to this deployment organization.");
            }
            return member;
        };
    }

    @Bean
    @Order(2)
    SecurityFilterChain calendarMcpSecurityFilterChain(
            HttpSecurity http,
            @Qualifier("calendarMcpJwtDecoder") JwtDecoder decoder,
            Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter,
            ApiAuthenticationEntryPoint authenticationEntryPoint,
            ApiAccessDeniedHandler accessDeniedHandler,
            ObjectProvider<DeviceCredentialAuthenticationFilter> deviceCredentials) throws Exception {
        http
                .securityMatcher("/api/calendar/calendars", "/api/calendar/calendars/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/api/calendar/calendars", "/api/calendar/calendars/**")
                                .access(CALENDAR_READ)
                        .anyRequest().hasAuthority("SCOPE_weave:workspace"))
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

package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.runner.application.RunnerWorkloadIdentityDirectory;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/** Dedicated certificate-only security boundary for the outbound private Runner protocol. */
@Configuration(proxyBeanMethods = false)
public class RunnerControlSecurityConfiguration {

    public static final String ENROLLMENT_PATH = "/runner/v1/enrollments:exchange";
    public static final String CONTROL_PATH = "/runner/v1/**";

    @Bean
    RunnerCertificateFingerprintExtractor runnerCertificateFingerprintExtractor() {
        return new RunnerCertificateFingerprintExtractor();
    }

    @Bean
    RunnerX509UserDetailsService runnerX509UserDetailsService(
            RunnerWorkloadIdentityDirectory identities) {
        return new RunnerX509UserDetailsService(identities, Clock.systemUTC());
    }

    @Bean
    RunnerControlAuthenticationEntryPoint runnerControlAuthenticationEntryPoint(
            ApiErrorResponseWriter errorResponseWriter) {
        return new RunnerControlAuthenticationEntryPoint(errorResponseWriter);
    }

    @Bean
    RunnerControlAccessDeniedHandler runnerControlAccessDeniedHandler(
            ApiErrorResponseWriter errorResponseWriter) {
        return new RunnerControlAccessDeniedHandler(errorResponseWriter);
    }

    /**
     * Enrollment uses Access ID plus a one-time secret and therefore must never fall through to
     * either the X.509 or normal JWT chain. It stays closed until the enrollment authority lands.
     */
    @Bean
    @Order(-1)
    SecurityFilterChain runnerEnrollmentSecurityFilterChain(
            HttpSecurity http,
            RunnerControlAuthenticationEntryPoint authenticationEntryPoint,
            RunnerControlAccessDeniedHandler accessDeniedHandler) throws Exception {
        return http
                .securityMatcher(ENROLLMENT_PATH)
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().denyAll())
                .build();
    }

    @Bean
    @Order(0)
    SecurityFilterChain runnerControlSecurityFilterChain(
            HttpSecurity http,
            RunnerCertificateFingerprintExtractor principalExtractor,
            RunnerX509UserDetailsService userDetailsService,
            RunnerControlAuthenticationEntryPoint authenticationEntryPoint,
            RunnerControlAccessDeniedHandler accessDeniedHandler) throws Exception {
        return http
                .securityMatcher(CONTROL_PATH)
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .x509(x509 -> x509
                        .x509PrincipalExtractor(principalExtractor)
                        .userDetailsService(userDetailsService))
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().hasAuthority(RunnerAuthenticatedPrincipal.AUTHORITY))
                .build();
    }
}

package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.security.device.DeviceCredentialAuthenticationFilter;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.core.annotation.Order;
import org.springframework.security.web.access.expression.WebExpressionAuthorizationManager;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String WORKSPACE_SCOPE_AUTHORITY = "SCOPE_weave:workspace";
    private static final WebExpressionAuthorizationManager MIGRATION_CONTROL_PLANE_ACCESS =
            new WebExpressionAuthorizationManager("hasAuthority('SCOPE_weave:workspace') and (hasRole('OWNER') or hasRole('ADMIN') or hasRole('OPERATOR'))");

    private final ApiAuthenticationEntryPoint authenticationEntryPoint;
    private final ApiAccessDeniedHandler accessDeniedHandler;
    private final DeviceCredentialAuthenticationFilter deviceCredentialAuthenticationFilter;

    public SecurityConfig(ApiAuthenticationEntryPoint authenticationEntryPoint,
            ApiAccessDeniedHandler accessDeniedHandler,
            ObjectProvider<DeviceCredentialAuthenticationFilter> deviceCredentialAuthenticationFilterProvider) {
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.deviceCredentialAuthenticationFilter = deviceCredentialAuthenticationFilterProvider.getIfAvailable();
    }

    @Bean
    @Order(3)
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/info",
                                "/actuator/metrics",
                                "/actuator/metrics/**",
                                "/error").permitAll()
                        .requestMatchers("/api/health/**", "/api/platform/config", "/api/platform/status").permitAll()
                        .requestMatchers("/.well-known/matrix/client", "/.well-known/weave").permitAll()
                        .requestMatchers(ChatE2eProofSecurityConfiguration.PATH).permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/internal/keycloak/events").permitAll()
                        .requestMatchers("/api/migration/**").access(MIGRATION_CONTROL_PLANE_ACCESS)
                        .requestMatchers("/dav/**", "/caldav/**", "/_matrix/client/**").hasAuthority(WORKSPACE_SCOPE_AUTHORITY)
                        .requestMatchers("/api/**").hasAuthority(WORKSPACE_SCOPE_AUTHORITY)
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        if (deviceCredentialAuthenticationFilter != null) {
            http.addFilterBefore(deviceCredentialAuthenticationFilter, BearerTokenAuthenticationFilter.class);
        }

        return http.build();
    }

    @Bean
    HttpFirewall httpFirewall() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setAllowedHttpMethods(List.of(
                "DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT",
                "PROPFIND", "REPORT", "COPY", "MOVE", "MKCOL", "LOCK", "UNLOCK"));
        return firewall;
    }

    @Bean
    Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        // First-party caller binding is carried by Keycloak as azp/client_id.
        // Do not treat that claim as an authority; issuer/audience/client
        // validation establishes the token contract and scopes grant API access.
        converter.setJwtGrantedAuthoritiesConverter(this::extractAuthorities);
        return converter;
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        LinkedHashSet<GrantedAuthority> authorities = new LinkedHashSet<>();

        JwtGrantedAuthoritiesConverter scopeAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        Collection<GrantedAuthority> scopeAuthorities = scopeAuthoritiesConverter.convert(jwt);
        if (scopeAuthorities != null) {
            authorities.addAll(scopeAuthorities);
        }

        for (String role : extractRoles(jwt)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + normalizeAuthoritySegment(role)));
        }

        return List.copyOf(authorities);
    }

    private List<String> extractRoles(Jwt jwt) {
        return extractClientRoles(jwt);
    }

    private List<String> extractClientRoles(Jwt jwt) {
        Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
        if (resourceAccess == null) {
            return List.of();
        }

        if (!(resourceAccess.get("weave-app") instanceof Map<?, ?> clientAccess)) {
            return List.of();
        }
        Object roles = clientAccess.get("roles");
        return roles instanceof Collection<?> roleValues ? stringValues(roleValues) : List.of();
    }

    private List<String> stringValues(Collection<?> values) {
        return values.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .toList();
    }

    private String normalizeAuthoritySegment(String value) {
        return value.toUpperCase(Locale.ROOT).replace('-', '_');
    }
}

package com.massimotter.weave.backend.config;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.expression.WebExpressionAuthorizationManager;

/** Exact interactive-admin OIDC boundary; this chain never grants MCP workload access. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression(
        "'${weave.agent-runtime.workload-identity.enabled:false}' == 'true'"
                + " && '${weave.agent-runtime.policy.enabled:false}' == 'true'"
                + " && '${weave.agent-runtime.profile-signing.enabled:false}' == 'true'"
                + " && '${weave.agent-runtime.state-store.enabled:false}' == 'true'")
public class AgentRuntimeAdminSecurityConfiguration {
    public static final String ADMIN_PATH = "/api/admin/agent-runtimes/**";
    public static final String ADMIN_SCOPE = "agent-runtime.admin";
    public static final String ADMIN_AUTHORITY = "SCOPE_" + ADMIN_SCOPE;
    public static final String CLIENT_ID = "weave-admin-console";
    public static final String OWNER_AUTHORITY = "ROLE_OWNER";
    public static final String ADMIN_ROLE_AUTHORITY = "ROLE_ADMIN";
    public static final String ACCESS_EXPRESSION = "hasAuthority('" + ADMIN_AUTHORITY
            + "') and (hasAuthority('" + OWNER_AUTHORITY + "') or hasAuthority('"
            + ADMIN_ROLE_AUTHORITY + "'))";

    @Bean
    @Order(-1)
    SecurityFilterChain agentRuntimeAdminSecurityFilterChain(
            HttpSecurity http,
            @Qualifier("agentRuntimeAdminJwtDecoder") JwtDecoder jwtDecoder,
            AgentRuntimeErrorResponseWriter errors) throws Exception {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(this::authorities);

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
                        .anyRequest().access(new WebExpressionAuthorizationManager(ACCESS_EXPRESSION)))
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

    private Collection<GrantedAuthority> authorities(Jwt jwt) {
        LinkedHashSet<GrantedAuthority> authorities = new LinkedHashSet<>();
        Collection<GrantedAuthority> scopes = new JwtGrantedAuthoritiesConverter().convert(jwt);
        if (scopes != null) {
            authorities.addAll(scopes);
        }
        roles(jwt).forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
        return List.copyOf(authorities);
    }

    private List<String> roles(Jwt jwt) {
        LinkedHashSet<String> roles = new LinkedHashSet<>();
        Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
        if (resourceAccess != null && resourceAccess.get("weave-app") instanceof Map<?, ?> clientAccess
                && clientAccess.get("roles") instanceof Collection<?> clientRoles) {
            clientRoles.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .map(value -> value.trim().toUpperCase(Locale.ROOT))
                    .filter(value -> value.equals("OWNER") || value.equals("ADMIN"))
                    .forEach(roles::add);
        }
        Object groups = jwt.getClaims().get("groups");
        if (groups instanceof Collection<?> values) {
            values.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .map(String::trim)
                    .map(value -> switch (value) {
                        case "/weave/owners" -> "OWNER";
                        case "/weave/admins" -> "ADMIN";
                        default -> "";
                    })
                    .filter(value -> !value.isEmpty())
                    .forEach(roles::add);
        }
        return List.copyOf(roles);
    }
}

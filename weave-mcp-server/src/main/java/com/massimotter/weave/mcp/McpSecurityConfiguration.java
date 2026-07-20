package com.massimotter.weave.mcp;

import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.util.StringUtils;

@Configuration
class McpSecurityConfiguration {

    @Bean
    SecurityFilterChain mcpSecurityFilterChain(
            HttpSecurity http,
            McpOAuthResourceMetadata resourceMetadata,
            @Value("${weave.oidc.inbound-audience:https://api.weave.test/mcp}") String audience,
            @Value("${weave.oidc.inbound-authorized-party:weave-app}") String authorizedParty,
            @Value("${weave.oidc.inbound-scope:weave:mcp}") String scope) throws Exception {
        AuthorizationManager<RequestAuthorizationContext> memberMcpAccess = (authentication, context) ->
                new AuthorizationDecision(authentication.get() instanceof JwtAuthenticationToken jwtAuthentication
                        && validMemberToken(jwtAuthentication.getToken(), audience, authorizedParty, scope));
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/info",
                                "/error",
                                McpOAuthResourceMetadata.METADATA_PATH).permitAll()
                        .requestMatchers("/mcp", "/mcp/**").access(memberMcpAccess)
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setHeader(
                                    HttpHeaders.WWW_AUTHENTICATE,
                                    "Bearer resource_metadata=\"" + resourceMetadata.metadataUri() + "\"");
                        })
                        .protectedResourceMetadata(metadata -> metadata.protectedResourceMetadataCustomizer(builder -> builder
                                .resource(resourceMetadata.resource().toString())
                                .authorizationServer(resourceMetadata.authorizationServer().toString())
                                .scope("weave:mcp")
                                .bearerMethod("header")
                                .tlsClientCertificateBoundAccessTokens(false)))
                        .jwt(Customizer.withDefaults()))
                .build();
    }

    static boolean validMemberToken(Jwt jwt, String audience, String authorizedParty, String scope) {
        String subject = jwt.getSubject();
        String username = jwt.getClaimAsString("preferred_username");
        String azp = jwt.getClaimAsString("azp");
        String clientId = jwt.getClaimAsString("client_id");
        return StringUtils.hasText(subject)
                && !subject.startsWith("service-account-")
                && (username == null || !username.startsWith("service-account-"))
                && jwt.getAudience().contains(audience)
                && authorizedParty.equals(azp)
                && (clientId == null || authorizedParty.equals(clientId))
                && jwt.getClaimAsString("scope") != null
                && java.util.Arrays.stream(jwt.getClaimAsString("scope").split("\\s+"))
                        .filter(Objects::nonNull)
                        .anyMatch(scope::equals)
                && java.util.Arrays.stream(jwt.getClaimAsString("scope").split("\\s+"))
                        .noneMatch("weave:mcp-backend"::equals);
    }
}

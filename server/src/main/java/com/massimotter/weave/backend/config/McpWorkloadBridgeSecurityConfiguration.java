package com.massimotter.weave.backend.config;

import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

/** Private typed-principal bridge for tokens emitted by the MCP edge's V2 exchange only. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "weave.agent-runtime.workload-identity.enabled", havingValue = "true")
public class McpWorkloadBridgeSecurityConfiguration {
    public static final String PATH = "/api/internal/agent-runtime/mcp-context";

    @Bean
    @Order(-2)
    SecurityFilterChain mcpWorkloadBridgeSecurityFilterChain(
            HttpSecurity http,
            OAuth2ResourceServerProperties resourceServerProperties,
            PlatformContractProperties platform,
            ApiErrorResponseWriter errors) throws Exception {
        String issuer = resourceServerProperties.getJwt().getIssuerUri();
        JwtDecoder decoder = StringUtils.hasText(issuer)
                ? JwtDecoderConfig.configuredRfc9068Decoder(
                        resourceServerProperties,
                        new DelegatingOAuth2TokenValidator<Jwt>(
                                new JwtTimestampValidator(),
                                new JwtIssuerValidator(issuer),
                                JwtDecoderConfig.rfc9068AccessTokenTypeValidator(),
                                JwtDecoderConfig.exactAudienceValidator(Set.of(platform.apiBaseUrl())),
                                JwtDecoderConfig.requiredAuthorizedPartyValidator("weave-mcp-server")))
                : JwtDecoderConfig.configuredRfc9068Decoder(
                        resourceServerProperties,
                        token -> org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success());
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new JwtGrantedAuthoritiesConverter());

        return http
                .securityMatcher(PATH)
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> errors.write(
                                request, response, HttpStatus.UNAUTHORIZED,
                                "mcp-exchanged-token-unauthorized",
                                "A valid downscoped MCP exchange token is required."))
                        .accessDeniedHandler((request, response, exception) -> errors.write(
                                request, response, HttpStatus.FORBIDDEN,
                                "mcp-exchanged-token-forbidden",
                                "The token cannot enter the MCP workload bridge.")))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, PATH).authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint((request, response, exception) -> errors.write(
                                request, response, HttpStatus.UNAUTHORIZED,
                                "mcp-exchanged-token-unauthorized",
                                "A valid downscoped MCP exchange token is required."))
                        .accessDeniedHandler((request, response, exception) -> errors.write(
                                request, response, HttpStatus.FORBIDDEN,
                                "mcp-exchanged-token-forbidden",
                                "The token cannot enter the MCP workload bridge."))
                        .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(converter)))
                .build();
    }
}

package com.massimotter.weave.mcp;

import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

@Configuration
class McpSecurityConfiguration {
  static final String PROTECTED_RESOURCE_METADATA_PATH =
      "/.well-known/oauth-protected-resource/mcp";
  private static final Logger LOGGER = LoggerFactory.getLogger(McpSecurityConfiguration.class);

  @Bean
  JwtDecoder mcpJwtDecoder(
      OAuth2ResourceServerProperties resourceServerProperties, McpWorkloadProperties properties) {
    String issuer = resourceServerProperties.getJwt().getIssuerUri();
    if (!StringUtils.hasText(issuer)) {
      throw new IllegalStateException("The MCP OIDC issuer is required");
    }
    String jwkSetUri = resourceServerProperties.getJwt().getJwkSetUri();
    NimbusJwtDecoder decoder =
        StringUtils.hasText(jwkSetUri)
            ? NimbusJwtDecoder.withJwkSetUri(jwkSetUri).validateType(false).build()
            : NimbusJwtDecoder.withIssuerLocation(issuer).validateType(false).build();
    OAuth2TokenValidator<Jwt> validator =
        new DelegatingOAuth2TokenValidator<>(
            new JwtTimestampValidator(),
            new JwtIssuerValidator(issuer),
            new McpAccessTokenTypeValidator(),
            exactAudienceValidator(
                Set.of(properties.resourceUri().toString(), properties.exchangeClientId())));
    decoder.setJwtValidator(validator);
    return decoder;
  }

  @Bean
  SecurityFilterChain mcpSecurityFilterChain(
      HttpSecurity http,
      McpWorkloadProperties properties,
      McpBackendTokenExchange exchange,
      JsonMapper mapper)
      throws Exception {
    McpBearerChallengeWriter challenges = new McpBearerChallengeWriter(properties, mapper);
    McpRequestAdmissionFilter admission =
        new McpRequestAdmissionFilter(properties, exchange, mapper);
    return http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers(
                        "/actuator/health",
                        "/actuator/health/**",
                        "/actuator/info",
                        "/error",
                        PROTECTED_RESOURCE_METADATA_PATH)
                    .permitAll()
                    .requestMatchers("/mcp", "/mcp/**")
                    .authenticated()
                    .anyRequest()
                    .denyAll())
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .jwt(jwt -> {})
                    .protectedResourceMetadata(
                        metadata ->
                            metadata.protectedResourceMetadataCustomizer(
                                builder ->
                                    builder
                                        .resource(properties.resourceUri().toString())
                                        .authorizationServers(
                                            servers -> {
                                              servers.clear();
                                              servers.add(
                                                  properties.authorizationServer().toString());
                                            })
                                        .scopes(
                                            scopes -> {
                                              scopes.clear();
                                              scopes.addAll(properties.requiredScopes());
                                            })
                                        .bearerMethods(
                                            methods -> {
                                              methods.clear();
                                              methods.add("header");
                                            })
                                        .resourceName("Weave workload-only MCP")
                                        .tlsClientCertificateBoundAccessTokens(false)))
                    .authenticationEntryPoint(
                        (request, response, failure) -> {
                          if (StringUtils.hasText(request.getHeader(HttpHeaders.AUTHORIZATION))) {
                            LOGGER.warn(
                                "MCP bearer validation rejected [failureType={}]",
                                failure.getClass().getSimpleName(),
                                failure);
                          }
                          challenges.unauthorized(response);
                        })
                    .accessDeniedHandler(
                        (request, response, failure) -> challenges.forbidden(response)))
        .addFilterAfter(admission, BearerTokenAuthenticationFilter.class)
        .build();
  }

  static OAuth2TokenValidator<Jwt> exactAudienceValidator(Set<String> expected) {
    return jwt -> {
      if (jwt.getAudience() != null
          && jwt.getAudience().size() == expected.size()
          && Set.copyOf(jwt.getAudience()).equals(expected)) {
        return OAuth2TokenValidatorResult.success();
      }
      return OAuth2TokenValidatorResult.failure(
          new OAuth2Error("invalid_token", "The MCP token audience set is not exact.", null));
    };
  }
}

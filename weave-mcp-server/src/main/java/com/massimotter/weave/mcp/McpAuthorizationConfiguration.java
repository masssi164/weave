package com.massimotter.weave.mcp;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.TokenExchangeOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.endpoint.NimbusJwtClientAuthenticationParametersConverter;
import org.springframework.security.oauth2.client.endpoint.RestClientTokenExchangeTokenResponseClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

@Configuration(proxyBeanMethods = false)
class McpAuthorizationConfiguration {
  static final String BACKEND_EXCHANGE_REGISTRATION = "mcpBackendExchangeClientRegistration";
  static final String BACKEND_EXCHANGE_PROVIDER = "mcpBackendExchangeAuthorizedClientProvider";

  @Bean
  @ConditionalOnMissingBean(McpBackendTokenExchange.class)
  McpBackendTokenExchange mcpBackendTokenExchange(
      McpWorkloadProperties properties,
      @Qualifier(BACKEND_EXCHANGE_REGISTRATION) ClientRegistration registration,
      @Qualifier(BACKEND_EXCHANGE_PROVIDER)
          TokenExchangeOAuth2AuthorizedClientProvider provider) {
    return new SpringSecurityMcpBackendTokenExchange(properties, registration, provider);
  }

  @Bean(BACKEND_EXCHANGE_REGISTRATION)
  ClientRegistration mcpBackendExchangeClientRegistration(McpWorkloadProperties properties) {
    return ClientRegistration.withRegistrationId("weave-backend-token-exchange")
        .clientId(properties.exchangeClientId())
        .clientAuthenticationMethod(ClientAuthenticationMethod.PRIVATE_KEY_JWT)
        .authorizationGrantType(AuthorizationGrantType.TOKEN_EXCHANGE)
        .tokenUri(properties.tokenUri().toString())
        .scope(properties.exchangeScopes())
        .clientName("Weave MCP backend exchange")
        .build();
  }

  @Bean(BACKEND_EXCHANGE_PROVIDER)
  TokenExchangeOAuth2AuthorizedClientProvider mcpBackendExchangeAuthorizedClientProvider(
      McpWorkloadProperties properties) {
    RestClientTokenExchangeTokenResponseClient responseClient =
        new RestClientTokenExchangeTokenResponseClient();
    responseClient.addParametersConverter(
        new NimbusJwtClientAuthenticationParametersConverter<>(
            ignored -> SecretRefJwkLoader.loadPrivateJwk(properties.exchangeClientJwkFile())));

    TokenExchangeOAuth2AuthorizedClientProvider provider =
        new TokenExchangeOAuth2AuthorizedClientProvider();
    provider.setAccessTokenResponseClient(responseClient);
    provider.setSubjectTokenResolver(
        context ->
            context.getAttribute(
                SpringSecurityMcpBackendTokenExchange.SUBJECT_TOKEN_ATTRIBUTE));
    return provider;
  }

  @Bean("mcpWorkloadBoundaryHealthIndicator")
  HealthIndicator mcpWorkloadBoundaryHealthIndicator(McpWorkloadProperties properties) {
    return () -> {
      try {
        SecretRefJwkLoader.loadPrivateJwk(properties.exchangeClientJwkFile());
        return Health.up()
            .withDetail("authorizationPosture", "guarded-fixed-resource")
            .withDetail("tokenExchange", "configured")
            .withDetail("credential", "mounted-secretref")
            .build();
      } catch (McpAdmissionException unavailable) {
        return Health.down()
            .withDetail("authorizationPosture", "blocked")
            .withDetail("tokenExchange", "credential-unavailable")
            .build();
      }
    };
  }
}

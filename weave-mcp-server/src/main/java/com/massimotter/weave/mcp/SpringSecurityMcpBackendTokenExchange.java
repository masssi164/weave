package com.massimotter.weave.mcp;

import java.util.Set;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.client.OAuth2AuthorizationContext;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.TokenExchangeOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

/**
 * Spring Security RFC 8693 adapter.
 *
 * <p>Client authentication uses {@code private_key_jwt}; no client secret, handwritten token
 * request, JWT payload parser, refresh token, or inbound-token relay exists here.
 */
final class SpringSecurityMcpBackendTokenExchange implements McpBackendTokenExchange {
  static final String SUBJECT_TOKEN_ATTRIBUTE =
      SpringSecurityMcpBackendTokenExchange.class.getName() + ".subject-token";

  private final McpWorkloadProperties properties;
  private final ClientRegistration registration;
  private final TokenExchangeOAuth2AuthorizedClientProvider provider;

  SpringSecurityMcpBackendTokenExchange(
      McpWorkloadProperties properties,
      ClientRegistration registration,
      TokenExchangeOAuth2AuthorizedClientProvider provider) {
    this.properties = properties;
    this.registration = java.util.Objects.requireNonNull(registration, "registration");
    this.provider = java.util.Objects.requireNonNull(provider, "provider");
  }

  @Override
  public ExchangedAccessToken exchange(
      McpCellWorkloadPrincipal workload, String subjectToken, Set<String> scopes) {
    if (subjectToken == null
        || subjectToken.isBlank()
        || scopes == null
        || scopes.isEmpty()
        || !workload.scopes().containsAll(scopes)
        || !Set.copyOf(properties.exchangeScopes()).equals(scopes)) {
      throw forbidden();
    }
    try {
      OAuth2AccessToken incoming =
          new OAuth2AccessToken(
              OAuth2AccessToken.TokenType.BEARER,
              subjectToken,
              workload.issuedAt(),
              workload.expiresAt(),
              workload.scopes());
      var principal =
          UsernamePasswordAuthenticationToken.authenticated(
              workload.clientId(), "", java.util.List.of());
      OAuth2AuthorizationContext context =
          OAuth2AuthorizationContext.withClientRegistration(registration)
              .principal(principal)
              .attribute(SUBJECT_TOKEN_ATTRIBUTE, incoming)
              .attribute(OAuth2AuthorizationContext.REQUEST_SCOPE_ATTRIBUTE_NAME, scopes)
              .build();
      OAuth2AuthorizedClient authorized = provider.authorize(context);
      if (authorized == null || authorized.getRefreshToken() != null) {
        throw forbidden();
      }
      OAuth2AccessToken token = authorized.getAccessToken();
      if (!token.getScopes().equals(scopes)
          || token.getExpiresAt() == null
          || token.getIssuedAt() == null
          || token.getExpiresAt().isAfter(workload.expiresAt())
          || token.getExpiresAt().isAfter(token.getIssuedAt().plus(properties.maximumTokenTtl()))) {
        throw forbidden();
      }
      return new ExchangedAccessToken(
          token.getTokenValue(),
          workload.subject(),
          properties.exchangeClientId(),
          Set.of(properties.backendResourceUri().toString()),
          token.getScopes(),
          token.getIssuedAt(),
          token.getExpiresAt());
    } catch (McpAdmissionException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw new McpAdmissionException(McpAdmissionException.Kind.UNAVAILABLE);
    }
  }

  private static McpAdmissionException forbidden() {
    return new McpAdmissionException(McpAdmissionException.Kind.FORBIDDEN);
  }
}

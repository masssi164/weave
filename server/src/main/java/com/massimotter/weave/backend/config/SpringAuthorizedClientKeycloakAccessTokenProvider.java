package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.agentruntime.adapter.KeycloakAdminAccessTokenProvider;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

/**
 * Separately qualified read-only Keycloak entitlement token provider.
 *
 * <p>It reuses the governed Spring registration backed by the existing Identity-owned SecretRef,
 * but uses a distinct service principal and is never injected into the workload DCR adapter.
 */
final class SpringAuthorizedClientKeycloakAccessTokenProvider
    implements KeycloakAdminAccessTokenProvider {
  private static final Duration MAXIMUM_TOKEN_LIFETIME = Duration.ofDays(1);
  private static final String SERVICE_PRINCIPAL = "weave-agent-runtime-entitlement";

  private final String registrationId;
  private final OAuth2AuthorizedClientManager manager;
  private final OAuth2AuthorizedClientService clients;
  private final Authentication principal;
  private final Clock clock;
  private CachedToken cached;

  SpringAuthorizedClientKeycloakAccessTokenProvider(
      String registrationId,
      OAuth2AuthorizedClientManager manager,
      OAuth2AuthorizedClientService clients) {
    this(registrationId, manager, clients, Clock.systemUTC());
  }

  SpringAuthorizedClientKeycloakAccessTokenProvider(
      String registrationId,
      OAuth2AuthorizedClientManager manager,
      OAuth2AuthorizedClientService clients,
      Clock clock) {
    if (registrationId == null || registrationId.isBlank()) {
      throw new IllegalArgumentException("registrationId is required");
    }
    this.registrationId = registrationId;
    this.manager = Objects.requireNonNull(manager, "manager");
    this.clients = Objects.requireNonNull(clients, "clients");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.principal =
        UsernamePasswordAuthenticationToken.authenticated(
            SERVICE_PRINCIPAL, "not-used", List.of());
  }

  @Override
  public synchronized String accessToken() {
    Instant now = clock.instant();
    if (cached != null && cached.refreshAfter().isAfter(now)) {
      return cached.value();
    }
    OAuth2AuthorizedClient authorized =
        manager.authorize(
            OAuth2AuthorizeRequest.withClientRegistrationId(registrationId)
                .principal(principal)
                .build());
    if (authorized == null) {
      throw new RuntimeWorkloadIdentityException(
          "Keycloak entitlement authentication is unavailable");
    }
    OAuth2AccessToken token = authorized.getAccessToken();
    Instant issuedAt = token.getIssuedAt() == null ? now : token.getIssuedAt();
    Instant expiresAt = token.getExpiresAt();
    Duration lifetime =
        expiresAt == null ? Duration.ZERO : Duration.between(issuedAt, expiresAt);
    if (!OAuth2AccessToken.TokenType.BEARER.equals(token.getTokenType())
        || token.getTokenValue().isBlank()
        || expiresAt == null
        || !expiresAt.isAfter(now)
        || lifetime.isZero()
        || lifetime.isNegative()
        || lifetime.compareTo(MAXIMUM_TOKEN_LIFETIME) > 0) {
      throw new RuntimeWorkloadIdentityException(
          "Keycloak returned an invalid entitlement token response");
    }
    long lifetimeSeconds = Math.max(1, Duration.between(now, expiresAt).toSeconds());
    long refreshSkew = Math.min(15, Math.max(1, lifetimeSeconds / 4));
    Instant refreshAfter = expiresAt.minusSeconds(refreshSkew);
    cached =
        new CachedToken(
            token.getTokenValue(), refreshAfter.isAfter(now) ? refreshAfter : now);
    return cached.value();
  }

  @Override
  public synchronized void invalidate(String rejectedToken) {
    if (cached != null && Objects.equals(cached.value(), rejectedToken)) {
      cached = null;
      clients.removeAuthorizedClient(registrationId, principal.getName());
    }
  }

  private record CachedToken(String value, Instant refreshAfter) {}
}

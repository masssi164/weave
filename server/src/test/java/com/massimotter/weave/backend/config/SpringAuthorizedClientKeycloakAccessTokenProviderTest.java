package com.massimotter.weave.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

class SpringAuthorizedClientKeycloakAccessTokenProviderTest {
  private static final Instant NOW = Instant.parse("2026-07-29T20:00:00Z");

  private OAuth2AuthorizedClientManager manager;
  private OAuth2AuthorizedClientService clients;
  private SpringAuthorizedClientKeycloakAccessTokenProvider provider;

  @BeforeEach
  void setUp() {
    manager = mock(OAuth2AuthorizedClientManager.class);
    clients = mock(OAuth2AuthorizedClientService.class);
    provider =
        new SpringAuthorizedClientKeycloakAccessTokenProvider(
            "weave-identity-admin",
            manager,
            clients,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void usesTheGovernedRegistrationWithADistinctServicePrincipalAndCachesTheToken() {
    when(manager.authorize(any())).thenReturn(authorizedClient("entitlement-token"));

    assertThat(provider.accessToken()).isEqualTo("entitlement-token");
    assertThat(provider.accessToken()).isEqualTo("entitlement-token");

    ArgumentCaptor<OAuth2AuthorizeRequest> request =
        ArgumentCaptor.forClass(OAuth2AuthorizeRequest.class);
    verify(manager).authorize(request.capture());
    assertThat(request.getValue().getClientRegistrationId()).isEqualTo("weave-identity-admin");
    assertThat(request.getValue().getPrincipal().getName())
        .isEqualTo("weave-agent-runtime-entitlement");
  }

  @Test
  void removesOnlyTheRejectedQualifiedAuthorizedClient() {
    when(manager.authorize(any())).thenReturn(authorizedClient("entitlement-token"));
    provider.accessToken();

    provider.invalidate("another-token");
    verify(clients, never()).removeAuthorizedClient(any(), any());

    provider.invalidate("entitlement-token");
    verify(clients)
        .removeAuthorizedClient(
            "weave-identity-admin", "weave-agent-runtime-entitlement");
  }

  @Test
  void failsClosedWhenAuthorizationOrTheTokenIsUnavailable() {
    assertThatThrownBy(provider::accessToken)
        .isInstanceOf(RuntimeWorkloadIdentityException.class)
        .hasMessage("Keycloak entitlement authentication is unavailable");

    when(manager.authorize(any())).thenReturn(authorizedClient("expired-token", NOW.minusSeconds(1)));
    assertThatThrownBy(provider::accessToken)
        .isInstanceOf(RuntimeWorkloadIdentityException.class)
        .hasMessage("Keycloak returned an invalid entitlement token response");
  }

  private static OAuth2AuthorizedClient authorizedClient(String tokenValue) {
    return authorizedClient(tokenValue, NOW.plusSeconds(120));
  }

  private static OAuth2AuthorizedClient authorizedClient(
      String tokenValue, Instant expiresAt) {
    ClientRegistration registration =
        ClientRegistration.withRegistrationId("weave-identity-admin")
            .clientId("weave-identity-admin")
            .clientSecret("withheld")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .tokenUri("https://auth.weave.test/realms/weave/protocol/openid-connect/token")
            .build();
    OAuth2AccessToken token =
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            tokenValue,
            expiresAt.minusSeconds(120),
            expiresAt);
    return new OAuth2AuthorizedClient(
        registration, "weave-agent-runtime-entitlement", token);
  }
}

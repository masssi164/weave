package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.agentruntime.adapter.KeycloakAdminAccessTokenProvider;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityException;
import com.massimotter.weave.backend.agentruntime.port.SecretRefAccess;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2ClientCredentialsGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.web.client.RestClient;

/**
 * SecretRef-backed Keycloak administration token boundary implemented with Spring Security's
 * OAuth2 client.
 *
 * <p>The long-lived client secret is resolved only while constructing one grant request. The
 * provider adapter receives only the resulting short-lived bearer and contains no token-endpoint
 * protocol implementation.
 */
public final class SpringSecurityKeycloakAdminAccessTokenProvider
    implements KeycloakAdminAccessTokenProvider {
  private static final Duration MAXIMUM_TOKEN_LIFETIME = Duration.ofDays(1);

  private final Settings settings;
  private final SecretRefAccess secrets;
  private final OAuth2AccessTokenResponseClient<OAuth2ClientCredentialsGrantRequest>
      tokenResponseClient;
  private final Clock clock;
  private volatile CachedToken cached;

  public SpringSecurityKeycloakAdminAccessTokenProvider(
      Settings settings, SecretRefAccess secrets) {
    this(settings, secrets, tokenResponseClient(settings), Clock.systemUTC());
  }

  SpringSecurityKeycloakAdminAccessTokenProvider(
      Settings settings,
      SecretRefAccess secrets,
      OAuth2AccessTokenResponseClient<OAuth2ClientCredentialsGrantRequest> tokenResponseClient,
      Clock clock) {
    this.settings = Objects.requireNonNull(settings, "settings");
    this.secrets = Objects.requireNonNull(secrets, "secrets");
    this.tokenResponseClient = Objects.requireNonNull(tokenResponseClient, "tokenResponseClient");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @Override
  public synchronized String accessToken() {
    Instant now = clock.instant();
    if (cached != null && cached.refreshAfter().isAfter(now)) {
      return cached.value();
    }
    CachedToken refreshed =
        secrets.withSecret(settings.adminCredentialRef(), secret -> requestToken(secret, now));
    cached = refreshed;
    return refreshed.value();
  }

  @Override
  public synchronized void invalidate(String rejectedToken) {
    if (cached != null && Objects.equals(cached.value(), rejectedToken)) {
      cached = null;
    }
  }

  private CachedToken requestToken(byte[] mountedSecret, Instant now) {
    byte[] secret = trimAsciiWhitespace(mountedSecret);
    try {
      ClientRegistration registration =
          ClientRegistration.withRegistrationId(settings.registrationId())
              .clientId(settings.adminClientId())
              .clientSecret(new String(secret, StandardCharsets.UTF_8))
              .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
              .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
              .tokenUri(settings.tokenEndpoint().toString())
              .clientName("Weave Keycloak administration")
              .build();
      OAuth2AccessToken token =
          tokenResponseClient
              .getTokenResponse(new OAuth2ClientCredentialsGrantRequest(registration))
              .getAccessToken();
      Instant issuedAt = token.getIssuedAt() == null ? now : token.getIssuedAt();
      Instant expiresAt = token.getExpiresAt();
      Duration lifetime =
          expiresAt == null ? Duration.ZERO : Duration.between(issuedAt, expiresAt);
      if (!OAuth2AccessToken.TokenType.BEARER.equals(token.getTokenType())
          || token.getTokenValue().isBlank()
          || expiresAt == null
          || !expiresAt.isAfter(now)
          || lifetime.isNegative()
          || lifetime.isZero()
          || lifetime.compareTo(MAXIMUM_TOKEN_LIFETIME) > 0) {
        throw new RuntimeWorkloadIdentityException(
            "Keycloak returned an invalid workload administration token response");
      }
      long lifetimeSeconds = Math.max(1, Duration.between(now, expiresAt).toSeconds());
      long refreshSkew = Math.min(15, Math.max(1, lifetimeSeconds / 4));
      Instant refreshAfter = expiresAt.minusSeconds(refreshSkew);
      return new CachedToken(
          token.getTokenValue(), refreshAfter.isAfter(now) ? refreshAfter : now);
    } catch (RuntimeWorkloadIdentityException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      // Spring's provider response may contain sensitive provider diagnostics. Keep the public
      // exception deliberately cause-free and support-safe.
      throw new RuntimeWorkloadIdentityException(
          "Keycloak workload administration authentication failed [failureType="
              + failure.getClass().getSimpleName()
              + "]");
    } finally {
      Arrays.fill(secret, (byte) 0);
    }
  }

  private static OAuth2AccessTokenResponseClient<OAuth2ClientCredentialsGrantRequest>
      tokenResponseClient(Settings settings) {
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(settings.timeout()).build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(settings.timeout());
    RestClientClientCredentialsTokenResponseClient client =
        new RestClientClientCredentialsTokenResponseClient();
    client.setRestClient(
        RestClient.builder()
            .requestFactory(requestFactory)
            .configureMessageConverters(
                converters -> {
                  converters.addCustomConverter(new FormHttpMessageConverter());
                  converters.addCustomConverter(
                      new OAuth2AccessTokenResponseHttpMessageConverter());
                })
            .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
            .build());
    return client;
  }

  private static byte[] trimAsciiWhitespace(byte[] value) {
    int start = 0;
    int end = value.length;
    while (start < end && whitespace(value[start])) {
      start++;
    }
    while (end > start && whitespace(value[end - 1])) {
      end--;
    }
    if (start == end) {
      throw new RuntimeWorkloadIdentityException(
          "The Keycloak administration SecretRef is empty");
    }
    return Arrays.copyOfRange(value, start, end);
  }

  private static boolean whitespace(byte value) {
    return value == ' ' || value == '\t' || value == '\r' || value == '\n';
  }

  public record Settings(
      URI adminBaseUrl,
      String realm,
      String adminClientId,
      String adminCredentialRef,
      Duration timeout) {
    public Settings {
      if (adminBaseUrl == null
          || adminBaseUrl.getHost() == null
          || !("http".equalsIgnoreCase(adminBaseUrl.getScheme())
              || "https".equalsIgnoreCase(adminBaseUrl.getScheme()))) {
        throw new IllegalArgumentException("adminBaseUrl must be an absolute HTTP(S) URI");
      }
      if (realm == null || realm.isBlank() || realm.contains("/")) {
        throw new IllegalArgumentException("realm is required");
      }
      if (adminClientId == null || adminClientId.isBlank()) {
        throw new IllegalArgumentException("adminClientId is required");
      }
      if (adminCredentialRef == null || !adminCredentialRef.startsWith("credentialref://")) {
        throw new IllegalArgumentException("adminCredentialRef must be a credentialref URI");
      }
      if (timeout == null || timeout.isZero() || timeout.isNegative()) {
        throw new IllegalArgumentException("timeout must be positive");
      }
    }

    URI tokenEndpoint() {
      String encodedRealm =
          URLEncoder.encode(realm, StandardCharsets.UTF_8).replace("+", "%20");
      return adminBaseUrl.resolve(
          "/realms/" + encodedRealm + "/protocol/openid-connect/token");
    }

    String registrationId() {
      return "weave-keycloak-admin-" + Integer.toUnsignedString(adminClientId.hashCode(), 16);
    }
  }

  private record CachedToken(String value, Instant refreshAfter) {}
}

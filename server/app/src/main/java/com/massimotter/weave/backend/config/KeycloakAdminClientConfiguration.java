package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.agentruntime.adapter.KeycloakAdminAccessTokenProvider;
import java.net.http.HttpClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Owns the server-to-Keycloak OAuth2 client boundary.
 *
 * <p>Spring Security signs a short-lived {@code private_key_jwt} assertion with the exact
 * Server-owned SecretRef. The calling request's human principal and the private JWK never cross
 * this boundary.
 */
@Configuration(proxyBeanMethods = false)
public class KeycloakAdminClientConfiguration {
  public static final String KEYCLOAK_ADMIN_REST_CLIENT = "keycloakIdentityAdminRestClient";
  public static final String KEYCLOAK_ADMIN_ACCESS_TOKENS =
      "keycloakIdentityAdminAccessTokenProvider";

  @Bean(KEYCLOAK_ADMIN_ACCESS_TOKENS)
  KeycloakAdminAccessTokenProvider keycloakIdentityAdminAccessTokenProvider(
      IdentityInvitationProperties properties) {
    IdentityInvitationProperties.Keycloak keycloak = properties.keycloak();
    ExactFileSecretRefAccess secrets =
        new ExactFileSecretRefAccess(
            keycloak.adminCredentialRef(), keycloak.adminPrivateJwkFile());
    return new SpringSecurityKeycloakAdminAccessTokenProvider(
        new SpringSecurityKeycloakAdminAccessTokenProvider.Settings(
            keycloak.baseUrl(),
            keycloak.realm(),
            keycloak.adminClientId(),
            keycloak.adminCredentialRef(),
            keycloak.privateKeyJwtAudience(),
            keycloak.timeout()),
        secrets);
  }

  @Bean(KEYCLOAK_ADMIN_REST_CLIENT)
  RestClient keycloakIdentityAdminRestClient(
      RestClient.Builder builder,
      @Qualifier(KEYCLOAK_ADMIN_ACCESS_TOKENS) KeycloakAdminAccessTokenProvider accessTokens,
      IdentityInvitationProperties properties) {
    IdentityInvitationProperties.Keycloak keycloak = properties.keycloak();

    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(keycloak.timeout()).build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(keycloak.timeout());

    return builder
        .clone()
        .baseUrl(keycloak.baseUrl().toString())
        .requestFactory(requestFactory)
        .requestInterceptor(
            (request, body, execution) -> {
              String token = accessTokens.accessToken();
              request.getHeaders().setBearerAuth(token);
              var response = execution.execute(request, body);
              if (response.getStatusCode().value() == 401) {
                accessTokens.invalidate(token);
              }
              return response;
            })
        .build();
  }
}

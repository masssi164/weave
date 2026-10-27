package com.massimotter.weave.backend.identity.keycloak;

import com.massimotter.weave.backend.security.keycloak.port.KeycloakAdminAccessTokenProvider;
import com.massimotter.weave.backend.identity.keycloak.IdentityInvitationProperties;
import com.massimotter.weave.backend.identity.invitation.KeycloakIdentityAdminClient.KeycloakAdminException;
import com.massimotter.weave.backend.identity.invitation.KeycloakIdentityAdminOperationPolicy;
import com.massimotter.weave.backend.security.secret.adapter.ExactFileSecretRefAccess;
import com.massimotter.weave.backend.security.keycloak.provider.SpringSecurityKeycloakAdminAccessTokenProvider;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;
import org.springframework.web.util.DefaultUriBuilderFactory;

/**
 * Owns the server-to-Keycloak OAuth2 client boundary.
 *
 * <p>Spring Security signs a short-lived {@code private_key_jwt} assertion with the exact
 * Server-owned SecretRef. The calling request's human principal and the private JWK never cross
 * this boundary.
 */
@ImportHttpServices(group = KeycloakAuthConfig.KEYCLOAK_ADMIN_GROUP, types = KeycloakAdminClient.class)
public class KeycloakAuthConfig {
  static final String KEYCLOAK_ADMIN_GROUP = "keycloak-admin";
  public static final String KEYCLOAK_ADMIN_ACCESS_TOKENS =
      "keycloakIdentityAdminAccessTokenProvider";

  @Bean(KEYCLOAK_ADMIN_ACCESS_TOKENS)
  KeycloakAdminAccessTokenProvider keycloakIdentityAdminAccessTokenProvider(
      IdentityInvitationProperties properties) {
    var keycloak = properties.keycloak();
    var secrets =
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

  @Bean
  RestClientHttpServiceGroupConfigurer keycloakAdminHttpServiceGroup(
      @Qualifier(KEYCLOAK_ADMIN_ACCESS_TOKENS) KeycloakAdminAccessTokenProvider accessTokens,
      IdentityInvitationProperties properties) {
    var keycloak = properties.keycloak();
    return groups ->
        groups
            .filterByName(KEYCLOAK_ADMIN_GROUP)
            .forEachClient(
                (group, builder) -> {
                  configurePolicy(builder, keycloak.baseUrl());
                  configureTransport(builder, accessTokens, keycloak);
                });
  }

  /** Group-local route and exact-status policy; kept separate so transport tests stay focused. */
  static void configurePolicy(RestClient.Builder builder, URI baseUrl) {
    var uriFactory = new DefaultUriBuilderFactory(baseUrl.toString());
    uriFactory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.URI_COMPONENT);
    builder
        .uriBuilderFactory(uriFactory)
        .requestInterceptor(
            (request, body, execution) -> {
              String path = relativePath(baseUrl, request.getURI());
              boolean fixture =
                  Boolean.TRUE.equals(
                      request.getAttributes().get(KeycloakAdminClient.MANAGED_FIXTURE_ATTRIBUTE));
              var allowed =
                  KeycloakIdentityAdminOperationPolicy.admit(request.getMethod(), path, fixture);
              var response = execution.execute(request, body);
              int status = response.getStatusCode().value();
              if (status != allowed.expectedStatus()) {
                response.close();
                throw new KeycloakAdminException(
                    status, "Keycloak administration operation failed", allowed.operation());
              }
              return response;
            });
  }

  /** Group-local timeout and service credential policy; no token is resolved during startup. */
  static void configureTransport(
      RestClient.Builder builder,
      KeycloakAdminAccessTokenProvider accessTokens,
      IdentityInvitationProperties.Keycloak keycloak) {
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(keycloak.timeout()).build();
    var requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(keycloak.timeout());
    builder
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
            });
  }

  private static String relativePath(URI baseUrl, URI target) {
    String prefix =
        baseUrl.getRawPath() == null ? "" : baseUrl.getRawPath().replaceAll("/+$", "");
    if (!Objects.equals(baseUrl.getScheme(), target.getScheme())
        || !Objects.equals(baseUrl.getRawAuthority(), target.getRawAuthority())
        || !target.getRawPath().startsWith(prefix + "/")) {
      throw new IllegalArgumentException(
          "Keycloak identity administration target is outside its boundary");
    }
    return target.getRawPath().substring(prefix.length())
        + (target.getRawQuery() == null ? "" : "?" + target.getRawQuery());
  }
}

package com.massimotter.weave.backend.config.keycloak;

import com.massimotter.weave.backend.agentruntime.adapter.KeycloakAdminAccessTokenProvider;
import com.massimotter.weave.backend.config.IdentityInvitationProperties;
import com.massimotter.weave.backend.identity.invitation.KeycloakIdentityAdminClient.KeycloakAdminException;
import com.massimotter.weave.backend.identity.invitation.KeycloakIdentityAdminOperationPolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import org.springframework.web.util.DefaultUriBuilderFactory;

/**
 * Owns the server-to-Keycloak OAuth2 client boundary.
 *
 * <p>Spring Security signs a short-lived {@code private_key_jwt} assertion with the exact
 * Server-owned SecretRef. The calling request's human principal and the private JWK never cross
 * this boundary.
 */
@Configuration(proxyBeanMethods = false)
public class KeycloakAuthConfig {
  public static final String KEYCLOAK_ADMIN_REST_CLIENT = "keycloakIdentityAdminRestClient";
  public static final String KEYCLOAK_ADMIN_ACCESS_TOKENS =
      "keycloakIdentityAdminAccessTokenProvider";

  @Bean(KEYCLOAK_ADMIN_ACCESS_TOKENS)
  KeycloakAdminAccessTokenProvider keycloakIdentityAdminAccessTokenProvider(
      IdentityInvitationProperties properties) {
    KeycloakAdminProperties keycloak = KeycloakAdminProperties.from(properties);
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
    KeycloakAdminProperties keycloak = KeycloakAdminProperties.from(properties);

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

  @Bean
  KeycloakAdminClient keycloakAdminClient(
      @Qualifier(KEYCLOAK_ADMIN_REST_CLIENT) RestClient restClient,
      IdentityInvitationProperties properties) {
    return proxy(restClient, properties.keycloak().baseUrl());
  }

  /** Preserves the configured context path and checks admission/status before body conversion. */
  public static KeycloakAdminClient proxy(RestClient restClient, URI baseUrl) {
    var uriFactory = new DefaultUriBuilderFactory(baseUrl.toString());
    uriFactory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.URI_COMPONENT);
    RestClient policyAwareClient = restClient.mutate()
        .uriBuilderFactory(uriFactory)
        .requestInterceptors(interceptors -> interceptors.add(0, (request, body, execution) -> {
          String path = relativePath(baseUrl, request.getURI());
          boolean fixture = Boolean.TRUE.equals(
              request.getAttributes().get(KeycloakAdminClient.MANAGED_FIXTURE_ATTRIBUTE));
          var allowed = KeycloakIdentityAdminOperationPolicy.admit(request.getMethod(), path, fixture);
          var response = execution.execute(request, body);
          int status = response.getStatusCode().value();
          if (status != allowed.expectedStatus()) {
            response.close();
            throw new KeycloakAdminException(
                status, "Keycloak administration operation failed", allowed.operation());
          }
          return response;
        }))
        .build();
    return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(policyAwareClient))
        .build()
        .createClient(KeycloakAdminClient.class);
  }

  private static String relativePath(URI baseUrl, URI target) {
    String prefix = baseUrl.getRawPath() == null ? "" : baseUrl.getRawPath().replaceAll("/+$", "");
    if (!Objects.equals(baseUrl.getScheme(), target.getScheme())
        || !Objects.equals(baseUrl.getRawAuthority(), target.getRawAuthority())
        || !target.getRawPath().startsWith(prefix + "/")) {
      throw new IllegalArgumentException("Keycloak identity administration target is outside its boundary");
    }
    return target.getRawPath().substring(prefix.length())
        + (target.getRawQuery() == null ? "" : "?" + target.getRawQuery());
  }
}

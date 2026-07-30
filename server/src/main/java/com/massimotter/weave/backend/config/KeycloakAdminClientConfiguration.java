package com.massimotter.weave.backend.config;

import static org.springframework.security.oauth2.client.web.client.RequestAttributeClientRegistrationIdResolver.clientRegistrationId;
import static org.springframework.security.oauth2.client.web.client.RequestAttributePrincipalResolver.principal;

import java.net.http.HttpClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.security.oauth2.client.web.client.RequestAttributeClientRegistrationIdResolver;
import org.springframework.security.oauth2.client.web.client.RequestAttributePrincipalResolver;
import org.springframework.web.client.RestClient;

/**
 * Owns the server-to-Keycloak OAuth2 client boundary.
 *
 * <p>Spring Security obtains and caches the service-account token. The calling request's human
 * principal is deliberately not used as the OAuth2 authorized-client principal.
 */
@Configuration(proxyBeanMethods = false)
public class KeycloakAdminClientConfiguration {
  public static final String KEYCLOAK_ADMIN_REST_CLIENT = "keycloakIdentityAdminRestClient";
  private static final String SERVICE_PRINCIPAL = "weave-server";

  @Bean(KEYCLOAK_ADMIN_REST_CLIENT)
  RestClient keycloakIdentityAdminRestClient(
      RestClient.Builder builder,
      ObjectProvider<OAuth2AuthorizedClientManager> authorizedClientManagerProvider,
      IdentityInvitationProperties properties) {
    IdentityInvitationProperties.Keycloak keycloak = properties.keycloak();

    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(keycloak.timeout()).build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(keycloak.timeout());

    RestClient.Builder keycloakClient =
        builder.clone().baseUrl(keycloak.baseUrl().toString()).requestFactory(requestFactory);
    OAuth2AuthorizedClientManager authorizedClientManager =
        authorizedClientManagerProvider.getIfAvailable();
    if (authorizedClientManager == null) {
      return keycloakClient
          .requestInterceptor(
              (request, body, execution) -> {
                throw new IllegalStateException(
                    "Keycloak administration is blocked because OAuth2 client "
                        + "configuration is unavailable");
              })
          .build();
    }

    OAuth2ClientHttpRequestInterceptor oauth =
        new OAuth2ClientHttpRequestInterceptor(authorizedClientManager);
    oauth.setClientRegistrationIdResolver(new RequestAttributeClientRegistrationIdResolver());
    oauth.setPrincipalResolver(new RequestAttributePrincipalResolver());
    return keycloakClient
        .requestInterceptor(oauth)
        .defaultRequest(
            request -> {
              request.attributes(clientRegistrationId(keycloak.oauthRegistrationId()));
              request.attributes(principal(SERVICE_PRINCIPAL));
            })
        .build();
  }
}

package com.massimotter.weave.backend.identity.keycloak;

import java.net.URI;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/** Test-only HTTP proxy construction; production clients are registered by Boot service groups. */
public final class KeycloakAdminTestClient {
  private KeycloakAdminTestClient() {}

  public static KeycloakAdminClient create(RestClient.Builder builder, URI baseUrl) {
    KeycloakAuthConfig.configurePolicy(builder, baseUrl);
    return proxy(builder.build());
  }

  public static KeycloakAdminClient proxy(RestClient restClient) {
    return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
        .build()
        .createClient(KeycloakAdminClient.class);
  }
}

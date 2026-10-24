package com.massimotter.weave.backend.config.openproject;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/** OpenProject is optional and instantiated only for an explicitly enabled Boards provider. */
public final class OpenProjectAuthConfig {
  private OpenProjectAuthConfig() {}

  public static OpenProjectClient client(RestClient.Builder builder) {
    RestClient transport = (builder == null ? RestClient.builder() : builder).build();
    return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(transport))
        .build().createClient(OpenProjectClient.class);
  }
}

package com.massimotter.weave.e2e;

import com.massimotter.weave.userapi.api.IdentityApi;
import com.massimotter.weave.userapi.invoker.ApiClient;
import com.massimotter.weave.userapi.invoker.ApiException;
import com.massimotter.weave.userapi.model.AuthenticatedUserResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/** TLS-bound consumer of the generated server-owned User contract. */
final class GeneratedUserApi {
  private final IdentityApi identity;

  GeneratedUserApi(URI apiOrigin, Path caCertificate) {
    this(
        apiOrigin,
        HttpClient.newBuilder()
            .sslContext(JsonHttpClient.sslContext(caCertificate))
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER));
  }

  GeneratedUserApi(URI apiOrigin, HttpClient.Builder httpClientBuilder) {
    ApiClient client =
        new ApiClient()
            .setHttpClientBuilder(httpClientBuilder)
            .setReadTimeout(Duration.ofSeconds(30));
    client.updateBaseUri(apiOrigin.getScheme() + "://" + apiOrigin.getRawAuthority());
    identity = new IdentityApi(client);
  }

  AuthenticatedUserResponse authenticatedUser(String bearer) {
    if (bearer == null || bearer.isBlank()) {
      throw new IllegalArgumentException("User access token is unavailable");
    }
    try {
      AuthenticatedUserResponse result = identity.me(Map.of("Authorization", "Bearer " + bearer));
      if (result == null) {
        throw new ProductFlowException("authenticated member surface returned an empty response");
      }
      return result;
    } catch (ApiException failure) {
      // Generated exceptions include raw bodies. Keep E2E diagnostics support-safe.
      throw new ProductFlowException(
          "authenticated member surface failed with HTTP " + failure.getCode());
    }
  }
}

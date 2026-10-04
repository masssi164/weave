package com.massimotter.weave.e2e;

import com.massimotter.weave.adminapi.api.AdminControlPlaneApi;
import com.massimotter.weave.adminapi.invoker.ApiClient;
import com.massimotter.weave.adminapi.invoker.ApiException;
import com.massimotter.weave.adminapi.model.AdminControlPlaneResponse;
import com.massimotter.weave.adminapi.model.ProviderSelectionRequest;
import com.massimotter.weave.adminapi.model.ProviderSelectionResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/** TLS-bound consumer of the generated server-owned Admin contract. */
final class GeneratedAdminApi {
  private static final ObjectMapper ERROR_MAPPER = new ObjectMapper();
  private final AdminControlPlaneApi controlPlane;

  GeneratedAdminApi(URI apiOrigin, Path caCertificate) {
    this(
        apiOrigin,
        HttpClient.newBuilder()
            .sslContext(JsonHttpClient.sslContext(caCertificate))
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER));
  }

  GeneratedAdminApi(URI apiOrigin, HttpClient.Builder httpClientBuilder) {
    ApiClient client =
        new ApiClient()
            .setHttpClientBuilder(httpClientBuilder)
            .setReadTimeout(Duration.ofSeconds(30));
    client.updateBaseUri(apiOrigin.getScheme() + "://" + apiOrigin.getRawAuthority());
    controlPlane = new AdminControlPlaneApi(client);
  }

  AdminControlPlaneResponse controlPlane(String bearer) {
    try {
      AdminControlPlaneResponse result =
          controlPlane.getAdminControlPlane(authHeaders(bearer));
      if (result == null) {
        throw new ProductFlowException("Admin control plane returned an empty response");
      }
      return result;
    } catch (ApiException failure) {
      // Generated exceptions include raw bodies. Keep E2E diagnostics support-safe.
      throw new ProductFlowException("Admin control plane failed with HTTP " + failure.getCode()
          + supportSafeErrorCode(failure));
    }
  }

  private static String supportSafeErrorCode(ApiException failure) {
    String body = failure.getResponseBody();
    if (body == null || body.length() > 8192) {
      return "";
    }
    try {
      JsonNode code = ERROR_MAPPER.readTree(body).path("code");
      String value = code.isTextual() ? code.textValue() : "";
      return value.matches("[a-z0-9-]{1,80}") ? " (code=" + value + ")" : "";
    } catch (Exception ignored) {
      return "";
    }
  }

  ProviderSelectionResponse selectProvider(String bearer, ProviderSelectionRequest request) {
    try {
      ProviderSelectionResponse result = controlPlane.selectProvider(request, authHeaders(bearer));
      if (result == null) {
        throw new ProductFlowException("Admin provider selection returned an empty response");
      }
      return result;
    } catch (ApiException failure) {
      throw new ProductFlowException("Admin provider selection failed with HTTP " + failure.getCode());
    }
  }

  private Map<String, String> authHeaders(String bearer) {
    if (bearer == null || bearer.isBlank()) {
      throw new IllegalArgumentException("Admin access token is unavailable");
    }
    return Map.of("Authorization", "Bearer " + bearer);
  }
}

package com.massimotter.weave.e2e;

import com.massimotter.weave.adminapi.api.AdminControlPlaneApi;
import com.massimotter.weave.adminapi.api.AdminWorkspaceApi;
import com.massimotter.weave.adminapi.api.ProviderRegistryApi;
import com.massimotter.weave.adminapi.invoker.ApiClient;
import com.massimotter.weave.adminapi.invoker.ApiException;
import com.massimotter.weave.adminapi.model.AdminControlPlaneResponse;
import com.massimotter.weave.adminapi.model.ProviderSelectionRequest;
import com.massimotter.weave.adminapi.model.ProviderSelectionResponse;
import com.massimotter.weave.adminapi.model.ProviderRegistryResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** TLS-bound consumer of the generated server-owned Admin contract. */
final class GeneratedAdminApi {
  private static final ObjectMapper ERROR_MAPPER = new ObjectMapper();
  private final AdminControlPlaneApi controlPlane;
  private final ProviderRegistryApi providers;
  private final AdminWorkspaceApi workspace;

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
    providers = new ProviderRegistryApi(client);
    workspace = new AdminWorkspaceApi(client);
  }

  void verifyWorkspaceDiagnostics(String bearer) {
    try {
      var policy = workspace.capabilityPolicy(authHeaders(bearer));
      if (policy == null || !Boolean.TRUE.equals(policy.getSupportSafe())
          || !Boolean.TRUE.equals(policy.getDenyByDefault())
          || !policy.getGrantedCapabilities().contains("admin_control_plane.readiness_read")) {
        throw new ProductFlowException("Admin workspace policy did not match current authority");
      }
      var readiness = workspace.releaseReadiness(authHeaders(bearer));
      if (readiness == null || readiness.getReadiness() == null || readiness.getChecks() == null
          || !readiness.getChecks().stream().map(value -> value.getKey()).collect(Collectors.toSet())
              .equals(Set.of("auth-contract", "chat", "files"))) {
        throw new ProductFlowException("Admin workspace configuration checks did not match");
      }
    } catch (ApiException failure) {
      throw new ProductFlowException("Admin workspace diagnostics failed with HTTP " + failure.getCode()
          + supportSafeErrorCode(failure));
    }
  }

  ProviderRegistryResponse providerStatus(String bearer) {
    try {
      ProviderRegistryResponse result = providers.status(authHeaders(bearer));
      if (result == null) {
        throw new ProductFlowException("Admin provider status returned an empty response");
      }
      return result;
    } catch (ApiException failure) {
      throw new ProductFlowException("Admin provider status failed with HTTP " + failure.getCode()
          + supportSafeErrorCode(failure));
    }
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

package com.massimotter.weave.e2e;

import com.massimotter.weave.userapi.api.ProfileApi;
import com.massimotter.weave.userapi.api.WorkspaceApi;
import com.massimotter.weave.userapi.invoker.ApiClient;
import com.massimotter.weave.userapi.invoker.ApiException;
import com.massimotter.weave.userapi.model.ProductProfileResponse;
import com.massimotter.weave.userapi.model.UpdateProductProfileRequest;
import com.massimotter.weave.userapi.model.WorkspaceHomeResponse;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

/** Typed User API transport for the independent profile and Home E2E assertions. */
final class GeneratedProfileHomeApi {
  private final ProfileApi profile;
  private final WorkspaceApi workspace;

  GeneratedProfileHomeApi(ProductFlowEnvironment environment) {
    ApiClient client =
        new ApiClient()
            .setHttpClientBuilder(
                HttpClient.newBuilder()
                    .sslContext(JsonHttpClient.sslContext(environment.caCertificate()))
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NEVER))
            .setReadTimeout(Duration.ofSeconds(30));
    client.updateBaseUri(
        environment.apiOrigin().getScheme() + "://" + environment.apiOrigin().getRawAuthority());
    profile = new ProfileApi(client);
    workspace = new WorkspaceApi(client);
  }

  ProductProfileResponse updateProfile(String token, String displayName, String locale) {
    UpdateProductProfileRequest request =
        new UpdateProductProfileRequest()
            .displayName(displayName)
            .locale(locale)
            .timezone("Europe/Berlin")
            .profileVisibility(UpdateProductProfileRequest.ProfileVisibilityEnum.PRIVATE)
            .accessibilityPreferences(Map.of("reducedMotion", "true"));
    try {
      ProductProfileResponse result = profile.updateProductProfile(request, bearer(token));
      if (result == null) {
        throw new ProductFlowException("Generated profile update returned no profile");
      }
      return result;
    } catch (ApiException failure) {
      throw new ProductFlowException("Generated profile update failed with HTTP " + failure.getCode());
    }
  }

  ProductProfileResponse readProfile(String token) {
    try {
      ProductProfileResponse result = profile.getProductProfile(bearer(token));
      if (result == null) {
        throw new ProductFlowException("Generated profile read returned no profile");
      }
      return result;
    } catch (ApiException failure) {
      throw new ProductFlowException("Generated profile read failed with HTTP " + failure.getCode());
    }
  }

  WorkspaceHomeResponse home(String token) {
    try {
      WorkspaceHomeResponse result = workspace.home(bearer(token));
      if (result == null) {
        throw new ProductFlowException("Generated Home read returned no snapshot");
      }
      return result;
    } catch (ApiException failure) {
      throw new ProductFlowException("Generated Home read failed with HTTP " + failure.getCode());
    }
  }

  private static Map<String, String> bearer(String token) {
    if (token == null || token.isBlank()) {
      throw new IllegalArgumentException("User access token is unavailable");
    }
    return Map.of("Authorization", "Bearer " + token);
  }
}

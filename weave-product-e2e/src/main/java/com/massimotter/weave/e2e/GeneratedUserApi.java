package com.massimotter.weave.e2e;

import com.massimotter.weave.userapi.api.ChatDomainApi;
import com.massimotter.weave.userapi.api.IdentityApi;
import com.massimotter.weave.userapi.api.IdentitySessionApi;
import com.massimotter.weave.userapi.api.PlatformApi;
import com.massimotter.weave.userapi.api.ProfileApi;
import com.massimotter.weave.userapi.invoker.ApiClient;
import com.massimotter.weave.userapi.invoker.ApiException;
import com.massimotter.weave.userapi.model.AuthenticatedUserResponse;
import com.massimotter.weave.userapi.model.ChatReadiness;
import com.massimotter.weave.userapi.model.IdentitySessionReconcileResponse;
import com.massimotter.weave.userapi.model.PlatformConfigResponse;
import com.massimotter.weave.userapi.model.ProfileReadinessResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/** TLS-bound consumer of the generated server-owned User contract. */
final class GeneratedUserApi {
  private final IdentityApi identity;
  private final IdentitySessionApi identitySession;
  private final ProfileApi profile;
  private final ChatDomainApi chat;
  private final PlatformApi platform;

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
    identitySession = new IdentitySessionApi(client);
    profile = new ProfileApi(client);
    chat = new ChatDomainApi(client);
    platform = new PlatformApi(client);
  }

  AuthenticatedUserResponse authenticatedUser(String bearer) {
    try {
      AuthenticatedUserResponse result = identity.me(headers(bearer));
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

  ProfileReadinessResponse profileReadiness(String bearer) {
    try {
      ProfileReadinessResponse result = profile.getProductProfileReadiness(headers(bearer));
      if (result == null) {
        throw new ProductFlowException("profile readiness returned an empty response");
      }
      return result;
    } catch (ApiException failure) {
      throw new ProductFlowException("profile readiness failed with HTTP " + failure.getCode());
    }
  }

  ChatReadiness chatReadiness(String bearer) {
    try {
      ChatReadiness result = chat.getChatReadiness(headers(bearer));
      if (result == null) {
        throw new ProductFlowException("Chat readiness returned an empty response");
      }
      return result;
    } catch (ApiException failure) {
      throw new ProductFlowException("Chat readiness failed with HTTP " + failure.getCode());
    }
  }

  IdentitySessionReconcileResponse reconcileIdentitySession(String bearer) {
    try {
      IdentitySessionReconcileResponse result =
          identitySession.reconcileIdentitySession(headers(bearer));
      if (result == null) {
        throw new ProductFlowException("identity reconciliation returned an empty response");
      }
      return result;
    } catch (ApiException failure) {
      throw new ProductFlowException("identity reconciliation failed with HTTP " + failure.getCode());
    }
  }

  PlatformConfigResponse platformConfig() {
    try {
      PlatformConfigResponse result = platform.config();
      if (result == null) {
        throw new ProductFlowException("platform configuration returned an empty response");
      }
      return result;
    } catch (ApiException failure) {
      throw new ProductFlowException("platform configuration failed with HTTP " + failure.getCode());
    }
  }

  private static Map<String, String> headers(String bearer) {
    if (bearer == null || bearer.isBlank()) {
      throw new IllegalArgumentException("User access token is unavailable");
    }
    return Map.of("Authorization", "Bearer " + bearer);
  }
}

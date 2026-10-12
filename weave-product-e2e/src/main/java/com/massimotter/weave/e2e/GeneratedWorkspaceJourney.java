package com.massimotter.weave.e2e;

import com.massimotter.weave.userapi.api.WorkspaceApi;
import com.massimotter.weave.userapi.invoker.ApiClient;
import com.massimotter.weave.userapi.invoker.ApiException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Generated member Home read with independent version and capability-projection expectations. */
final class GeneratedWorkspaceJourney {
  private final WorkspaceApi workspace;

  GeneratedWorkspaceJourney(ProductFlowEnvironment environment) {
    ApiClient client = new ApiClient()
        .setHttpClientBuilder(HttpClient.newBuilder()
            .sslContext(JsonHttpClient.sslContext(environment.caCertificate()))
            .connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER))
        .setReadTimeout(Duration.ofSeconds(30));
    client.updateBaseUri(environment.apiOrigin().getScheme() + "://"
        + environment.apiOrigin().getRawAuthority());
    workspace = new WorkspaceApi(client);
  }

  void verifyHome(String token) {
    try {
      var home = workspace.home(Map.of("Authorization", "Bearer " + token));
      if (home == null || !Integer.valueOf(3).equals(home.getVersion())
          || !Boolean.TRUE.equals(home.getSupportSafe()) || home.getSections() == null
          || home.getSections().size() != 5
          || !home.getSections().stream().map(value -> value.getKey()).collect(Collectors.toSet())
              .equals(Set.of("recent-channels", "open-tasks", "upcoming-meetings",
                  "recent-decisions", "workspace-health"))
          || home.getSections().stream().anyMatch(value -> value.getItemCount() != null)) {
        throw new ProductFlowException("Home version, section identity or unknown count semantics changed");
      }
      for (var action : home.getActions()) {
        if (action.getReason() == null || action.getLabel() == null
            || action.getReason().contains("WEAVE_") || action.getReason().contains("/api/admin/")
            || action.getReason().contains("Nextcloud") || action.getReason().contains("http://")
            || action.getReason().contains("https://") || !"Review availability".equals(action.getLabel())) {
          throw new ProductFlowException("Member Home exposed operator setup instructions");
        }
      }
    } catch (ApiException failure) {
      throw new ProductFlowException("Generated member Home read failed with HTTP " + failure.getCode());
    }
  }
}

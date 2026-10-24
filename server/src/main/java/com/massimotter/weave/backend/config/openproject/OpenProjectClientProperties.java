package com.massimotter.weave.backend.config.openproject;

import java.net.URI;

/** Immutable view of the existing runtime provider configuration, not a second config source. */
public record OpenProjectClientProperties(URI baseUrl, String apiToken) {
  public OpenProjectClientProperties {
    apiToken = apiToken == null ? "" : apiToken.trim();
  }

  public boolean configured() {
    return baseUrl != null && baseUrl.isAbsolute() && !apiToken.isBlank();
  }
}

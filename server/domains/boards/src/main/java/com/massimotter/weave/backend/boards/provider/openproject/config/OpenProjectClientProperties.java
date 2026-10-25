package com.massimotter.weave.backend.boards.provider.openproject.config;

import java.net.URI;

/** Immutable view of the existing runtime provider configuration, not a second config source. */
public record OpenProjectClientProperties(URI baseUrl, String apiToken) {
  public OpenProjectClientProperties {
    apiToken = apiToken == null ? "" : apiToken.trim();
  }

  @Override
  public String toString() {
    return "OpenProjectClientProperties[configured=" + configured() + "]";
  }

  public boolean configured() {
    return baseUrl != null && baseUrl.isAbsolute() && !apiToken.isBlank();
  }
}

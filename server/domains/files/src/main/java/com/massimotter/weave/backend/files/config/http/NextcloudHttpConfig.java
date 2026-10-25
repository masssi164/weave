package com.massimotter.weave.backend.files.config.http;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Dedicated WebDAV transport; custom PROPFIND/MKCOL and streaming semantics remain explicit. */
public final class NextcloudHttpConfig {
  private NextcloudHttpConfig() {}

  public static RestClient client(RestClient.Builder builder) {
    return builder.clone().requestFactory(new JdkClientHttpRequestFactory()).build();
  }
}

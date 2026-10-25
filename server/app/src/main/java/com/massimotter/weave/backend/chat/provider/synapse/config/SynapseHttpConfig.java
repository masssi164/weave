package com.massimotter.weave.backend.chat.provider.synapse.config;

import com.massimotter.weave.backend.config.ChatRuntimeProperties;
import java.net.http.HttpClient;

/** Dedicated provider HTTP transport for Matrix streaming and bounded error parsing. */
public final class SynapseHttpConfig {
  private SynapseHttpConfig() {}

  public static HttpClient client(ChatRuntimeProperties.Matrix properties) {
    return HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
  }
}

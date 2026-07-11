package com.massimotter.weave.keycloak.events;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

public final class WeaveIdentityEventListenerProviderFactory implements EventListenerProviderFactory {
  public static final String ID = "weave-identity-events";

  private URI endpoint;
  private byte[] signingSecret;
  private Duration timeout;
  private HttpClient httpClient;

  @Override
  public EventListenerProvider create(KeycloakSession session) {
    return new WeaveIdentityEventListenerProvider(session, endpoint, signingSecret, timeout, httpClient);
  }

  @Override
  public void init(Config.Scope config) {
    var endpointValue = requiredEnvironment("WEAVE_IDENTITY_EVENTS_ENDPOINT");
    var secretValue = requiredEnvironment("WEAVE_IDENTITY_EVENTS_HMAC_SECRET");
    endpoint = URI.create(endpointValue);
    if (!"http".equals(endpoint.getScheme()) && !"https".equals(endpoint.getScheme())) {
      throw new IllegalStateException("WEAVE_IDENTITY_EVENTS_ENDPOINT must be an HTTP(S) URI");
    }
    signingSecret = secretValue.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    timeout = Duration.ofSeconds(5);
    httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
  }

  private static String requiredEnvironment(String name) {
    var value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(name + " is required when the Weave identity event provider is installed");
    }
    return value;
  }

  @Override
  public void postInit(KeycloakSessionFactory factory) {}

  @Override
  public void close() {}

  @Override
  public String getId() {
    return ID;
  }
}

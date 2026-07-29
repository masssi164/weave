package com.massimotter.weave.backend.config;

import tools.jackson.databind.JsonNode;
import com.massimotter.weave.backend.agentruntime.adapter.KeycloakClientRegistrationTransport;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Qualified, bounded Spring transport for Keycloak OIDC Dynamic Client Registration only. */
public final class SpringKeycloakClientRegistrationTransport
    implements KeycloakClientRegistrationTransport {

  private final URI registrationEndpoint;
  private final String registrationPrefix;
  private final URI tokenEndpoint;
  private final RestClient restClient;

  public SpringKeycloakClientRegistrationTransport(
      URI keycloakBaseUrl, String realm, Duration timeout) {
    Objects.requireNonNull(keycloakBaseUrl, "keycloakBaseUrl");
    Objects.requireNonNull(timeout, "timeout");
    if (keycloakBaseUrl.getHost() == null
        || realm == null
        || realm.isBlank()
        || realm.contains("/")
        || timeout.isZero()
        || timeout.isNegative()) {
      throw new IllegalArgumentException("Keycloak DCR transport settings are invalid");
    }
    String encodedRealm =
        java.net.URLEncoder.encode(realm, StandardCharsets.UTF_8).replace("+", "%20");
    registrationEndpoint =
        keycloakBaseUrl.resolve(
            "/realms/" + encodedRealm + "/clients-registrations/openid-connect");
    registrationPrefix = registrationEndpoint + "/";
    tokenEndpoint =
        keycloakBaseUrl.resolve(
            "/realms/" + encodedRealm + "/protocol/openid-connect/token");
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(timeout);
    restClient = RestClient.builder().requestFactory(requestFactory).build();
  }

  @Override
  public JsonNode create(JsonNode metadata, String administrationAccessToken) {
    if (administrationAccessToken == null || administrationAccessToken.isBlank()) {
      throw new RuntimeWorkloadIdentityException(
          "Keycloak workload administration access token is unavailable");
    }
    return exchangeJson(
        () ->
            restClient
                .post()
                .uri(registrationEndpoint)
                .headers(headers -> headers.setBearerAuth(administrationAccessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(metadata)
                .retrieve()
                .body(JsonNode.class));
  }

  @Override
  public JsonNode retrieve(URI registrationUri, byte[] registrationAccessToken) {
    requireRegistrationUri(registrationUri);
    return exchangeJson(
        () ->
            restClient
                .get()
                .uri(registrationUri)
                .headers(
                    headers ->
                        headers.setBearerAuth(
                            new String(registrationAccessToken, StandardCharsets.UTF_8)))
                .retrieve()
                .body(JsonNode.class));
  }

  @Override
  public JsonNode update(
      URI registrationUri, JsonNode metadata, byte[] registrationAccessToken) {
    requireRegistrationUri(registrationUri);
    return exchangeJson(
        () ->
            restClient
                .put()
                .uri(registrationUri)
                .headers(
                    headers ->
                        headers.setBearerAuth(
                            new String(registrationAccessToken, StandardCharsets.UTF_8)))
                .contentType(MediaType.APPLICATION_JSON)
                .body(metadata)
                .retrieve()
                .body(JsonNode.class));
  }

  @Override
  public void delete(URI registrationUri, byte[] registrationAccessToken) {
    requireRegistrationUri(registrationUri);
    exchangeVoid(
        () -> {
          restClient
              .delete()
              .uri(registrationUri)
              .headers(
                  headers ->
                      headers.setBearerAuth(
                          new String(registrationAccessToken, StandardCharsets.UTF_8)))
              .retrieve()
              .toBodilessEntity();
          return null;
        });
  }

  @Override
  public JsonNode clientCredentials(Map<String, String> parameters) {
    LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    parameters.forEach(form::set);
    return exchangeJson(
        () ->
            restClient
                .post()
                .uri(tokenEndpoint)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class));
  }

  private void requireRegistrationUri(URI registrationUri) {
    if (registrationUri == null
        || registrationUri.getUserInfo() != null
        || registrationUri.getFragment() != null
        || !registrationUri.toString().startsWith(registrationPrefix)
        || registrationUri.toString().substring(registrationPrefix.length()).contains("/")) {
      throw new RuntimeWorkloadIdentityException(
          "The Keycloak registration URI is outside the configured realm boundary");
    }
  }

  private static JsonNode exchangeJson(Exchange<JsonNode> exchange) {
    JsonNode response = exchangeVoid(exchange);
    if (response == null || !response.isObject()) {
      throw new RuntimeWorkloadIdentityException(
          "Keycloak returned a malformed client-registration response");
    }
    return response;
  }

  private static <T> T exchangeVoid(Exchange<T> exchange) {
    try {
      return exchange.perform();
    } catch (RuntimeWorkloadIdentityException failure) {
      throw failure;
    } catch (RestClientResponseException failure) {
      throw new RuntimeWorkloadIdentityException(
          "Keycloak client-registration protocol request failed [failureType="
              + failureCategory(failure)
              + "]");
    } catch (RuntimeException failure) {
      throw new RuntimeWorkloadIdentityException(
          "Keycloak client-registration protocol request failed [failureType="
              + failure.getClass().getSimpleName()
              + "]");
    }
  }

  static String failureCategory(RestClientResponseException failure) {
    String response = failure.getResponseBodyAsString().toLowerCase(Locale.ROOT);
    if (response.contains("allowed client scopes")) {
      return "RegistrationPolicyClientScopes";
    }
    if (response.contains("allowed protocol mapper")) {
      return "RegistrationPolicyProtocolMappers";
    }
    if (response.contains("trusted host")) {
      return "RegistrationPolicyTrustedHost";
    }
    if (response.contains("weave workload") || response.contains("client metadata invalid")) {
      return "WorkloadClientPolicy";
    }
    if (response.contains("insufficient_scope")) {
      return "RegistrationPolicyInsufficientScope";
    }
    if (response.contains("invalid_client_metadata")) {
      return "InvalidClientMetadata";
    }
    return "Http" + failure.getStatusCode().value();
  }

  @FunctionalInterface
  private interface Exchange<T> {
    T perform();
  }
}

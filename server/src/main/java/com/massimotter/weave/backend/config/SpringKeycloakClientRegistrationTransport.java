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
import java.util.regex.Pattern;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Qualified, bounded Spring transport for Keycloak OIDC Dynamic Client Registration only. */
public final class SpringKeycloakClientRegistrationTransport
    implements KeycloakClientRegistrationTransport {

  private static final Pattern WORKLOAD_CLIENT_ID =
      Pattern.compile("weaver-cell-[A-Za-z0-9_-]+");

  private final URI registrationEndpoint;
  private final URI publicRegistrationEndpoint;
  private final URI tokenEndpoint;
  private final RestClient restClient;

  public SpringKeycloakClientRegistrationTransport(
      URI keycloakBaseUrl, URI issuer, String realm, Duration timeout) {
    Objects.requireNonNull(keycloakBaseUrl, "keycloakBaseUrl");
    Objects.requireNonNull(issuer, "issuer");
    Objects.requireNonNull(timeout, "timeout");
    if (keycloakBaseUrl.getHost() == null
        || issuer.getHost() == null
        || !"https".equalsIgnoreCase(issuer.getScheme())
        || issuer.getUserInfo() != null
        || issuer.getQuery() != null
        || issuer.getFragment() != null
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
    String expectedIssuerPath = "/realms/" + encodedRealm;
    if (!expectedIssuerPath.equals(issuer.getRawPath())) {
      throw new IllegalArgumentException(
          "Keycloak DCR issuer must identify the configured realm exactly");
    }
    publicRegistrationEndpoint =
        URI.create(
            issuer.getScheme().toLowerCase(Locale.ROOT)
                + "://"
                + issuer.getRawAuthority()
                + expectedIssuerPath
                + "/clients-registrations/openid-connect");
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
  public JsonNode retrieve(
      String clientId, URI registrationUri, byte[] registrationAccessToken) {
    URI operationUri = registrationOperationUri(clientId, registrationUri);
    return exchangeJson(
        () ->
            restClient
                .get()
                .uri(operationUri)
                .headers(
                    headers ->
                        headers.setBearerAuth(
                            new String(registrationAccessToken, StandardCharsets.UTF_8)))
                .retrieve()
                .body(JsonNode.class));
  }

  @Override
  public JsonNode update(
      String clientId,
      URI registrationUri,
      JsonNode metadata,
      byte[] registrationAccessToken) {
    URI operationUri = registrationOperationUri(clientId, registrationUri);
    return exchangeJson(
        () ->
            restClient
                .put()
                .uri(operationUri)
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
  public void delete(
      String clientId, URI registrationUri, byte[] registrationAccessToken) {
    URI operationUri = registrationOperationUri(clientId, registrationUri);
    try {
      restClient
          .delete()
          .uri(operationUri)
          .headers(
              headers ->
                  headers.setBearerAuth(
                      new String(registrationAccessToken, StandardCharsets.UTF_8)))
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientResponseException failure) {
      if (failure.getStatusCode().value() != 404) {
        throw protocolFailure(failure);
      }
    } catch (RuntimeWorkloadIdentityException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw new RuntimeWorkloadIdentityException(
          "Keycloak client-registration protocol request failed [failureType="
              + failure.getClass().getSimpleName()
              + "]");
    }
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

  private URI registrationOperationUri(String clientId, URI registrationUri) {
    if (!WORKLOAD_CLIENT_ID.matcher(Objects.requireNonNullElse(clientId, "")).matches()) {
      throw new RuntimeWorkloadIdentityException(
          "The Keycloak registration client is outside the configured realm boundary");
    }
    URI expectedPublicUri =
        URI.create(publicRegistrationEndpoint.toASCIIString() + "/" + clientId);
    if (!expectedPublicUri.equals(registrationUri)) {
      throw new RuntimeWorkloadIdentityException(
          "The Keycloak registration URI is outside the configured realm boundary");
    }
    // Keycloak returns its public backend URI. Retain that protocol value in protected state,
    // but never use its authority as an outbound destination.
    return URI.create(registrationEndpoint.toASCIIString() + "/" + clientId);
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
      throw protocolFailure(failure);
    } catch (RuntimeException failure) {
      throw new RuntimeWorkloadIdentityException(
          "Keycloak client-registration protocol request failed [failureType="
              + failure.getClass().getSimpleName()
              + "]");
    }
  }

  private static RuntimeWorkloadIdentityException protocolFailure(
      RestClientResponseException failure) {
    return new RuntimeWorkloadIdentityException(
        "Keycloak client-registration protocol request failed [failureType="
            + failureCategory(failure)
            + "]");
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

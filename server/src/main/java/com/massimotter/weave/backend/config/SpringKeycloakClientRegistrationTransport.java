package com.massimotter.weave.backend.config;

import tools.jackson.databind.JsonNode;
import com.massimotter.weave.backend.agentruntime.adapter.KeycloakClientRegistrationTransport;
import com.massimotter.weave.backend.agentruntime.adapter.KeycloakClientRegistrationTransport.FinalizeResult;
import com.massimotter.weave.backend.agentruntime.adapter.KeycloakClientRegistrationTransport.RegistrationHandoffProof;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Qualified, bounded Spring transport for Keycloak OIDC Dynamic Client Registration only. */
public final class SpringKeycloakClientRegistrationTransport
    implements KeycloakClientRegistrationTransport {

  private static final Pattern WORKLOAD_CLIENT_ID =
      Pattern.compile("weaver-cell-[A-Za-z0-9_-]+");
  private static final String HANDOFF_HEADER = "Weave-Registration-Handoff";
  private static final String HANDOFF_STATE_HEADER = "Weave-Registration-Handoff-State";
  private static final String HANDOFF_OPERATION_HEADER =
      "Weave-Registration-Handoff-Operation";

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
    HttpClient httpClient =
        HttpClient.newBuilder()
            .connectTimeout(timeout)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(timeout);
    restClient = RestClient.builder().requestFactory(requestFactory).build();
  }

  @Override
  public JsonNode create(
      JsonNode metadata,
      String administrationAccessToken,
      RegistrationHandoffProof handoff) {
    if (administrationAccessToken == null || administrationAccessToken.isBlank()) {
      throw new RuntimeWorkloadIdentityException(
          "Keycloak workload administration access token is unavailable");
    }
    return exchangeJson(
        () ->
            restClient
                .post()
                .uri(registrationEndpoint)
                .headers(
                    headers -> {
                      headers.setBearerAuth(administrationAccessToken);
                      addHandoffHeaders(headers, handoff);
                    })
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
      byte[] registrationAccessToken,
      RegistrationHandoffProof handoff) {
    URI operationUri = registrationOperationUri(clientId, registrationUri);
    return exchangeJson(
        () ->
            restClient
                .put()
                .uri(operationUri)
                .headers(
                    headers -> {
                      headers.setBearerAuth(
                          new String(registrationAccessToken, StandardCharsets.UTF_8));
                      addHandoffHeaders(headers, handoff);
                    })
                .contentType(MediaType.APPLICATION_JSON)
                .body(metadata)
                .retrieve()
                .body(JsonNode.class));
  }

  @Override
  public JsonNode recover(
      String clientId,
      URI registrationUri,
      String administrationAccessToken,
      RegistrationHandoffProof handoff) {
    if (administrationAccessToken == null || administrationAccessToken.isBlank()) {
      throw new RuntimeWorkloadIdentityException(
          "Keycloak workload administration access token is unavailable");
    }
    URI operationUri =
        URI.create(
            registrationOperationUri(clientId, registrationUri).toASCIIString()
                + "/weave-registration-handoff/recover");
    ResponseEntity<JsonNode> response;
    try {
      response =
          restClient
              .post()
              .uri(operationUri)
              .headers(
                  headers -> {
                    headers.setBearerAuth(administrationAccessToken);
                    addHandoffHeaders(headers, handoff);
                  })
              .retrieve()
              .toEntity(JsonNode.class);
    } catch (RestClientResponseException failure) {
      requireNonCacheable(failure.getResponseHeaders());
      throw protocolFailure(failure);
    } catch (RuntimeWorkloadIdentityException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw new RuntimeWorkloadIdentityException(
          "Keycloak client-registration protocol request failed [failureType="
              + failure.getClass().getSimpleName()
              + "]");
    }
    requireNonCacheable(response);
    JsonNode body = response.getBody();
    if (body == null || !body.isObject()) {
      throw new RuntimeWorkloadIdentityException(
          "Keycloak returned a malformed client-registration response");
    }
    return body;
  }

  @Override
  public FinalizeResult finalizeHandoff(
      String clientId,
      URI registrationUri,
      byte[] registrationAccessToken,
      RegistrationHandoffProof handoff) {
    URI operationUri =
        URI.create(
            registrationOperationUri(clientId, registrationUri).toASCIIString()
                + "/weave-registration-handoff/finalize");
    try {
      ResponseEntity<Void> response =
          restClient
              .post()
              .uri(operationUri)
              .headers(
                  headers -> {
                    headers.setBearerAuth(
                        new String(registrationAccessToken, StandardCharsets.UTF_8));
                    addHandoffHeaders(headers, handoff);
                  })
              .retrieve()
              .toBodilessEntity();
      if (response.getStatusCode().value() != 204) {
        throw new RuntimeWorkloadIdentityException(
            "Keycloak returned an invalid registration handoff status");
      }
      requireNonCacheable(response);
      return FinalizeResult.FINALIZED;
    } catch (RestClientResponseException failure) {
      requireNonCacheable(failure.getResponseHeaders());
      if (failure.getStatusCode().value() == 409
          && failure
              .getResponseBodyAsString()
              .toLowerCase(Locale.ROOT)
              .contains("registration_handoff_state_mismatch")) {
        return FinalizeResult.ALREADY_FINALIZED;
      }
      throw protocolFailure(failure);
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

  private static void addHandoffHeaders(
      org.springframework.http.HttpHeaders headers, RegistrationHandoffProof handoff) {
    Objects.requireNonNull(handoff, "handoff");
    headers.set(HANDOFF_HEADER, handoff.capabilityHeader());
    headers.set(HANDOFF_STATE_HEADER, handoff.stateDigest());
    headers.set(HANDOFF_OPERATION_HEADER, handoff.operation().wireValue());
  }

  private static void requireNonCacheable(ResponseEntity<?> response) {
    requireNonCacheable(response.getHeaders());
  }

  private static void requireNonCacheable(HttpHeaders headers) {
    if (headers == null
        || !containsValuelessCacheControlDirective(headers, "no-store")
        || !"no-cache".equalsIgnoreCase(headers.getFirst("Pragma"))) {
      throw new RuntimeWorkloadIdentityException(
          "Keycloak returned an unsafe registration handoff response");
    }
  }

  private static boolean containsValuelessCacheControlDirective(
      HttpHeaders headers, String requiredDirective) {
    for (String headerValue : headers.getOrEmpty(HttpHeaders.CACHE_CONTROL)) {
      boolean quoted = false;
      boolean escaped = false;
      int directiveStart = 0;
      for (int index = 0; index <= headerValue.length(); index++) {
        boolean atEnd = index == headerValue.length();
        char character = atEnd ? '\0' : headerValue.charAt(index);
        if (!atEnd && quoted && character == '\\' && !escaped) {
          escaped = true;
          continue;
        }
        if (!atEnd && character == '"' && !escaped) {
          quoted = !quoted;
        }
        if (atEnd || (!quoted && character == ',')) {
          String directive = headerValue.substring(directiveStart, index).strip();
          if (requiredDirective.equalsIgnoreCase(directive)) {
            return true;
          }
          directiveStart = index + 1;
        }
        escaped = false;
      }
    }
    return false;
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

package com.massimotter.weave.backend.identity.migration;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Support-safe HTTP transport limited by {@link KeycloakRealmMigrationOperationPolicy}. */
final class KeycloakRealmMigrationTransport {
  private final RestClient restClient;
  private final ObjectMapper mapper;

  KeycloakRealmMigrationTransport(RestClient restClient, ObjectMapper mapper) {
    this.restClient = restClient;
    this.mapper = mapper;
  }

  JsonNode get(String path) {
    String body = request(HttpMethod.GET, path, null, 200);
    try {
      JsonNode result = mapper.readTree(body);
      if (result == null) {
        throw blocked("admin-rest-response-invalid");
      }
      return result;
    } catch (JacksonException failure) {
      throw blocked("admin-rest-response-invalid");
    }
  }

  void post(String path, JsonNode body) {
    request(HttpMethod.POST, path, body.toString(), 201);
  }

  void put(String path, JsonNode body) {
    request(HttpMethod.PUT, path, body.toString(), 201);
  }

  void delete(String path) {
    request(HttpMethod.DELETE, path, null, 204);
  }

  boolean rejectsCurrentToken(String path) {
    String operation = KeycloakRealmMigrationOperationPolicy.requireAllowed(HttpMethod.GET, path);
    try {
      ResponseEntity<Void> response =
          restClient
              .get()
              .uri(path)
              .retrieve()
              .onStatus(status -> status.value() == 401, (ignored, providerResponse) -> {})
              .onStatus(
                  status -> status.value() != 200 && status.value() != 401,
                  (ignored, providerResponse) -> {
                    throw blocked(
                        "admin-rest-"
                            + operation
                            + "-status-"
                            + providerResponse.getStatusCode().value());
                  })
              .toBodilessEntity();
      return response.getStatusCode().value() == 401;
    } catch (KeycloakRealmMigrationException failure) {
      throw failure;
    } catch (RestClientException failure) {
      throw blocked("admin-rest-" + operation + "-unavailable");
    }
  }

  private String request(HttpMethod method, String path, String body, int expectedStatus) {
    String operation = KeycloakRealmMigrationOperationPolicy.requireAllowed(method, path);
    RestClient.RequestBodySpec request = restClient.method(method).uri(path);
    if (body != null) {
      request.contentType(MediaType.APPLICATION_JSON).body(body);
    }
    try {
      ResponseEntity<String> response =
          request
              .retrieve()
              .onStatus(
                  status -> status.value() != expectedStatus,
                  (ignored, providerResponse) -> {
                    throw blocked(
                        "admin-rest-"
                            + operation
                            + "-status-"
                            + providerResponse.getStatusCode().value());
                  })
              .toEntity(String.class);
      return response.getBody() == null ? "" : response.getBody();
    } catch (KeycloakRealmMigrationException failure) {
      throw failure;
    } catch (RestClientException failure) {
      throw blocked("admin-rest-" + operation + "-unavailable");
    }
  }

  private static KeycloakRealmMigrationException blocked(String code) {
    return new KeycloakRealmMigrationException(code);
  }
}

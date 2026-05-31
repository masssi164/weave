package com.massimotter.weave.backend.identity.realm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HttpKeycloakRealmAdminClient implements KeycloakRealmAdminClient {

    private static final TypeReference<List<Map<String, Object>>> LIST_OF_MAPS = new TypeReference<>() {};

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI baseUri;
    private final String bearerToken;
    private final Duration timeout;

    public HttpKeycloakRealmAdminClient(URI baseUri, String bearerToken) {
        this(baseUri, bearerToken, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), new ObjectMapper(), Duration.ofSeconds(10));
    }

    HttpKeycloakRealmAdminClient(URI baseUri, String bearerToken, HttpClient httpClient, ObjectMapper objectMapper, Duration timeout) {
        this.baseUri = baseUri;
        this.bearerToken = bearerToken;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.timeout = timeout;
    }

    @Override
    public ApplySummary applyDesiredState(IdentityRealmDesiredState desiredState) {
        if (desiredState == null || blank(desiredState.realmId())) {
            throw new KeycloakRealmAdminClientException("Keycloak desired realm id is required");
        }
        String realmId = desiredState.realmId().trim();
        int verified = 0;
        boolean mutated = false;

        RealmCheck realm = get("/admin/realms/" + path(realmId));
        verified++;
        Map<String, Object> realmRepresentation = Map.of(
                "realm", realmId,
                "displayName", valueOrDefault(desiredState.displayName(), realmId),
                "enabled", desiredState.enabled() == null || desiredState.enabled());
        if (realm.status() == 404) {
            request("POST", "/admin/realms", realmRepresentation, 201, 204);
            mutated = true;
        } else if (realm.success() && !realmMatches(realm.body(), desiredState)) {
            request("PUT", "/admin/realms/" + path(realmId), realmRepresentation, 204);
            mutated = true;
        } else if (!realm.success()) {
            throw unavailable("realm", realm.status());
        }

        for (String role : desiredState.roles()) {
            if (blank(role)) {
                continue;
            }
            RealmCheck roleCheck = get("/admin/realms/" + path(realmId) + "/roles/" + path(role));
            verified++;
            if (roleCheck.status() == 404) {
                request("POST", "/admin/realms/" + path(realmId) + "/roles", Map.of("name", role), 201, 204);
                mutated = true;
            } else if (!roleCheck.success()) {
                throw unavailable("role", roleCheck.status());
            }
        }

        for (String group : desiredState.groups()) {
            if (blank(group)) {
                continue;
            }
            RealmCheck groupCheck = get("/admin/realms/" + path(realmId) + "/groups?search=" + query(group));
            verified++;
            if (groupCheck.success() && groupMissing(groupCheck.body())) {
                request("POST", "/admin/realms/" + path(realmId) + "/groups", Map.of("name", group), 201, 204);
                mutated = true;
            } else if (!groupCheck.success()) {
                throw unavailable("group", groupCheck.status());
            }
        }

        for (IdentityRealmDesiredState.RealmClient client : desiredState.clients()) {
            if (client == null || blank(client.clientId())) {
                continue;
            }
            RealmCheck clientCheck = get("/admin/realms/" + path(realmId) + "/clients?clientId=" + query(client.clientId()));
            verified++;
            List<Map<String, Object>> existing = list(clientCheck.body());
            Map<String, Object> representation = Map.of(
                    "clientId", client.clientId(),
                    "publicClient", client.publicClient(),
                    "redirectUris", client.redirectOrigins(),
                    "defaultClientScopes", client.scopes());
            if (clientCheck.success() && existing.isEmpty()) {
                request("POST", "/admin/realms/" + path(realmId) + "/clients", representation, 201, 204);
                mutated = true;
            } else if (clientCheck.success() && !clientMatches(existing.get(0), client)) {
                Object id = existing.get(0).get("id");
                if (id != null && !String.valueOf(id).isBlank()) {
                    request("PUT", "/admin/realms/" + path(realmId) + "/clients/" + path(String.valueOf(id)), representation, 204);
                    mutated = true;
                }
            } else if (!clientCheck.success()) {
                throw unavailable("client", clientCheck.status());
            }
        }
        return new ApplySummary(mutated, verified);
    }

    private RealmCheck get(String path) {
        HttpRequest request = requestBuilder(path).GET().build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new RealmCheck(response.statusCode(), response.body());
        } catch (IOException e) {
            throw new KeycloakRealmAdminClientException("Keycloak Admin REST is unavailable", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KeycloakRealmAdminClientException("Keycloak Admin REST request was interrupted", e);
        }
    }

    private void request(String method, String path, Map<String, Object> body, int... expectedStatuses) {
        try {
            HttpRequest request = requestBuilder(path)
                    .method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .header("Content-Type", "application/json")
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            for (int expected : expectedStatuses) {
                if (response.statusCode() == expected) {
                    return;
                }
            }
            throw unavailable(method.toLowerCase(Locale.ROOT), response.statusCode());
        } catch (IOException e) {
            throw new KeycloakRealmAdminClientException("Keycloak Admin REST is unavailable", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KeycloakRealmAdminClientException("Keycloak Admin REST request was interrupted", e);
        }
    }

    private HttpRequest.Builder requestBuilder(String path) {
        return HttpRequest.newBuilder(baseUri.resolve(path))
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + bearerToken);
    }

    private boolean realmMatches(String body, IdentityRealmDesiredState desiredState) {
        try {
            Map<String, Object> realm = objectMapper.readValue(body, new TypeReference<>() {});
            boolean enabled = desiredState.enabled() == null || desiredState.enabled();
            return valueOrDefault(desiredState.displayName(), desiredState.realmId()).equals(String.valueOf(realm.getOrDefault("displayName", "")))
                    && Boolean.valueOf(enabled).equals(Boolean.valueOf(String.valueOf(realm.getOrDefault("enabled", "false"))));
        } catch (Exception e) {
            return false;
        }
    }

    private boolean groupMissing(String body) {
        return list(body).isEmpty();
    }

    private boolean clientMatches(Map<String, Object> existing, IdentityRealmDesiredState.RealmClient desired) {
        if (existing == null) {
            return false;
        }
        return String.valueOf(existing.getOrDefault("clientId", "")).equals(desired.clientId())
                && Boolean.valueOf(desired.publicClient()).equals(Boolean.valueOf(String.valueOf(existing.getOrDefault("publicClient", "false"))))
                && listValues(existing.get("redirectUris")).containsAll(desired.redirectOrigins())
                && listValues(existing.get("defaultClientScopes")).containsAll(desired.scopes());
    }

    private List<String> listValues(Object value) {
        if (value instanceof List<?> values) {
            return values.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private List<Map<String, Object>> list(String body) {
        if (blank(body)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(body, LIST_OF_MAPS);
        } catch (Exception e) {
            return List.of();
        }
    }

    private KeycloakRealmAdminClientException unavailable(String operation, int status) {
        return new KeycloakRealmAdminClientException("Keycloak Admin REST " + operation + " operation failed with sanitized status " + status);
    }

    private String path(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String query(String value) {
        return path(value);
    }

    private String valueOrDefault(String value, String fallback) {
        return blank(value) ? fallback : value.trim();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record RealmCheck(int status, String body) {
        boolean success() {
            return status >= 200 && status < 300;
        }
    }
}

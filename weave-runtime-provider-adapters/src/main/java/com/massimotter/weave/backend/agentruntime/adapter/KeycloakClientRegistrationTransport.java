package com.massimotter.weave.backend.agentruntime.adapter;

import tools.jackson.databind.JsonNode;
import java.net.URI;
import java.util.Map;

/**
 * Narrow transport for Keycloak's OIDC Dynamic Client Registration and workload token protocols.
 *
 * <p>Registration Access Tokens are adapter-private and must never be logged, surfaced through
 * application ports, or reused for a different registration URI.
 */
public interface KeycloakClientRegistrationTransport {

    JsonNode create(JsonNode metadata, String administrationAccessToken);

    JsonNode retrieve(URI registrationUri, byte[] registrationAccessToken);

    JsonNode update(
            URI registrationUri,
            JsonNode metadata,
            byte[] registrationAccessToken);

    void delete(URI registrationUri, byte[] registrationAccessToken);

    JsonNode clientCredentials(Map<String, String> parameters);
}

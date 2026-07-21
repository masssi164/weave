package com.massimotter.weave.mcp;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Docker-private ARC context adapter. It receives only the newly exchanged API token. */
final class HttpMcpBackendContextResolver implements McpBackendContextResolver {
    private static final int MAXIMUM_RESPONSE_BYTES = 1_048_576;
    private static final Set<String> RESPONSE_FIELDS = Set.of(
            "schema",
            "authorizationRef",
            "organizationRef",
            "cellRef",
            "workloadClientId",
            "workloadRefHash",
            "runtimeProfileId",
            "runtimeProfileHash",
            "entitlementRevision",
            "authorizationExpiresAt",
            "grantedScopes",
            "visibleToolClasses");

    private final McpWorkloadProperties properties;
    private final JsonMapper mapper;
    private final HttpClient http;

    HttpMcpBackendContextResolver(McpWorkloadProperties properties, JsonMapper mapper) {
        this(
                properties,
                mapper,
                HttpClient.newBuilder()
                        .connectTimeout(properties.requestTimeout())
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build());
    }

    HttpMcpBackendContextResolver(McpWorkloadProperties properties, JsonMapper mapper, HttpClient http) {
        this.properties = properties;
        this.mapper = mapper;
        this.http = http;
    }

    @Override
    public McpBackendContext resolve(McpCellWorkloadPrincipal workload, ExchangedAccessToken token) {
        try {
            HttpRequest request = HttpRequest.newBuilder(properties.backendContextUri())
                    .timeout(properties.requestTimeout())
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + token.value())
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.body().length > MAXIMUM_RESPONSE_BYTES) {
                throw unavailable();
            }
            if (response.statusCode() != 200) {
                throw response.statusCode() >= 500 ? unavailable() : forbidden();
            }
            return validate(response.body(), workload, token);
        } catch (IOException failure) {
            throw unavailable();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw unavailable();
        }
    }

    private McpBackendContext validate(
            byte[] body,
            McpCellWorkloadPrincipal workload,
            ExchangedAccessToken token) {
        try {
            JsonNode root = mapper.readTree(body);
            if (!root.isObject()
                    || !root.propertyNames().stream().collect(java.util.stream.Collectors.toSet()).equals(RESPONSE_FIELDS)
                    || !"weave.mcp-workload-context/v2".equals(required(root, "schema"))) {
                throw forbidden();
            }
            String authorizationRef = pattern(root, "authorizationRef", "mcp-authz:[a-f0-9]{64}");
            String organizationRef = required(root, "organizationRef");
            String cellRef = required(root, "cellRef");
            String clientId = required(root, "workloadClientId");
            String workloadRefHash = pattern(root, "workloadRefHash", "sha256:[a-f0-9]{64}");
            String profileId = pattern(root, "runtimeProfileId", "rp_[A-Za-z0-9_-]+");
            String profileHash = pattern(root, "runtimeProfileHash", "sha256:[a-f0-9]{64}");
            String entitlementRevision = pattern(root, "entitlementRevision", "sha256:[a-f0-9]{64}");
            Instant authorizationExpiresAt = Instant.parse(required(root, "authorizationExpiresAt"));
            Set<String> scopes = strings(root.path("grantedScopes"));
            Set<String> toolClasses = strings(root.path("visibleToolClasses"));
            String expectedWorkloadRef = fingerprint(
                    workload.issuer() + "\u0000" + workload.subject() + "\u0000" + workload.clientId());
            if (!clientId.equals(workload.clientId())
                    || !workloadRefHash.equals(expectedWorkloadRef)
                    || !scopes.equals(token.scopes())
                    || toolClasses.isEmpty()
                    || !scopes.containsAll(toolClasses)
                    || authorizationExpiresAt.isAfter(token.expiresAt())) {
                throw forbidden();
            }
            return new McpBackendContext(
                    authorizationRef,
                    organizationRef,
                    cellRef,
                    clientId,
                    workloadRefHash,
                    profileId,
                    profileHash,
                    entitlementRevision,
                    authorizationExpiresAt,
                    scopes,
                    toolClasses);
        } catch (McpAdmissionException failure) {
            throw failure;
        } catch (RuntimeException invalid) {
            throw forbidden();
        }
    }

    private static Set<String> strings(JsonNode node) {
        if (!node.isArray() || node.isEmpty() || node.size() > 64) {
            throw forbidden();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        node.forEach(item -> {
            if (!item.isString() || item.stringValue().isBlank() || !values.add(item.stringValue())) {
                throw forbidden();
            }
        });
        return Set.copyOf(values);
    }

    private static String required(JsonNode root, String field) {
        JsonNode node = root.path(field);
        if (!node.isString()) {
            throw forbidden();
        }
        String value = node.stringValue();
        if (value.isBlank() || value.length() > 500) {
            throw forbidden();
        }
        return value;
    }

    private static String pattern(JsonNode root, String field, String pattern) {
        String value = required(root, field);
        if (!value.matches(pattern)) {
            throw forbidden();
        }
        return value;
    }

    private static String fingerprint(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static McpAdmissionException forbidden() {
        return new McpAdmissionException(McpAdmissionException.Kind.FORBIDDEN);
    }

    private static McpAdmissionException unavailable() {
        return new McpAdmissionException(McpAdmissionException.Kind.UNAVAILABLE);
    }
}

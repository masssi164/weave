package com.massimotter.weave.mcp;

import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** RFC 8693 Standard Token Exchange V2 adapter. Incoming tokens are never relayed to domain or provider services. */
final class HttpMcpBackendTokenExchange implements McpBackendTokenExchange {
    private static final String TOKEN_EXCHANGE_GRANT = "urn:ietf:params:oauth:grant-type:token-exchange";
    private static final String ACCESS_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:access_token";
    private static final int MAXIMUM_RESPONSE_BYTES = 1_048_576;

    private final McpWorkloadProperties properties;
    private final JsonMapper mapper;
    private final HttpClient http;

    HttpMcpBackendTokenExchange(McpWorkloadProperties properties, JsonMapper mapper) {
        this(
                properties,
                mapper,
                HttpClient.newBuilder()
                        .connectTimeout(properties.requestTimeout())
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build());
    }

    HttpMcpBackendTokenExchange(McpWorkloadProperties properties, JsonMapper mapper, HttpClient http) {
        this.properties = properties;
        this.mapper = mapper;
        this.http = http;
    }

    @Override
    public ExchangedAccessToken exchange(
            McpCellWorkloadPrincipal workload,
            String subjectToken,
            Set<String> scopes) {
        if (subjectToken == null || subjectToken.isBlank()
                || scopes == null || scopes.isEmpty()
                || !workload.scopes().containsAll(scopes)
                || !Set.copyOf(properties.exchangeScopes()).equals(scopes)) {
            throw forbidden();
        }
        byte[] privateJwk = readCredential(properties.exchangeClientKeyFile());
        byte[] form = null;
        try {
            Map<String, String> parameters = new LinkedHashMap<>();
            parameters.put("grant_type", TOKEN_EXCHANGE_GRANT);
            parameters.put("client_id", properties.exchangeClientId());
            parameters.put("client_assertion_type", PrivateKeyJwtClientAssertion.ASSERTION_TYPE);
            parameters.put(
                    "client_assertion",
                    PrivateKeyJwtClientAssertion.create(properties, mapper, privateJwk, Instant.now()));
            parameters.put("subject_token", subjectToken);
            parameters.put("subject_token_type", ACCESS_TOKEN_TYPE);
            parameters.put("requested_token_type", ACCESS_TOKEN_TYPE);
            parameters.put("audience", properties.backendResourceUri().toString());
            parameters.put("scope", scopes.stream().sorted().reduce((left, right) -> left + " " + right).orElseThrow());
            form = form(parameters).getBytes(StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(properties.tokenUri())
                    .timeout(properties.requestTimeout())
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(form))
                    .build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.body().length > MAXIMUM_RESPONSE_BYTES) {
                throw unavailable();
            }
            if (response.statusCode() != 200) {
                throw response.statusCode() >= 500 ? unavailable() : forbidden();
            }
            return validateResponse(response.body(), workload, scopes);
        } catch (IOException failure) {
            throw unavailable();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } finally {
            clear(privateJwk);
            clear(form);
        }
    }

    private ExchangedAccessToken validateResponse(
            byte[] body,
            McpCellWorkloadPrincipal workload,
            Set<String> requestedScopes) {
        try {
            JsonNode response = mapper.readTree(body);
            if (!response.isObject()
                    || response.has("refresh_token")
                    || response.has("id_token")
                    || !"Bearer".equalsIgnoreCase(requiredText(response, "token_type"))
                    || (response.has("issued_token_type")
                    && !ACCESS_TOKEN_TYPE.equals(requiredText(response, "issued_token_type")))) {
                throw forbidden();
            }
            String accessToken = requiredText(response, "access_token");
            JsonNode claims = jwtClaims(accessToken);
            String subject = requiredText(claims, "sub");
            String authorizedParty = requiredText(claims, "azp");
            String clientId = claims.has("client_id")
                    ? requiredText(claims, "client_id")
                    : authorizedParty;
            Set<String> audiences = audiences(claims.path("aud"));
            Set<String> scopes = scopes(requiredText(claims, "scope"));
            Instant issuedAt = epoch(claims, "iat");
            Instant expiresAt = epoch(claims, "exp");
            requiredText(claims, "jti");
            if (!workload.subject().equals(subject)
                    || !properties.exchangeClientId().equals(authorizedParty)
                    || !properties.exchangeClientId().equals(clientId)
                    || !audiences.equals(Set.of(properties.backendResourceUri().toString()))
                    || !scopes.equals(requestedScopes)
                    || expiresAt.isAfter(workload.expiresAt())
                    || expiresAt.isAfter(issuedAt.plus(properties.maximumTokenTtl()))
                    || !expiresAt.isAfter(issuedAt)) {
                throw forbidden();
            }
            long expiresIn = response.path("expires_in").asLong(-1);
            if (expiresIn < 1 || expiresIn > properties.maximumTokenTtl().toSeconds()) {
                throw forbidden();
            }
            return new ExchangedAccessToken(
                    accessToken,
                    subject,
                    authorizedParty,
                    audiences,
                    scopes,
                    issuedAt,
                    expiresAt);
        } catch (McpAdmissionException failure) {
            throw failure;
        } catch (RuntimeException | IOException invalid) {
            throw forbidden();
        }
    }

    private JsonNode jwtClaims(String token) throws IOException {
        String[] segments = token.split("\\.", -1);
        if (segments.length != 3 || segments[0].isBlank() || segments[1].isBlank() || segments[2].isBlank()) {
            throw forbidden();
        }
        byte[] payload = Base64.getUrlDecoder().decode(segments[1]);
        try {
            if (payload.length > MAXIMUM_RESPONSE_BYTES) {
                throw forbidden();
            }
            return mapper.readTree(payload);
        } finally {
            Arrays.fill(payload, (byte) 0);
        }
    }

    private static Set<String> audiences(JsonNode value) {
        if (value.isString()) {
            return Set.of(value.stringValue());
        }
        if (!value.isArray() || value.isEmpty()) {
            throw forbidden();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        value.forEach(item -> {
            if (!item.isString() || item.stringValue().isBlank() || !result.add(item.stringValue())) {
                throw forbidden();
            }
        });
        return Set.copyOf(result);
    }

    private static Set<String> scopes(String value) {
        String[] parts = value.trim().split("\\s+");
        LinkedHashSet<String> result = new LinkedHashSet<>(List.of(parts));
        if (result.isEmpty() || result.size() != parts.length) {
            throw forbidden();
        }
        return Set.copyOf(result);
    }

    private static Instant epoch(JsonNode claims, String field) {
        long value = claims.path(field).asLong(Long.MIN_VALUE);
        if (value == Long.MIN_VALUE) {
            throw forbidden();
        }
        return Instant.ofEpochSecond(value);
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode valueNode = node.path(field);
        if (!valueNode.isString()) {
            throw forbidden();
        }
        String value = valueNode.stringValue();
        if (value.isBlank()) {
            throw forbidden();
        }
        return value;
    }

    static byte[] readCredential(Path file) {
        try {
            Path normalized = file.toAbsolutePath().normalize();
            if (Files.isSymbolicLink(normalized)
                    || !Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)
                    || !Files.isReadable(normalized)) {
                throw unavailable();
            }
            try {
                Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(normalized, LinkOption.NOFOLLOW_LINKS);
                if (permissions.contains(PosixFilePermission.GROUP_WRITE)
                        || permissions.contains(PosixFilePermission.OTHERS_READ)
                        || permissions.contains(PosixFilePermission.OTHERS_WRITE)
                        || permissions.contains(PosixFilePermission.OTHERS_EXECUTE)) {
                    throw unavailable();
                }
            } catch (UnsupportedOperationException ignored) {
                // Non-POSIX filesystems still retain the no-symlink/regular/readable checks.
            }
            byte[] bytes = Files.readAllBytes(normalized);
            if (bytes.length == 0 || bytes.length > 16384) {
                clear(bytes);
                throw unavailable();
            }
            int end = bytes.length;
            while (end > 0 && (bytes[end - 1] == '\n' || bytes[end - 1] == '\r')) {
                end--;
            }
            if (end == 0) {
                clear(bytes);
                throw unavailable();
            }
            byte[] result = Arrays.copyOf(bytes, end);
            clear(bytes);
            return result;
        } catch (IOException failure) {
            throw unavailable();
        }
    }

    private static String form(Map<String, String> values) {
        List<Map.Entry<String, String>> entries = new ArrayList<>(values.entrySet());
        entries.sort(Comparator.comparing(Map.Entry::getKey));
        return entries.stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElseThrow();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static void clear(byte[] value) {
        if (value != null) {
            Arrays.fill(value, (byte) 0);
        }
    }

    private static McpAdmissionException forbidden() {
        return new McpAdmissionException(McpAdmissionException.Kind.FORBIDDEN);
    }

    private static McpAdmissionException unavailable() {
        return new McpAdmissionException(McpAdmissionException.Kind.UNAVAILABLE);
    }
}

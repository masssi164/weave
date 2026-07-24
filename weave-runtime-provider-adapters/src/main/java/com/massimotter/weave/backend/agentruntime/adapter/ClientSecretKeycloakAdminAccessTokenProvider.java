package com.massimotter.weave.backend.agentruntime.adapter;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityException;
import com.massimotter.weave.backend.agentruntime.port.SecretRefAccess;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

/** Client-credentials token provider whose long-lived Keycloak admin secret stays behind a SecretRef. */
public final class ClientSecretKeycloakAdminAccessTokenProvider implements KeycloakAdminAccessTokenProvider {
    private static final byte[] TOKEN_REQUEST = "grant_type=client_credentials".getBytes(StandardCharsets.US_ASCII);

    private final Settings settings;
    private final SecretRefAccess secrets;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;
    private final Clock clock;
    private volatile CachedToken cached;

    public ClientSecretKeycloakAdminAccessTokenProvider(
            Settings settings,
            SecretRefAccess secrets,
            ObjectMapper objectMapper) {
        this(settings, secrets, objectMapper,
                HttpClient.newBuilder().connectTimeout(settings.timeout()).build(), Clock.systemUTC());
    }

    ClientSecretKeycloakAdminAccessTokenProvider(
            Settings settings,
            SecretRefAccess secrets,
            ObjectMapper objectMapper,
            HttpClient httpClient,
            Clock clock) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.secrets = Objects.requireNonNull(secrets, "secrets");
        this.mapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public synchronized String accessToken() {
        Instant now = clock.instant();
        if (cached != null && cached.refreshAfter().isAfter(now)) {
            return cached.value();
        }
        CachedToken refreshed = secrets.withSecret(settings.adminCredentialRef(), secret -> requestToken(secret, now));
        cached = refreshed;
        return refreshed.value();
    }

    @Override
    public synchronized void invalidate(String rejectedToken) {
        if (cached != null && Objects.equals(cached.value(), rejectedToken)) {
            cached = null;
        }
    }

    private CachedToken requestToken(byte[] mountedSecret, Instant now) {
        byte[] secret = trimAsciiWhitespace(mountedSecret);
        byte[] client = settings.adminClientId().getBytes(StandardCharsets.UTF_8);
        byte[] basicInput = new byte[client.length + 1 + secret.length];
        System.arraycopy(client, 0, basicInput, 0, client.length);
        basicInput[client.length] = ':';
        System.arraycopy(secret, 0, basicInput, client.length + 1, secret.length);
        String authorization = "Basic " + Base64.getEncoder().encodeToString(basicInput);
        Arrays.fill(secret, (byte) 0);
        Arrays.fill(client, (byte) 0);
        Arrays.fill(basicInput, (byte) 0);

        HttpRequest request = HttpRequest.newBuilder(settings.tokenEndpoint())
                .timeout(settings.timeout())
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Authorization", authorization)
                .POST(HttpRequest.BodyPublishers.ofByteArray(TOKEN_REQUEST))
                .build();
        byte[] body = null;
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            body = response.body();
            if (response.statusCode() != 200) {
                throw new RuntimeWorkloadIdentityException(
                        "Keycloak workload administration authentication failed with sanitized status "
                                + response.statusCode());
            }
            JsonNode token = mapper.readTree(body);
            String value = token.path("access_token").asText("");
            long lifetime = token.path("expires_in").asLong(0);
            if (value.isBlank() || lifetime < 1 || lifetime > 86_400) {
                throw new RuntimeWorkloadIdentityException(
                        "Keycloak returned an invalid workload administration token response");
            }
            long refreshIn = Math.max(1, lifetime - Math.min(15, Math.max(1, lifetime / 4)));
            return new CachedToken(value, now.plusSeconds(refreshIn));
        } catch (IOException exception) {
            throw new RuntimeWorkloadIdentityException(
                    "Keycloak workload administration authentication is unavailable", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeWorkloadIdentityException(
                    "Keycloak workload administration authentication was interrupted", exception);
        } finally {
            if (body != null) {
                Arrays.fill(body, (byte) 0);
            }
        }
    }

    private static byte[] trimAsciiWhitespace(byte[] value) {
        int start = 0;
        int end = value.length;
        while (start < end && whitespace(value[start])) {
            start++;
        }
        while (end > start && whitespace(value[end - 1])) {
            end--;
        }
        if (start == end) {
            throw new RuntimeWorkloadIdentityException("The Keycloak administration SecretRef is empty");
        }
        return Arrays.copyOfRange(value, start, end);
    }

    private static boolean whitespace(byte value) {
        return value == ' ' || value == '\t' || value == '\r' || value == '\n';
    }

    public record Settings(
            URI adminBaseUrl,
            String realm,
            String adminClientId,
            String adminCredentialRef,
            Duration timeout) {
        public Settings {
            if (adminBaseUrl == null
                    || adminBaseUrl.getHost() == null
                    || !("http".equalsIgnoreCase(adminBaseUrl.getScheme())
                        || "https".equalsIgnoreCase(adminBaseUrl.getScheme()))) {
                throw new IllegalArgumentException("adminBaseUrl must be an absolute HTTP(S) URI");
            }
            if (realm == null || realm.isBlank() || realm.contains("/")) {
                throw new IllegalArgumentException("realm is required");
            }
            if (adminClientId == null || adminClientId.isBlank()) {
                throw new IllegalArgumentException("adminClientId is required");
            }
            if (adminCredentialRef == null || !adminCredentialRef.startsWith("credentialref://")) {
                throw new IllegalArgumentException("adminCredentialRef must be a credentialref URI");
            }
            if (timeout == null || timeout.isZero() || timeout.isNegative()) {
                throw new IllegalArgumentException("timeout must be positive");
            }
        }

        URI tokenEndpoint() {
            String encodedRealm = URLEncoder.encode(realm, StandardCharsets.UTF_8).replace("+", "%20");
            return adminBaseUrl.resolve("/realms/" + encodedRealm + "/protocol/openid-connect/token");
        }
    }

    private record CachedToken(String value, Instant refreshAfter) {}
}

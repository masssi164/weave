package com.massimotter.weave.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class HttpMcpAuthorizationAdaptersTest {
    private static final String ISSUER = "https://auth.weave.test/realms/weave";
    private static final String MCP_RESOURCE = "https://api.weave.test/mcp";
    private static final String API_RESOURCE = "https://api.weave.test/api";
    private static final String EDGE = "weave-mcp-server";
    private static final String CLIENT = "weaver-cell-test";
    private static final String SUBJECT = "service-account-cell-subject";

    @TempDir
    Path temporary;

    private HttpServer server;
    private JsonMapper mapper;
    private Path secretFile;
    private Instant now;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mapper = JsonMapper.builder().build();
        secretFile = temporary.resolve("mcp-edge.secret");
        Files.writeString(secretFile, "edge+secret/=\n", StandardCharsets.UTF_8);
        try {
            Files.setPosixFilePermissions(secretFile, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException ignored) {
            // The production adapter retains regular-file, no-symlink, and readability checks.
        }
        now = Instant.now().minusSeconds(1);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void exchangesByRfc8693WithoutRelayingTheTokenOrRequestingOfflineAuthority() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<Map<String, String>> form = new AtomicReference<>();
        String outputToken = jwt(Map.of(
                "sub", SUBJECT,
                "azp", EDGE,
                "client_id", EDGE,
                "aud", API_RESOURCE,
                "scope", "calendar.read",
                "iat", now.getEpochSecond(),
                "exp", now.plusSeconds(30).getEpochSecond(),
                "jti", "exchange-jti"));
        server.createContext("/token", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            form.set(form(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            byte[] response = mapper.writeValueAsBytes(Map.of(
                    "access_token", outputToken,
                    "issued_token_type", "urn:ietf:params:oauth:token-type:access_token",
                    "token_type", "Bearer",
                    "expires_in", 30));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        McpCellWorkloadPrincipal workload = workload();
        ExchangedAccessToken result = new HttpMcpBackendTokenExchange(properties("/token", "/context"), mapper)
                .exchange(workload, "incoming.cell.token", Set.of("calendar.read"));

        assertThat(authorization.get()).isEqualTo("Basic " + Base64.getEncoder().encodeToString(
                (EDGE + ":edge%2Bsecret%2F%3D").getBytes(StandardCharsets.UTF_8)));
        assertThat(form.get()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "audience", API_RESOURCE,
                "grant_type", "urn:ietf:params:oauth:grant-type:token-exchange",
                "requested_token_type", "urn:ietf:params:oauth:token-type:access_token",
                "scope", "calendar.read",
                "subject_token", "incoming.cell.token",
                "subject_token_type", "urn:ietf:params:oauth:token-type:access_token"));
        assertThat(form.get()).doesNotContainKeys("resource", "actor_token", "requested_subject", "client_secret");
        assertThat(result.value()).isEqualTo(outputToken);
        assertThat(result.subject()).isEqualTo(SUBJECT);
        assertThat(result.authorizedParty()).isEqualTo(EDGE);
        assertThat(result.audiences()).containsExactly(API_RESOURCE);
        assertThat(result.scopes()).containsExactly("calendar.read");
        assertThat(result.toString()).doesNotContain(outputToken).isEqualTo("ExchangedAccessToken[redacted]");
    }

    @Test
    void rejectsAnUpscopedOrIdentityChangingExchangeResponse() throws Exception {
        String invalidToken = jwt(Map.of(
                "sub", "different-workload",
                "azp", EDGE,
                "aud", API_RESOURCE,
                "scope", "calendar.read calendar.manage_events",
                "iat", now.getEpochSecond(),
                "exp", now.plusSeconds(30).getEpochSecond(),
                "jti", "invalid-jti"));
        server.createContext("/token", exchange -> {
            byte[] response = mapper.writeValueAsBytes(Map.of(
                    "access_token", invalidToken,
                    "token_type", "Bearer",
                    "expires_in", 30));
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        assertThatThrownBy(() -> new HttpMcpBackendTokenExchange(properties("/token", "/context"), mapper)
                .exchange(workload(), "incoming.cell.token", Set.of("calendar.read")))
                .isInstanceOfSatisfying(McpAdmissionException.class,
                        failure -> assertThat(failure.kind()).isEqualTo(McpAdmissionException.Kind.FORBIDDEN))
                .hasMessageNotContaining(invalidToken);
    }

    @Test
    void resolvesOnlyTheExactSupportSafeBackendContextUsingTheExchangedToken() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        String workloadHash = fingerprint(ISSUER + "\u0000" + SUBJECT + "\u0000" + CLIENT);
        server.createContext("/context", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = mapper.writeValueAsBytes(Map.ofEntries(
                    Map.entry("schema", "weave.mcp-workload-context/v2"),
                    Map.entry("authorizationRef", "mcp-authz:" + "a".repeat(64)),
                    Map.entry("organizationRef", "org:test"),
                    Map.entry("cellRef", "cell:test"),
                    Map.entry("workloadClientId", CLIENT),
                    Map.entry("workloadRefHash", workloadHash),
                    Map.entry("runtimeProfileId", "rp_test"),
                    Map.entry("runtimeProfileHash", "sha256:" + "b".repeat(64)),
                    Map.entry("entitlementRevision", "sha256:" + "c".repeat(64)),
                    Map.entry("authorizationExpiresAt", now.plusSeconds(30).toString()),
                    Map.entry("grantedScopes", List.of("calendar.read")),
                    Map.entry("visibleToolClasses", List.of("calendar.read"))));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        ExchangedAccessToken token = exchanged("backend.only.token");

        McpBackendContext context = new HttpMcpBackendContextResolver(
                properties("/token", "/context"), mapper).resolve(workload(), token);

        assertThat(authorization.get()).isEqualTo("Bearer backend.only.token");
        assertThat(context.workloadRefHash()).isEqualTo(workloadHash);
        assertThat(context.grantedScopes()).containsExactly("calendar.read");
        assertThat(context.visibleToolClasses()).containsExactly("calendar.read");
    }

    @Test
    void rejectsBackendContextWithUnknownFieldsOrCrossCellBinding() throws Exception {
        server.createContext("/context", exchange -> {
            byte[] response = mapper.writeValueAsBytes(Map.ofEntries(
                    Map.entry("schema", "weave.mcp-workload-context/v2"),
                    Map.entry("authorizationRef", "mcp-authz:" + "a".repeat(64)),
                    Map.entry("organizationRef", "org:test"),
                    Map.entry("cellRef", "cell:other"),
                    Map.entry("workloadClientId", "weaver-cell-other"),
                    Map.entry("workloadRefHash", "sha256:" + "d".repeat(64)),
                    Map.entry("runtimeProfileId", "rp_test"),
                    Map.entry("runtimeProfileHash", "sha256:" + "b".repeat(64)),
                    Map.entry("entitlementRevision", "sha256:" + "c".repeat(64)),
                    Map.entry("authorizationExpiresAt", now.plusSeconds(30).toString()),
                    Map.entry("grantedScopes", List.of("calendar.read")),
                    Map.entry("visibleToolClasses", List.of("calendar.read")),
                    Map.entry("unexpected", "provider-secret")));
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        assertThatThrownBy(() -> new HttpMcpBackendContextResolver(
                properties("/token", "/context"), mapper).resolve(workload(), exchanged("backend.only.token")))
                .isInstanceOfSatisfying(McpAdmissionException.class,
                        failure -> assertThat(failure.kind()).isEqualTo(McpAdmissionException.Kind.FORBIDDEN))
                .hasMessageNotContaining("provider-secret");
    }

    private McpWorkloadProperties properties(String tokenPath, String contextPath) {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        return new McpWorkloadProperties(
                URI.create(MCP_RESOURCE),
                URI.create("https://api.weave.test/.well-known/oauth-protected-resource/mcp"),
                URI.create(ISSUER),
                List.of("mcp:tools", "calendar.read"),
                URI.create(base + tokenPath),
                EDGE,
                secretFile.toAbsolutePath(),
                URI.create(API_RESOURCE),
                URI.create(base + contextPath),
                List.of("calendar.read"),
                Duration.ofSeconds(2),
                Duration.ofSeconds(60),
                8192);
    }

    private McpCellWorkloadPrincipal workload() {
        return new McpCellWorkloadPrincipal(
                ISSUER,
                SUBJECT,
                CLIENT,
                Set.of("mcp:tools", "calendar.read"),
                now,
                now.plusSeconds(45),
                "cell-jti");
    }

    private ExchangedAccessToken exchanged(String value) {
        return new ExchangedAccessToken(
                value,
                SUBJECT,
                EDGE,
                Set.of(API_RESOURCE),
                Set.of("calendar.read"),
                now,
                now.plusSeconds(30));
    }

    private static String jwt(Map<String, Object> claims) throws Exception {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        JsonMapper mapper = JsonMapper.builder().build();
        return encoder.encodeToString(mapper.writeValueAsBytes(Map.of("alg", "RS256", "typ", "at+jwt")))
                + "." + encoder.encodeToString(mapper.writeValueAsBytes(claims))
                + "." + encoder.encodeToString("signature".getBytes(StandardCharsets.US_ASCII));
    }

    private static Map<String, String> form(String body) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String pair : body.split("&")) {
            String[] parts = pair.split("=", 2);
            values.put(
                    URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }
        return values;
    }

    private static String fingerprint(String value) throws Exception {
        return "sha256:" + HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}

package com.massimotter.weave.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.massimotter.weave.backend.agentruntime.adapter.FileRuntimeWorkloadCredentialStore;
import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpringSecurityKeycloakAdminAccessTokenProviderTest {
    private static final String CREDENTIAL_REF = "credentialref://weave/agent-runtime/admin/keycloak";

    @TempDir
    Path temporary;

    private HttpServer server;
    private AtomicInteger requests;
    private AtomicReference<String> authorization;
    private AtomicReference<String> requestBody;

    @BeforeEach
    void setUp() throws Exception {
        requests = new AtomicInteger();
        authorization = new AtomicReference<>();
        requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();

        Path secret = temporary.resolve("weave/agent-runtime/admin/keycloak");
        Files.createDirectories(secret.getParent());
        Files.writeString(
                secret,
                new RSAKeyGenerator(2048)
                        .keyID("identity-admin-test")
                        .algorithm(JWSAlgorithm.PS256)
                        .generate()
                        .toJSONString(),
                StandardCharsets.UTF_8);
        if (Files.getFileStore(secret).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(secret.getParent(), PosixFilePermissions.fromString("rwx------"));
            Files.setPosixFilePermissions(secret, PosixFilePermissions.fromString("rw-------"));
        }
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void usesPrivateKeyJwtOnlyAndCachesShortLivedTokens() throws Exception {
        server.createContext("/realms/weave/protocol/openid-connect/token", exchange -> {
            int sequence = requests.incrementAndGet();
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.US_ASCII));
            respond(exchange, 200, "{\"access_token\":\"admin-token-" + sequence
                    + "\",\"token_type\":\"Bearer\",\"expires_in\":60}");
        });
        SpringSecurityKeycloakAdminAccessTokenProvider provider = provider();

        assertThat(provider.accessToken()).isEqualTo("admin-token-1");
        assertThat(provider.accessToken()).isEqualTo("admin-token-1");
        provider.invalidate("another-token");
        assertThat(provider.accessToken()).isEqualTo("admin-token-1");
        provider.invalidate("admin-token-1");
        assertThat(provider.accessToken()).isEqualTo("admin-token-2");

        assertThat(requests).hasValue(2);
        assertThat(authorization.get()).isNull();
        assertThat(requestBody.get())
                .contains("grant_type=client_credentials")
                .contains("client_assertion_type=")
                .contains("client_assertion=")
                .doesNotContain("client_secret");
        String assertion = java.util.Arrays.stream(requestBody.get().split("&"))
                .filter(value -> value.startsWith("client_assertion="))
                .map(value -> URLDecoder.decode(
                        value.substring("client_assertion=".length()), StandardCharsets.UTF_8))
                .findFirst()
                .orElseThrow();
        String claims = new String(
                Base64.getUrlDecoder().decode(assertion.split("\\.")[1]),
                StandardCharsets.UTF_8);
        assertThat(claims).contains("\"aud\":\"https://auth.weave.local/realms/weave\"");
    }

    @Test
    void rejectsKeycloakErrorsWithoutLeakingTheSecretOrResponseBody() {
        server.createContext("/realms/weave/protocol/openid-connect/token", exchange ->
                respond(exchange, 401, "private-provider-diagnostic identity-admin-test"));

        assertThatThrownBy(() -> provider().accessToken())
                .isInstanceOf(RuntimeWorkloadIdentityException.class)
                .hasMessageContaining("authentication failed")
                .hasMessageNotContaining("private-provider-diagnostic")
                .hasMessageNotContaining("identity-admin-test");
    }

    @Test
    void rejectsPublicOnlyOrMalformedJwksAtTheServerPrivateKeyBoundary() throws Exception {
        Path secret = temporary.resolve("weave/agent-runtime/admin/keycloak");
        Files.writeString(
                secret,
                "{\"kty\":\"RSA\",\"alg\":\"PS256\",\"kid\":\"public-only\",\"n\":\"n\",\"e\":\"AQAB\"}",
                StandardCharsets.UTF_8);

        assertThatThrownBy(() -> provider().accessToken())
                .isInstanceOf(RuntimeWorkloadIdentityException.class)
                .hasMessage("Keycloak workload administration private JWK is invalid")
                .hasMessageNotContaining("public-only");
        assertThat(requests).hasValue(0);
    }

    private SpringSecurityKeycloakAdminAccessTokenProvider provider() {
        FileRuntimeWorkloadCredentialStore secrets =
                new FileRuntimeWorkloadCredentialStore(temporary, new ObjectMapper());
        URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        SpringSecurityKeycloakAdminAccessTokenProvider.Settings settings =
                new SpringSecurityKeycloakAdminAccessTokenProvider.Settings(
                        base,
                        "weave",
                        "weave-identity-admin",
                        CREDENTIAL_REF,
                        URI.create("https://auth.weave.local/realms/weave"),
                        Duration.ofSeconds(2));
        return new SpringSecurityKeycloakAdminAccessTokenProvider(settings, secrets);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}

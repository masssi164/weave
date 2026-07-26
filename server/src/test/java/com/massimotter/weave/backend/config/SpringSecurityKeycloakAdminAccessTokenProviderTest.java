package com.massimotter.weave.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.agentruntime.adapter.FileRuntimeWorkloadCredentialStore;
import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
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
        Files.writeString(secret, "admin-secret\n", StandardCharsets.UTF_8);
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
    void readsTheLongLivedSecretOnlyThroughItsRefAndCachesShortLivedTokens() throws Exception {
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
        assertThat(requestBody).hasValue("grant_type=client_credentials");
        assertThat(authorization.get()).isEqualTo("Basic " + Base64.getEncoder()
                .encodeToString("weave-identity-admin:admin-secret".getBytes(StandardCharsets.UTF_8)));
        assertThat(Files.readString(temporary.resolve("weave/agent-runtime/admin/keycloak")))
                .isEqualTo("admin-secret\n");
    }

    @Test
    void rejectsKeycloakErrorsWithoutLeakingTheSecretOrResponseBody() {
        server.createContext("/realms/weave/protocol/openid-connect/token", exchange ->
                respond(exchange, 401, "private-provider-diagnostic admin-secret"));

        assertThatThrownBy(() -> provider().accessToken())
                .isInstanceOf(RuntimeWorkloadIdentityException.class)
                .hasMessageContaining("authentication failed")
                .hasMessageNotContaining("private-provider-diagnostic")
                .hasMessageNotContaining("admin-secret");
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

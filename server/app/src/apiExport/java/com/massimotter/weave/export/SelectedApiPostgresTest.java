package com.massimotter.weave.export;

import com.massimotter.weave.backend.support.HumanJwtTestSupport;
import com.massimotter.weave.backend.support.SignedJwtTestSupport;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;

/** Real selected executable, database, JWT decoder and generated Flutter consumer. */
@Tag("postgres")
@Testcontainers
class SelectedApiPostgresTest {
    private static final String ISSUER = "https://auth.weave.test/realms/weave";
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.9-alpine");
    @TempDir(cleanup = org.junit.jupiter.api.io.CleanupMode.ON_SUCCESS) Path temporary;

    @Test
    void packagedMemberBootstrapAndGeneratedConsumerRespectInstallation() throws Exception {
        RSAKey signingKey = SignedJwtTestSupport.rsaSigningKey();
        try (var jwks = SignedJwtTestSupport.JwksServer.start(signingKey)) {
            int port = availablePort();
            Path log = temporary.resolve("application.log");
            Process application = startApplication(port, jwks.jwkSetUri(), log, List.of());
            try {
                awaitHttp(application, port, log);
                SelectedApiExportTest.assertAndExport(port);
                String memberToken = memberToken(signingKey, HumanJwtTestSupport.organizationWithRole("member"));
                assertThat(get(port, "/api/workspace/capabilities", null).statusCode()).isEqualTo(401);
                assertThat(get(port, "/api/workspace/capabilities", memberToken(signingKey,
                        Map.of("another-org", Map.of("id", "wrong-organization", "resource_access",
                                Map.of("weave-app", Map.of("roles", List.of("member"))))))).statusCode()).isEqualTo(403);
                assertThat(get(port, "/api/workspace/capabilities", memberToken).statusCode()).isEqualTo(200);
                // A User API token remains invalid for the separate Admin API client/audience.
                assertThat(get(port, "/api/admin/providers/status", memberToken).statusCode()).isEqualTo(401);
                runGeneratedFlutterConsumer(port, memberToken);
            } finally {
                stop(application);
            }
            if (List.of(System.getProperty("weave.domains").split(",")).contains("chat")) {
                int invalidPort = availablePort();
                Path invalidLog = temporary.resolve("invalid-chat-application.log");
                Process invalid = startApplication(invalidPort, jwks.jwkSetUri(), invalidLog,
                        List.of("--weave.matrix.facade.base-url=http://unsafe.example"));
                try {
                    awaitHttp(invalid, invalidPort, invalidLog);
                    for (String endpoint : List.of("/api/platform/config", "/api/platform/status")) {
                        var response = get(invalidPort, endpoint, null);
                        assertThat(response.statusCode()).as(endpoint).isEqualTo(503);
                        assertThat(response.body()).contains("matrix-facade-origin-unavailable");
                    }
                } finally {
                    stop(invalid);
                }
            }
        }
    }

    private Process startApplication(int port, String jwks, Path log, List<String> overrides) throws Exception {
        var command = new ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        copyProperty(command, "weave.matrix.protocol.library.path");
        copyProperty(command, "weave.identity.invitations.keycloak.reference-hmac-secret-file");
        command.addAll(List.of("-jar", System.getProperty("weave.selected.jar"),
                "--server.address=127.0.0.1", "--server.port=" + port,
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--spring.datasource.driver-class-name=" + POSTGRES.getDriverClassName(),
                "--spring.datasource.hikari.maximum-pool-size=2", "--spring.datasource.hikari.minimum-idle=0",
                // A new disposable database only; never an operator-provided schema.
                "--spring.jpa.hibernate.ddl-auto=create-drop",
                "--spring.security.oauth2.resourceserver.jwt.issuer-uri=" + ISSUER,
                "--spring.security.oauth2.resourceserver.jwt.jwk-set-uri=" + jwks,
                "--weave.security.primary-organization.keycloak-id=" + HumanJwtTestSupport.ORGANIZATION_ID,
                "--weave.security.primary-organization.keycloak-alias=" + HumanJwtTestSupport.ORGANIZATION_ALIAS));
        command.addAll(overrides);
        return new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
    }

    private void runGeneratedFlutterConsumer(int port, String token) throws Exception {
        Path tokenFile = temporary.resolve("member-token");
        Files.createFile(tokenFile, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        Files.writeString(tokenFile, token);
        Path log = temporary.resolve("generated-consumer.log");
        var builder = new ProcessBuilder("flutter", "test", "--no-pub", "test/core/router/app_router_test.dart",
                "--plain-name", "selected packaged server bootstraps and navigates through generated consumers",
                "--reporter", "expanded")
                .directory(Path.of(System.getProperty("weave.repository.root"), "client").toFile())
                .redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().put("WEAVE_SELECTED_TEST_ORIGIN", "http://127.0.0.1:" + port);
        builder.environment().put("WEAVE_SELECTED_TEST_DOMAINS", System.getProperty("weave.domains"));
        builder.environment().put("WEAVE_SELECTED_TEST_TOKEN_FILE", tokenFile.toString());
        Process consumer = builder.start();
        try {
            assertThat(consumer.waitFor(120, TimeUnit.SECONDS)).as("Flutter consumer timeout; inspect %s", log).isTrue();
            assertThat(consumer.exitValue()).as("Generated consumer/bootstrap/navigation; inspect %s", log).isZero();
        } finally {
            // Flutter launches compiler/tester children; reclaim them on a timeout too.
            consumer.descendants().forEach(ProcessHandle::destroyForcibly);
            if (consumer.isAlive()) {
                consumer.destroyForcibly();
                consumer.waitFor(5, TimeUnit.SECONDS);
            }
            Files.deleteIfExists(tokenFile);
        }
    }

    private static String memberToken(RSAKey signingKey, Map<String, Object> organization) throws Exception {
        Instant now = Instant.now();
        var claims = new JWTClaimsSet.Builder().issuer(ISSUER).subject("selected-ordinary-member")
                .audience("https://api.weave.test/api").claim("azp", "weave-app")
                .claim("scope", "weave:workspace").claim("weave_tenant_id", "tenant-default")
                .claim("organization", organization).issueTime(Date.from(now))
                .notBeforeTime(Date.from(now.minusSeconds(30))).expirationTime(Date.from(now.plusSeconds(600))).build();
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }

    private static HttpResponse<String> get(int port, String path, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(10));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return HttpClient.newHttpClient().send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static int availablePort() throws Exception {
        try (var socket = new ServerSocket(0)) { return socket.getLocalPort(); }
    }

    private static void stop(Process application) throws Exception {
        application.destroy();
        if (!application.waitFor(15, TimeUnit.SECONDS)) {
            application.destroyForcibly();
            assertThat(application.waitFor(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static void copyProperty(ArrayList<String> command, String key) {
        String value = System.getProperty(key);
        if (value != null && !value.isBlank()) command.add("-D" + key + "=" + value);
    }

    private static void awaitHttp(Process application, int port, Path log) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (application.isAlive() && System.nanoTime() < deadline) {
            try {
                if (get(port, "/api/health/live", null).statusCode() == 200) return;
            } catch (java.io.IOException unavailable) {
                // The operating-system listener is not ready yet.
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Selected packaged application did not start; inspect " + log);
    }
}

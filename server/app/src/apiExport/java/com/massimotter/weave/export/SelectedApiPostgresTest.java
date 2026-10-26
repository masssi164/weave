package com.massimotter.weave.export;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;

/** Starts the actual selected executable on disposable PostgreSQL, outside the test classloader. */
@Tag("postgres")
@Testcontainers
class SelectedApiPostgresTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.9-alpine");
    @TempDir(cleanup = org.junit.jupiter.api.io.CleanupMode.ON_SUCCESS) Path temporary;

    @Test
    void packagedApplicationStartsAndExportsOnlyItsSelectedHandlers() throws Exception {
        int port;
        try (var socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        var command = new ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        copyProperty(command, "weave.matrix.protocol.library.path");
        copyProperty(command, "weave.identity.invitations.keycloak.reference-hmac-secret-file");
        command.addAll(java.util.List.of("-jar", System.getProperty("weave.selected.jar"),
                "--server.address=127.0.0.1", "--server.port=" + port,
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--spring.datasource.driver-class-name=" + POSTGRES.getDriverClassName(),
                "--spring.datasource.hikari.maximum-pool-size=2", "--spring.datasource.hikari.minimum-idle=0",
                // This is a new disposable test database, never an operator-provided schema.
                "--spring.jpa.hibernate.ddl-auto=create-drop",
                "--spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.weave.test/realms/weave",
                "--spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://127.0.0.1:9/test-jwks",
                "--weave.security.primary-organization.keycloak-id=test-primary-organization",
                "--weave.security.primary-organization.keycloak-alias=weave-dogfood"));
        Path log = temporary.resolve("application.log");
        Process application = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            awaitHttp(application, port, log);
            SelectedApiExportTest.assertAndExport(port);
        } finally {
            // Close serving pools/Hibernate while PostgreSQL still exists; the container
            // extension stops infrastructure only after this method has fully returned.
            application.destroy();
            if (!application.waitFor(15, TimeUnit.SECONDS)) {
                application.destroyForcibly();
                assertThat(application.waitFor(5, TimeUnit.SECONDS)).isTrue();
            }
        }
    }

    private static void copyProperty(ArrayList<String> command, String key) {
        String value = System.getProperty(key);
        if (value != null && !value.isBlank()) command.add("-D" + key + "=" + value);
    }

    private static void awaitHttp(Process application, int port, Path log) throws Exception {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(250)).build();
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        while (application.isAlive() && System.nanoTime() < deadline) {
            try {
                var response = client.send(HttpRequest.newBuilder(URI.create(
                        "http://127.0.0.1:" + port + "/api/health/live"))
                        .timeout(Duration.ofSeconds(1)).GET().build(), HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() == 200) return;
            } catch (java.io.IOException unavailable) {
                // The operating-system listener is not ready yet.
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Selected packaged application did not start; inspect " + log);
    }
}

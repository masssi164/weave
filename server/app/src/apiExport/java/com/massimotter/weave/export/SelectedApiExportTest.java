package com.massimotter.weave.export;

import com.massimotter.weave.backend.WeaveBackendApplication;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;

/** Boots only the selected production graph; no integration-test fixtures enter this classpath. */
@SpringBootTest(classes = WeaveBackendApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:selected-api-export;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.hikari.maximum-pool-size=2",
        "spring.datasource.hikari.minimum-idle=0",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.weave.test/realms/weave",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://127.0.0.1:9/test-jwks",
        "weave.security.primary-organization.keycloak-id=test-primary-organization",
        "weave.security.primary-organization.keycloak-alias=weave-dogfood",
        "weave.identity.invitations.bootstrap-owner.enabled=true",
        "weave.identity.invitations.bootstrap-owner.token-file=/openapi-export/owner-bootstrap-token"
})
class SelectedApiExportTest {
    @LocalServerPort int port;

    @Test
    void exportActualSelectedHandlersToSeparateUserAndAdminDocuments() throws Exception {
        assertAndExport(port);
    }

    static void assertAndExport(int port) throws Exception {
        var mapper = new ObjectMapper();
        Path directory = Path.of(System.getProperty("weave.openapi.export.directory"));
        Files.createDirectories(directory);
        Set<String> selected = Arrays.stream(System.getProperty("weave.domains").split(","))
                .collect(Collectors.toUnmodifiableSet());
        for (var document : Map.of("weave-openapi", "/v3/api-docs",
                "weave-user-openapi", "/v3/api-docs/user",
                "weave-admin-openapi", "/v3/api-docs/admin").entrySet()) {
            var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                    URI.create("http://127.0.0.1:" + port + document.getValue())).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            String json = response.body();
            var schema = mapper.readTree(json);
            // The disposable HTTP port is not a deployment contract. Preserve the existing
            // generated-document origin while leaving any explicitly declared server untouched.
            for (var server : schema.path("servers")) {
                if ("Generated server url".equals(server.path("description").asString())) {
                    ((tools.jackson.databind.node.ObjectNode) server).put("url", "http://localhost");
                }
            }
            var paths = schema.path("paths");
            if (!document.getKey().equals("weave-admin-openapi")) {
                for (var domain : Map.of("files", "/api/files/readiness",
                        "calendar", "/api/calendar/scopes", "chat", "/api/chat/readiness").entrySet()) {
                    assertThat(paths.has(domain.getValue())).as("%s in %s", domain.getKey(), document.getKey())
                            .isEqualTo(selected.contains(domain.getKey()));
                }
            }
            Files.writeString(directory.resolve(document.getKey() + ".raw.json"), mapper.writeValueAsString(schema));
        }
    }
}

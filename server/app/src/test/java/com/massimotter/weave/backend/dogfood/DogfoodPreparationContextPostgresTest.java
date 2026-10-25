package com.massimotter.weave.backend.dogfood;

import static com.massimotter.weave.backend.dogfood.DogfoodPreparationProtocolTest.*;
import static org.assertj.core.api.Assertions.*;

import com.massimotter.weave.backend.schema.SchemaAuthorityTestSupport;
import com.massimotter.weave.backend.schema.ServingSchemaAuthorityGuard;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Actual non-web graph against migrated catalog/DML role. No identity request or mutation runs. */
@org.junit.jupiter.api.Tag("postgres")
@Testcontainers
class DogfoodPreparationContextPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test void exactMinimalCommandContextValidatesServingSchemaWithoutWebSchedulersOrProviderBootstraps(@TempDir Path directory) throws Exception {
        var r = request("invitations");
        var init = SchemaAuthorityTestSupport.withRuntimeRole(Map.of(
                "WEAVE_PERSISTENCE_URL", POSTGRES.getJdbcUrl(), "WEAVE_PERSISTENCE_USERNAME", POSTGRES.getUsername(),
                "WEAVE_PERSISTENCE_PASSWORD", POSTGRES.getPassword(), "WEAVE_CANDIDATE_COMMIT", r.candidateCommit(),
                "WEAVE_SCHEMA_INIT_RECEIPT_FILE", directory.resolve("schema-receipt.json").toString()));
        SchemaAuthorityTestSupport.initializeAndVerify(init);
        Path hmac = directory.resolve("identity-reference-key");
        Files.writeString(hmac, "test-only-not-a-live-secret-key-000000000000000000000000");
        var values = new LinkedHashMap<String, Object>();
        values.put("weave.candidate.commit", r.candidateCommit());
        values.put("spring.datasource.url", POSTGRES.getJdbcUrl());
        values.put("spring.datasource.username", init.get("WEAVE_SCHEMA_RUNTIME_ROLE"));
        values.put("spring.datasource.password", "test-only-runtime");
        values.put("spring.datasource.driver-class-name", "org.postgresql.Driver");
        values.put("spring.jpa.hibernate.ddl-auto", "validate");
        values.put("spring.flyway.enabled", "false");
        values.put("weave.platform.public-base-url", r.plan().organizationOrigin());
        values.put("weave.platform.api-base-url", r.apiBaseUrl());
        values.put("spring.security.oauth2.resourceserver.jwt.issuer-uri", r.plan().issuerUrl());
        values.put("weave.security.primary-organization.keycloak-id", "provider-org");
        values.put("weave.security.primary-organization.keycloak-alias", "weave");
        values.put("weave.context.authorization.default-tenant-id", "org");
        values.put("weave.identity.invitations.keycloak.organization-id", "provider-org");
        values.put("weave.identity.invitations.keycloak.organization-alias", "weave");
        values.put("weave.identity.invitations.keycloak.base-url", "https://uncontacted.example.test");
        values.put("weave.identity.invitations.keycloak.private-key-jwt-audience", ISSUER);
        values.put("weave.identity.invitations.keycloak.reference-hmac-secret-file", hmac.toString());
        values.put("weave.security.required-audience", "weave-app");
        var environment = new StandardEnvironment();
        environment.getPropertySources().replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new org.springframework.core.env.SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        Map.of("WEAVE_RELEASE_POSTURE", "dogfood", "WEAVE_CANDIDATE_COMMIT", r.candidateCommit())));
        environment.getPropertySources().addFirst(new MapPropertySource("owned-isolated-test", values));
        var app = DogfoodPreparationCli.application(r);
        app.setEnvironment(environment);
        try (var context = app.run()) {
            assertThat(context.getBean(DogfoodPreparationService.class)).isNotNull();
            assertThat(context.getBean(ServingSchemaAuthorityGuard.class)).isNotNull();
            assertThat(context).isNotInstanceOf(org.springframework.web.context.WebApplicationContext.class);
            assertThat(context.getBeansOfType(org.springframework.boot.ApplicationRunner.class)).isEmpty();
            assertThat(context.getBeansOfType(org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor.class)).isEmpty();
            assertThat(context.getBeansOfType(com.massimotter.weave.backend.config.WeavePersistenceConfiguration.class)).isEmpty();
            assertThat(context.getBeansOfType(com.massimotter.weave.backend.files.application.FilesProviderBindingBootstrap.class)).isEmpty();
            assertThat(context.getBeansOfType(com.massimotter.weave.backend.spaces.application.DefaultSpaceBootstrapService.class)).isEmpty();
            var source = context.getBean(javax.sql.DataSource.class);
            try (var connection = source.getConnection(); var rows = connection.createStatement().executeQuery(
                    "select count(*) from weave_identity_provisioning_intents")) {
                assertThat(rows.next()).isTrue(); assertThat(rows.getLong(1)).isZero();
            }
        }
    }
}

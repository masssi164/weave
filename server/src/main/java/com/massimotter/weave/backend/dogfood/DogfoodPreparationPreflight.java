package com.massimotter.weave.backend.dogfood;

import static com.massimotter.weave.backend.dogfood.DogfoodPreparationProtocol.*;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

/** Runs after configuration resolution and before refresh, datasource, Hibernate or any bean. */
final class DogfoodPreparationPreflight implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    private final Request request;
    DogfoodPreparationPreflight(Request request) { this.request = request; }

    @Override
    public void initialize(ConfigurableApplicationContext context) { validate(context.getEnvironment(), request); }

    private static Object inherited(Environment environment, String key) {
        if (!(environment instanceof org.springframework.core.env.ConfigurableEnvironment configurable)) return null;
        var source = configurable.getPropertySources().get(org.springframework.core.env.StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        if (!(source instanceof org.springframework.core.env.SystemEnvironmentPropertySource system)) return null;
        return system.getSource().get(key);
    }

    static void validate(Environment environment, Request request) {
        require("dogfood".equals(inherited(environment, "WEAVE_RELEASE_POSTURE")), "dogfood-posture-required");
        require(request.candidateCommit().equals(inherited(environment, "WEAVE_CANDIDATE_COMMIT")), "candidate-mismatch");
        require(request.candidateCommit().equals(environment.getProperty("weave.candidate.commit")), "candidate-mismatch");
        require("validate".equals(environment.getProperty("spring.jpa.hibernate.ddl-auto"))
                && "false".equals(environment.getProperty("spring.flyway.enabled")), "schema-authority-unavailable");
        require(!environment.getProperty("spring.jpa.generate-ddl", Boolean.class, false)
                && environment.getProperty("spring.main.sources", "").isBlank()
                && "none".equals(environment.getProperty("spring.main.web-application-type", "none")), "schema-authority-unavailable");
        for (String key : java.util.List.of("spring.jpa.properties.jakarta.persistence.schema-generation.database.action",
                "spring.jpa.properties.javax.persistence.schema-generation.database.action")) {
            require(java.util.Set.of("none", "").contains(environment.getProperty(key, "")), "schema-authority-unavailable");
        }
        require(java.util.Set.of("validate", "").contains(environment.getProperty("spring.jpa.properties.hibernate.hbm2ddl.auto", "")),
                "schema-authority-unavailable");
        require(java.util.Set.of("never", "embedded", "").contains(environment.getProperty("spring.sql.init.mode", ""))
                && environment.getProperty("spring.sql.init.schema-locations", "").isBlank()
                && environment.getProperty("spring.sql.init.data-locations", "").isBlank(), "schema-authority-unavailable");
        require(request.plan().organizationOrigin().equals(environment.getProperty("weave.platform.public-base-url"))
                && request.apiBaseUrl().equals(environment.getProperty("weave.platform.api-base-url"))
                && request.plan().issuerUrl().equals(environment.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri")),
                "configuration-mismatch");
        String org = environment.getProperty("weave.security.primary-organization.keycloak-id", "");
        require(!org.isBlank() && org.equals(environment.getProperty("weave.identity.invitations.keycloak.organization-id"))
                && !environment.getProperty("weave.context.authorization.default-tenant-id", "").isBlank(), "configuration-mismatch");
        require(environment.getProperty("weave.identity.invitations.keycloak.timeout", java.time.Duration.class,
                java.time.Duration.ofSeconds(10)).compareTo(java.time.Duration.ofSeconds(15)) <= 0, "configuration-mismatch");
    }
}

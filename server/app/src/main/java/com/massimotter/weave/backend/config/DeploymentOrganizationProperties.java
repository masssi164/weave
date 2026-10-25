package com.massimotter.weave.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Operator-owned Keycloak coordinate for this single-organization deployment. */
@ConfigurationProperties(prefix = "weave.security.primary-organization")
public record DeploymentOrganizationProperties(String keycloakId, String keycloakAlias) {
    public DeploymentOrganizationProperties {
        keycloakId = keycloakId == null ? "" : keycloakId.trim();
        keycloakAlias = keycloakAlias == null ? "" : keycloakAlias.trim();
    }

    public boolean configured() {
        return !keycloakId.isBlank() && !keycloakAlias.isBlank();
    }
}

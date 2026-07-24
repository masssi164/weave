package com.massimotter.weave.backend.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "weave.identity.invitations")
public class IdentityInvitationProperties {
    private Duration defaultLifetime = Duration.ofDays(7);
    private final Keycloak keycloak = new Keycloak();

    public Duration defaultLifetime() { return defaultLifetime; }
    public void setDefaultLifetime(Duration defaultLifetime) { this.defaultLifetime = defaultLifetime; }
    public Keycloak keycloak() { return keycloak; }

    public static class Keycloak {
        private URI baseUrl = URI.create("http://weave-keycloak:8080");
        private String realm = "weave";
        private String organizationId = "";
        private String organizationAlias = "weave";
        private String clientId = "weave-identity-admin";
        private String clientSecret = "";
        private Duration timeout = Duration.ofSeconds(10);

        public URI baseUrl() { return baseUrl; }
        public void setBaseUrl(URI baseUrl) { this.baseUrl = baseUrl; }
        public String realm() { return realm; }
        public void setRealm(String realm) { this.realm = realm; }
        public String organizationId() { return organizationId; }
        public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
        public String organizationAlias() { return organizationAlias; }
        public void setOrganizationAlias(String organizationAlias) { this.organizationAlias = organizationAlias; }
        public String clientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        public String clientSecret() { return clientSecret; }
        public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
        public Duration timeout() { return timeout; }
        public void setTimeout(Duration timeout) { this.timeout = timeout; }
    }
}

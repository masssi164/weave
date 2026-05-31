package com.massimotter.weave.backend.identity.realm;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "weave.identity.realm.apply")
public class IdentityRealmApplyProperties {

    private boolean liveApplyEnabled;
    private boolean providerConfigured;
    private boolean destructiveApplyEnabled;
    private long dryRunFreshnessSeconds = 1800;
    private String keycloakAdminBaseUrl;
    private String keycloakAdminToken;

    public boolean liveApplyEnabled() {
        return liveApplyEnabled;
    }

    public void setLiveApplyEnabled(boolean liveApplyEnabled) {
        this.liveApplyEnabled = liveApplyEnabled;
    }

    public boolean providerConfigured() {
        return providerConfigured && keycloakAdminBaseUri() != null && hasText(keycloakAdminToken);
    }

    public void setProviderConfigured(boolean providerConfigured) {
        this.providerConfigured = providerConfigured;
    }

    public boolean destructiveApplyEnabled() {
        return destructiveApplyEnabled;
    }

    public void setDestructiveApplyEnabled(boolean destructiveApplyEnabled) {
        this.destructiveApplyEnabled = destructiveApplyEnabled;
    }

    public long dryRunFreshnessSeconds() {
        return dryRunFreshnessSeconds;
    }

    public void setDryRunFreshnessSeconds(long dryRunFreshnessSeconds) {
        this.dryRunFreshnessSeconds = Math.max(60, dryRunFreshnessSeconds);
    }

    public String keycloakAdminBaseUrl() {
        return keycloakAdminBaseUrl;
    }

    public void setKeycloakAdminBaseUrl(String keycloakAdminBaseUrl) {
        this.keycloakAdminBaseUrl = keycloakAdminBaseUrl;
    }

    public String keycloakAdminToken() {
        return keycloakAdminToken;
    }

    public void setKeycloakAdminToken(String keycloakAdminToken) {
        this.keycloakAdminToken = keycloakAdminToken;
    }

    public URI keycloakAdminBaseUri() {
        if (!hasText(keycloakAdminBaseUrl)) {
            return null;
        }
        try {
            URI uri = URI.create(keycloakAdminBaseUrl.trim());
            return uri.getScheme() == null || uri.getHost() == null ? null : uri;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

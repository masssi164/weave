package com.massimotter.weave.backend.identity.realm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "weave.identity.realm.apply")
public class IdentityRealmApplyProperties {

    private boolean liveApplyEnabled;
    private boolean providerConfigured;
    private boolean destructiveApplyEnabled;
    private long dryRunFreshnessSeconds = 1800;

    public boolean liveApplyEnabled() {
        return liveApplyEnabled;
    }

    public void setLiveApplyEnabled(boolean liveApplyEnabled) {
        this.liveApplyEnabled = liveApplyEnabled;
    }

    public boolean providerConfigured() {
        return providerConfigured;
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
}

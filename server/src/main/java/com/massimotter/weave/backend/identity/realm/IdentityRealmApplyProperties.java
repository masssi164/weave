package com.massimotter.weave.backend.identity.realm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Review-evidence policy only; no Keycloak Admin REST location or credential is accepted. */
@Component
@ConfigurationProperties(prefix = "weave.identity.realm.apply")
public class IdentityRealmApplyProperties {
    private long dryRunFreshnessSeconds = 1800;

    public long dryRunFreshnessSeconds() {
        return dryRunFreshnessSeconds;
    }

    public void setDryRunFreshnessSeconds(long dryRunFreshnessSeconds) {
        this.dryRunFreshnessSeconds = Math.max(60, dryRunFreshnessSeconds);
    }
}

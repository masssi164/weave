package com.massimotter.weave.backend.config;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "weave.agent-runtime.profile-signing")
public class AgentRuntimeProfileSigningProperties {
    private boolean enabled;
    private Path secretRoot;
    private Duration keyLifetime = Duration.ofDays(365);
    private Duration trustOverlap = Duration.ofMinutes(10);
    private Duration maximumProfileTtl = Duration.ofMinutes(5);

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Path secretRoot() {
        return secretRoot;
    }

    public void setSecretRoot(Path secretRoot) {
        this.secretRoot = secretRoot;
    }

    public Duration keyLifetime() {
        return keyLifetime;
    }

    public void setKeyLifetime(Duration keyLifetime) {
        this.keyLifetime = keyLifetime;
    }

    public Duration trustOverlap() {
        return trustOverlap;
    }

    public void setTrustOverlap(Duration trustOverlap) {
        this.trustOverlap = trustOverlap;
    }

    public Duration maximumProfileTtl() {
        return maximumProfileTtl;
    }

    public void setMaximumProfileTtl(Duration maximumProfileTtl) {
        this.maximumProfileTtl = maximumProfileTtl;
    }

    public Path requiredSecretRoot() {
        if (secretRoot == null) {
            throw new IllegalStateException(
                    "RuntimeProfile signing requires an explicit operator-mounted SecretRef root");
        }
        return secretRoot;
    }
}

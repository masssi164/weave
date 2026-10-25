package com.massimotter.weave.backend.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "weave.agent-runtime.policy")
public class AgentRuntimePolicyProperties {
    private boolean enabled;
    private Path file;

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Path file() {
        return file;
    }

    public void setFile(Path file) {
        this.file = file;
    }

    public Path requiredFile() {
        if (file == null) {
            throw new IllegalStateException("Agent Runtime policy requires an explicit policy file");
        }
        return file;
    }
}

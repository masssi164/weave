package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.model.WorkspaceCapabilityReadiness;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "weave.workspace")

public record WorkspaceCapabilityProperties(
        Capability shellAccess,
        Capability chat,
        Capability files,
        Capability calendar,
        Capability boards) {

    public WorkspaceCapabilityProperties {
        shellAccess = defaultCapability(shellAccess, true, null, null);
        chat = defaultCapability(chat, true, null, null);
        files = defaultCapability(files, true, null, null);
        calendar = defaultCapability(calendar, false, null, null);
        boards = defaultCapability(boards, false, null, null);
    }

    private static Capability defaultCapability(
            Capability capability,
            boolean enabled,
            String dependencyUrl,
            WorkspaceCapabilityReadiness readiness) {
        if (capability == null) {
            return new Capability(enabled, dependencyUrl, readiness);
        }
        return new Capability(capability.enabled(), capability.dependencyUrl(), capability.readiness());
    }

    public record Capability(boolean enabled, String dependencyUrl, WorkspaceCapabilityReadiness readiness) {
    }
}

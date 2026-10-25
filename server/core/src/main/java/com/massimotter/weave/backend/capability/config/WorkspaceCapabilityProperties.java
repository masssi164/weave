package com.massimotter.weave.backend.capability.config;

import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityReadiness;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Platform-owned shell admission and administration policy only. */
@ConfigurationProperties(prefix = "weave.workspace")
public record WorkspaceCapabilityProperties(CapabilitySettings shellAccess, CapabilitySettings adminControlPlane) {
    public WorkspaceCapabilityProperties {
        shellAccess = shellAccess == null ? new CapabilitySettings(true, null, null) : shellAccess;
        adminControlPlane = adminControlPlane == null
                ? new CapabilitySettings(true, null, WorkspaceCapabilityReadiness.READY) : adminControlPlane;
    }
}

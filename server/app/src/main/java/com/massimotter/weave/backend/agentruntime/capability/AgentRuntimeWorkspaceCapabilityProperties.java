package com.massimotter.weave.backend.agentruntime.capability;

import com.massimotter.weave.backend.capability.config.CapabilitySettings;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityReadiness;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Owns the existing agentRuntimeControl setting and its default, independently of module selection. */
@ConfigurationProperties(prefix = "weave.workspace")
public record AgentRuntimeWorkspaceCapabilityProperties(CapabilitySettings agentRuntimeControl) {
    public AgentRuntimeWorkspaceCapabilityProperties {
        agentRuntimeControl = agentRuntimeControl == null ? new CapabilitySettings(false, null, null) : agentRuntimeControl;
    }
}

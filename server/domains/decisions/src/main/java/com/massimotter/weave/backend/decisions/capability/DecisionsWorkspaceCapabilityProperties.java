package com.massimotter.weave.backend.decisions.capability;

import com.massimotter.weave.backend.capability.config.CapabilitySettings;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityReadiness;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Owns the existing decisionsEvidence setting and its default, independently of module selection. */
@ConfigurationProperties(prefix = "weave.workspace")
public record DecisionsWorkspaceCapabilityProperties(CapabilitySettings decisionsEvidence) {
    public DecisionsWorkspaceCapabilityProperties {
        decisionsEvidence = decisionsEvidence == null ? new CapabilitySettings(true,
                null, WorkspaceCapabilityReadiness.READY) : decisionsEvidence;
    }
}

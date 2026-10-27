package com.massimotter.weave.backend.devops.capability;

import com.massimotter.weave.backend.capability.config.CapabilitySettings;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityReadiness;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Owns the existing releaseEvidence setting and its default, independently of module selection. */
@ConfigurationProperties(prefix = "weave.workspace")
public record DevopsWorkspaceCapabilityProperties(CapabilitySettings releaseEvidence) {
    public DevopsWorkspaceCapabilityProperties {
        releaseEvidence = releaseEvidence == null ? new CapabilitySettings(true, null,
                WorkspaceCapabilityReadiness.READY) : releaseEvidence;
    }
}

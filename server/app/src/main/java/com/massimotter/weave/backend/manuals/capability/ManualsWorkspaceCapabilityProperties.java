package com.massimotter.weave.backend.manuals.capability;

import com.massimotter.weave.backend.capability.config.CapabilitySettings;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityReadiness;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Owns the existing manualsHelp setting and its default, independently of module selection. */
@ConfigurationProperties(prefix = "weave.workspace")
public record ManualsWorkspaceCapabilityProperties(CapabilitySettings manualsHelp) {
    public ManualsWorkspaceCapabilityProperties {
        manualsHelp = manualsHelp == null ? new CapabilitySettings(true, null, WorkspaceCapabilityReadiness.READY) : manualsHelp;
    }
}

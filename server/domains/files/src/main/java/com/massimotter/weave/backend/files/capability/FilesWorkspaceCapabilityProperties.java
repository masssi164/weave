package com.massimotter.weave.backend.files.capability;

import com.massimotter.weave.backend.capability.config.CapabilitySettings;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityReadiness;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Owns the existing files setting and its default, independently of module selection. */
@ConfigurationProperties(prefix = "weave.workspace")
public record FilesWorkspaceCapabilityProperties(CapabilitySettings files) {
    public FilesWorkspaceCapabilityProperties {
        files = files == null ? new CapabilitySettings(true, null, null) : files;
    }
}

package com.massimotter.weave.backend.office.capability;

import com.massimotter.weave.backend.capability.config.CapabilitySettings;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityReadiness;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Owns the existing documentsCollaboration setting and its default, independently of module selection. */
@ConfigurationProperties(prefix = "weave.workspace")
public record OfficeWorkspaceCapabilityProperties(CapabilitySettings documentsCollaboration) {
    public OfficeWorkspaceCapabilityProperties {
        documentsCollaboration = documentsCollaboration == null ? new CapabilitySettings(false,
                null, null) : documentsCollaboration;
    }
}

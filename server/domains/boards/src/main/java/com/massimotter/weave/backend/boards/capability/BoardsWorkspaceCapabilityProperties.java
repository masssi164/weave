package com.massimotter.weave.backend.boards.capability;

import com.massimotter.weave.backend.capability.config.CapabilitySettings;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityReadiness;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Owns the existing boards setting and its default, independently of module selection. */
@ConfigurationProperties(prefix = "weave.workspace")
public record BoardsWorkspaceCapabilityProperties(CapabilitySettings boards) {
    public BoardsWorkspaceCapabilityProperties {
        boards = boards == null ? new CapabilitySettings(false, null, null) : boards;
    }
}

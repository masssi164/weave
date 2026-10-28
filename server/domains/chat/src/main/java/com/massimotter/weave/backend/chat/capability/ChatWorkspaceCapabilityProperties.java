package com.massimotter.weave.backend.chat.capability;

import com.massimotter.weave.backend.capability.config.CapabilitySettings;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Owns the existing chat setting and its default, independently of module selection. */
@ConfigurationProperties(prefix = "weave.workspace")
public record ChatWorkspaceCapabilityProperties(CapabilitySettings chat) {
    public ChatWorkspaceCapabilityProperties {
        chat = chat == null ? new CapabilitySettings(true, null, null) : chat;
    }
}

package com.massimotter.weave.backend.meetings.capability;

import com.massimotter.weave.backend.capability.config.CapabilitySettings;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Owns the existing meetingsCalls setting and its default, independently of module selection. */
@ConfigurationProperties(prefix = "weave.workspace")
public record MeetingsWorkspaceCapabilityProperties(CapabilitySettings meetingsCalls) {
    public MeetingsWorkspaceCapabilityProperties {
        meetingsCalls = meetingsCalls == null ? new CapabilitySettings(false, null, null) : meetingsCalls;
    }
}

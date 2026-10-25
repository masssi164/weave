package com.massimotter.weave.backend.calendar.capability;

import com.massimotter.weave.backend.capability.config.CapabilitySettings;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityReadiness;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Owns the existing calendar setting and its default, independently of module selection. */
@ConfigurationProperties(prefix = "weave.workspace")
public record CalendarWorkspaceCapabilityProperties(CapabilitySettings calendar) {
    public CalendarWorkspaceCapabilityProperties {
        calendar = calendar == null ? new CapabilitySettings(false, null, null) : calendar;
    }
}

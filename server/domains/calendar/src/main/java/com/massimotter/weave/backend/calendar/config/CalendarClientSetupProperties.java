package com.massimotter.weave.backend.calendar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Existing public DAV profile setting, separate from southbound provider credentials. */
@ConfigurationProperties("weave.calendar.caldav")
public record CalendarClientSetupProperties(String publicBaseUrl) {
    public CalendarClientSetupProperties {
        publicBaseUrl = publicBaseUrl == null || publicBaseUrl.isBlank()
                ? "https://calendar.weave.test" : publicBaseUrl.trim();
    }
}

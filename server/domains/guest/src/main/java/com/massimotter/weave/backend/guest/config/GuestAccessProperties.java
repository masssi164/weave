package com.massimotter.weave.backend.guest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "weave.guest")
public record GuestAccessProperties(boolean enabled) {
}

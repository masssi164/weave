package com.massimotter.weave.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "weave.migration")
public record MigrationToolkitProperties(boolean enabled) {
}

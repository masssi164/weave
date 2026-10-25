package com.massimotter.weave.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "weave.connectors")
public record ConnectorRuntimeProperties(boolean publicSdkEnabled) {
}

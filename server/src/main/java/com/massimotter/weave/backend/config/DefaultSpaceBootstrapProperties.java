package com.massimotter.weave.backend.config;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Exceptional development setup authority; never enabled by routine startup or production. */
@ConfigurationProperties(prefix = "weave.spaces.bootstrap")
public record DefaultSpaceBootstrapProperties(boolean enabled, String environment, String tokenFile) {
    public DefaultSpaceBootstrapProperties {
        environment = environment == null ? "prod" : environment.trim();
        tokenFile = tokenFile == null ? "" : tokenFile.trim();
        if (enabled && !Set.of("dogfood", "e2e").contains(environment)) {
            throw new IllegalArgumentException("Default Space bootstrap is restricted to development environments");
        }
    }
}

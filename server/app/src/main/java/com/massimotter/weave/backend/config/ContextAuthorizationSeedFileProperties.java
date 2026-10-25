package com.massimotter.weave.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Private startup-only Context membership seed used by disposable isolated E2E stacks. */
@ConfigurationProperties(prefix = "weave.context.authorization.seed")
public record ContextAuthorizationSeedFileProperties(String membershipsFile) {

    public ContextAuthorizationSeedFileProperties {
        membershipsFile = membershipsFile == null ? "" : membershipsFile.trim();
    }
}

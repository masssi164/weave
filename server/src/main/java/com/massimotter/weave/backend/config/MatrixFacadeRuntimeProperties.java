package com.massimotter.weave.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Runtime selection for the optional northbound Matrix Client-Server facade.
 *
 * <p>This switch is deliberately independent from {@code weave.chat.provider}:
 * selecting the Weave-native canonical Chat provider neither enables nor requires
 * a Matrix protocol edge, and enabling the Matrix edge does not select Synapse.
 */
@ConfigurationProperties(prefix = "weave.chat.matrix-facade")
public record MatrixFacadeRuntimeProperties(@DefaultValue("true") boolean enabled) {

    public static final String ENABLED_PROPERTY = "weave.chat.matrix-facade.enabled";
}

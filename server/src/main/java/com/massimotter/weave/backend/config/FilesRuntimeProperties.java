package com.massimotter.weave.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "weave.files")
public record FilesRuntimeProperties(String provider) {

    public static final String WEAVE_NATIVE = "weave-native";
    public static final String NEXTCLOUD_WEBDAV = "nextcloud-webdav";

    public FilesRuntimeProperties {
        provider = provider == null || provider.isBlank() ? WEAVE_NATIVE : provider.trim();
    }
}

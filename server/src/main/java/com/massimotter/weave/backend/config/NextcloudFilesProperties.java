package com.massimotter.weave.backend.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "weave.nextcloud.files")
public record NextcloudFilesProperties(
        String baseUrl,
        String webdavRootPath,
        String actorModel,
        String actorUsername,
        String actorToken) {

    public NextcloudFilesProperties {
        baseUrl = defaultIfBlank(baseUrl, "https://files.weave.local");
        webdavRootPath = normalizeWebdavRootPath(defaultIfBlank(webdavRootPath, "/remote.php/dav/files"));
        actorModel = defaultIfBlank(actorModel, "backend-service-account");
        actorUsername = trimToNull(actorUsername);
        actorToken = trimToNull(actorToken);
    }

    public boolean isConfigured() {
        return StringUtils.hasText(baseUrl)
                && isSupportedActorModel()
                && StringUtils.hasText(actorUsername)
                && StringUtils.hasText(actorToken)
                && isAbsoluteHttpBaseUrl();
    }

    public boolean isSupportedActorModel() {
        return "backend-service-account".equals(actorModel);
    }

    public URI baseUri() {
        return URI.create(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
    }

    private boolean isAbsoluteHttpBaseUrl() {
        try {
            URI uri = URI.create(baseUrl);
            return uri.isAbsolute()
                    && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String normalizeWebdavRootPath(String value) {
        String trimmed = value.trim();
        String withLeadingSlash = trimmed.startsWith("/") ? trimmed : "/" + trimmed;
        if (withLeadingSlash.length() > 1 && withLeadingSlash.endsWith("/")) {
            return withLeadingSlash.substring(0, withLeadingSlash.length() - 1);
        }
        return withLeadingSlash;
    }

    private static String defaultIfBlank(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

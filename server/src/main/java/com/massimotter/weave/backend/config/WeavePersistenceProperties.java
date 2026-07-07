package com.massimotter.weave.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "weave.persistence.jdbc")
public record WeavePersistenceProperties(
        String url,
        String username,
        String password,
        String driverClassName) {

    public String requiredUrl() {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException(
                    "JDBC persistence is enabled but weave.persistence.jdbc.url is not configured.");
        }
        return url.trim();
    }

    public String normalizedUsername() {
        return username == null ? "" : username.trim();
    }

    public String normalizedPassword() {
        return password == null ? "" : password;
    }

    public String normalizedDriverClassName() {
        return driverClassName == null ? "" : driverClassName.trim();
    }
}

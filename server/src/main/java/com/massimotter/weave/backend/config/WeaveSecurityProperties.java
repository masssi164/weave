package com.massimotter.weave.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "weave.security")
public record WeaveSecurityProperties(String requiredAudience, String clientId) {

    private static final String DEFAULT_FIRST_PARTY_CLIENT_ID = "weave-app";

    public WeaveSecurityProperties {
        requiredAudience = defaultIfBlank(requiredAudience);
        clientId = defaultIfBlank(clientId);
    }

    public boolean hasRequiredAudience() {
        return requiredAudience != null && !requiredAudience.isBlank();
    }

    public String requiredAuthorizedParty() {
        return clientId;
    }

    public boolean hasRequiredAuthorizedParty() {
        return clientId != null && !clientId.isBlank();
    }

    private static String defaultIfBlank(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_FIRST_PARTY_CLIENT_ID;
        }
        return value.trim();
    }
}

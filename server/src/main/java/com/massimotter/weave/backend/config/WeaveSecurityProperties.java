package com.massimotter.weave.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "weave.security")
public record WeaveSecurityProperties(String requiredAudience, String clientId) {

    private static final String DEFAULT_FIRST_PARTY_CLIENT_ID = "weave-app";
    private static final String DEFAULT_BACKEND_AUDIENCE = "weave-backend";
    @ConstructorBinding
    public WeaveSecurityProperties {
        requiredAudience = defaultIfBlank(requiredAudience, DEFAULT_BACKEND_AUDIENCE);
        clientId = defaultIfBlank(clientId, DEFAULT_FIRST_PARTY_CLIENT_ID);
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

    private static String defaultIfBlank(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }
}

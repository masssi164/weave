package com.massimotter.weave.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "weave.interop")
public record InteropGatewayProperties(
        boolean enabled,
        Provider slack,
        Provider teams,
        String supportBundleRedactionMode) {

    public InteropGatewayProperties {
        slack = slack == null ? Provider.disabled() : slack;
        teams = teams == null ? Provider.disabled() : teams;
        supportBundleRedactionMode = supportBundleRedactionMode == null || supportBundleRedactionMode.isBlank()
                ? "support-safe-redacted"
                : supportBundleRedactionMode.trim();
    }

    public record Provider(
            boolean enabled,
            String clientId,
            String clientSecretRef,
            String signingSecretRef,
            String tokenRef,
            String workspaceId,
            String channelId,
            String roomId) {

        public Provider {
            clientId = trim(clientId);
            clientSecretRef = trim(clientSecretRef);
            signingSecretRef = trim(signingSecretRef);
            tokenRef = trim(tokenRef);
            workspaceId = trim(workspaceId);
            channelId = trim(channelId);
            roomId = trim(roomId);
        }

        static Provider disabled() {
            return new Provider(false, null, null, null, null, null, null, null);
        }

        private static String trim(String value) {
            return value == null || value.isBlank() ? null : value.trim();
        }
    }
}

package com.massimotter.weave.backend.meetings.config;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class LiveKitSfuProviderPropertiesTest {
    @Test
    void diagnosticsRedactCredentialsAndProviderAddressesWithoutChangingSettings() {
        var properties = new LiveKitSfuProviderProperties(true, " https://private-provider.invalid ",
                " private-api-key ", " private-api-secret ", " https://private-token.invalid ");
        assertThat(properties.apiKey()).isEqualTo("private-api-key");
        assertThat(properties.apiSecret()).isEqualTo("private-api-secret");
        assertThat(properties.directCredentialModeConfigured()).isTrue();
        assertThat(properties.tokenEndpointModeConfigured()).isTrue();
        assertThat(properties.toString()).contains("enabled=true", "urlConfigured=true", "[REDACTED]")
                .doesNotContain("private-api-key", "private-api-secret", "private-provider", "private-token");
    }
}

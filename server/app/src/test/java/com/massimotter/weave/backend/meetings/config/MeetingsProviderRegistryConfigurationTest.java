package com.massimotter.weave.backend.meetings.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.massimotter.weave.backend.config.LiveKitSfuProviderProperties;
import com.massimotter.weave.backend.discovery.provider.ProviderState;
import com.massimotter.weave.backend.discovery.provider.ProviderStatusResponse;
import org.junit.jupiter.api.Test;

class MeetingsProviderRegistryConfigurationTest {
    private final MeetingsProviderRegistryConfiguration configuration = new MeetingsProviderRegistryConfiguration();

    @Test
    void liveKitSfuAdapterReportsDirectCredentialModeSupportSafely() {
        ProviderStatusResponse status = configuration.liveKitSfuProviderRegistrySeam(
                new LiveKitSfuProviderProperties(
                        true,
                        "https://livekit.internal",
                        "secret-api-key",
                        "secret-api-secret",
                        ""))
                .status();

        assertThat(status.module().contractName()).isEqualTo("meetings");
        assertThat(status.providerKey()).isEqualTo("livekit");
        assertThat(status.state()).isEqualTo(ProviderState.CONFIGURED);
        assertThat(status.readiness()).isEqualTo("configured");
        assertThat(status.enabled()).isTrue();
        assertThat(status.configured()).isTrue();
        assertThat(status.failClosed()).isTrue();
        assertThat(status.supportSafe()).isTrue();
        assertThat(status.diagnostics())
                .containsEntry("livekitUrlConfigured", true)
                .containsEntry("apiKeyConfigured", true)
                .containsEntry("apiSecretConfigured", true)
                .containsEntry("directCredentialModeConfigured", true)
                .containsEntry("tokenEndpointModeConfigured", false)
                .containsEntry("secretsReturned", false);
        assertThat(status.toString())
                .doesNotContain("secret-api-key")
                .doesNotContain("secret-api-secret")
                .doesNotContain("https://livekit.internal");
    }

    @Test
    void liveKitSfuAdapterCanUseTokenEndpointModeWithoutReturningEndpointValue() {
        ProviderStatusResponse status = configuration.liveKitSfuProviderRegistrySeam(
                new LiveKitSfuProviderProperties(
                        true,
                        "https://livekit.internal",
                        "",
                        "",
                        "https://token-broker.internal/livekit"))
                .status();

        assertThat(status.state()).isEqualTo(ProviderState.CONFIGURED);
        assertThat(status.configured()).isTrue();
        assertThat(status.diagnostics())
                .containsEntry("tokenEndpointConfigured", true)
                .containsEntry("directCredentialModeConfigured", false)
                .containsEntry("tokenEndpointModeConfigured", true)
                .containsEntry("secretsReturned", false);
        assertThat(status.toString())
                .doesNotContain("https://livekit.internal")
                .doesNotContain("https://token-broker.internal/livekit");
    }
}
